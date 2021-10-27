package eu.unicore.uftp.authserver.share;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.unicore.services.security.util.AuthZAttributeStore;
import eu.unicore.uftp.authserver.LogicalUFTPServer;
import eu.unicore.uftp.authserver.TransferInitializer;
import eu.unicore.uftp.authserver.TransferRequest;
import eu.unicore.uftp.authserver.UFTPDInstance;
import eu.unicore.uftp.authserver.authenticate.UserAttributes;
import eu.unicore.uftp.authserver.messages.AuthRequest;
import eu.unicore.uftp.authserver.messages.AuthResponse;
import eu.unicore.uftp.client.FileInfo;
import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.datashare.AccessType;
import eu.unicore.uftp.datashare.Owner;
import eu.unicore.uftp.datashare.SharingUser;
import eu.unicore.uftp.datashare.Target;
import eu.unicore.uftp.datashare.db.ACLStorage;
import eu.unicore.uftp.datashare.db.ShareDAO;
import eu.unicore.uftp.dpc.Session.Mode;
import eu.unicore.uftp.dpc.UFTPConstants;
import eu.unicore.uftp.server.workers.UFTPWorker;
import eu.unicore.util.Log;

/**
 * @author schuller
 */
@Path("/")
public class ShareServiceImpl extends ShareServiceBase {

	private static final Logger logger = Log.getLogger(Log.SERVICES,ShareServiceImpl.class);

	/**
	 * authenticate a transfer of a shared file using the current client's attributes
	 */
	@POST
	@Path("/{serverName}/auth")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response auth(@PathParam("serverName") String serverName, String json) {
		if(AuthZAttributeStore.getTokens()==null){
			throw new WebApplicationException(Status.UNAUTHORIZED);
		}
		Response r = null;
		ACLStorage shareDB = getShareDB(serverName);
		if(shareDB==null || !haveServer(serverName)){
			return handleError(404, "No sharing for / no server '"+serverName+"', please check your URL", null, logger);
		}

		String clientIP = AuthZAttributeStore.getTokens().getClientIP();
		
		try{
			UFTPDInstance uftp = getServer(serverName);
			
			AuthRequest authRequest = gson.fromJson(json, AuthRequest.class);
			UserAttributes ua = assembleAttributes(serverName, null);
			String targetID = getNormalizedCurrentUserName();
			String path = authRequest.serverPath;
			Target target = new SharingUser(targetID);
			Collection<ShareDAO> shares = shareDB.readAll(path);
			if(shares.size()==0){
				return handleError(404, "No such share '"+path+"', please check your URL", null, logger);
			}
			ShareDAO share = shareDB.filter(shares, target);
			if(share==null){
				return handleError(401, "Not allowed to access '"+path+"'", null, logger);
			}
			else{
				AccessType access = AccessType.valueOf(share.getAccess());
				boolean write = !authRequest.send;
				if(write && access.compareTo(AccessType.WRITE)<0){
					return handleError(401, "Not allowed to write to '"+path+"'", null, logger);
				}
				File requested = new File(path);
				String parent = requested.getParent();
				String file = requested.getName();
				authRequest.serverPath = parent + "/"+UFTPConstants.sessionModeTag;
				ua.uid = share.getUid();
				ua.gid = share.getGid();
				ua.excludes = null;
				ua.includes = file;
				TransferRequest transferRequest = new TransferRequest(authRequest, ua, clientIP);
				// limit UFTP session to read/write the specified path
				transferRequest.setAccessPermissions(write ? Mode.WRITE : Mode.READ);
				
				AuthResponse response = new TransferInitializer().initTransfer(transferRequest,uftp);            
				response.secret = transferRequest.getSecret();
				r = Response.ok().entity(gson.toJson(response)).build();
				logUsage(write, path, share);
			}
		}catch(Exception ex){
			r = handleError(500, "", ex, logger);
		}
		return r;
	}


	/**
	 * create or update a share
	 */
	@POST
	@Path("/{serverName}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response share(@PathParam("serverName") String serverName, String jsonString) {
		if(AuthZAttributeStore.getTokens()==null){
			throw new WebApplicationException(Status.UNAUTHORIZED);
		}
		ACLStorage shareDB = getShareDB(serverName);
		if(shareDB==null || !haveServer(serverName)){
			return handleError(404, "No sharing for '"+serverName+"', please check your URL", null, logger);
		}
		
		try{
			UFTPDInstance uftp = getServer(serverName);
			String clientIP = AuthZAttributeStore.getTokens().getClientIP();
			logger.debug("Incoming request from: {} {}", clientIP, jsonString);

			JSONObject json = new JSONObject(jsonString);
			String requestedGroup = json.optString("group", null);
			UserAttributes ua = assembleAttributes(serverName, requestedGroup);
			if("nobody".equalsIgnoreCase(ua.uid)){
				// cannot share as nobody!
				return handleError(401, "Your User ID cannot share, please check your access level", null, logger);
			}
			AccessType accessType = AccessType.valueOf(json.optString("access", "READ"));
			if(AccessType.WRITE.equals(accessType)&&!getShareServiceProperties().isWriteAllowed()){
				return handleError(401, "Writable shares not allowed", null, logger);
			}
			String path = json.getString("path");
			if(!accessType.equals(AccessType.NONE)){
				path = validate(uftp, path, ua, accessType);
			}
			String user = json.getString("user");
			Target target = new SharingUser(normalize(user));
			Owner owner = new Owner(getNormalizedCurrentUserName(), ua.uid, ua.gid);
			String unique = shareDB.grant(accessType, path, target, owner);
			Response r = null;
			if(accessType.equals(AccessType.NONE)){
				r = Response.ok().build();
			}else{
				String location = getBaseURL()+"/"+serverName+"/"+unique;
				r = Response.created(new URI(location)).build();
			}
			return r;
		}catch(JSONException je){
			return handleError(400, "Could not process share", je, logger);
		}
		catch(Exception ex){
			return handleError(500, "Could not process share", ex, logger);
		}
	}

	@Override
	protected Map<String, Object> getProperties() throws Exception {
		Map<String, Object>res = new HashMap<>();
		return res;
	}


	/**
	 * list all shares and accessible files for the current user
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/{serverName}")
	public Response getShares(@PathParam("serverName") String serverName) {
		ACLStorage shareDB = getShareDB(serverName);
		if(shareDB==null || !haveServer(serverName)){
			throw new WebApplicationException(404);
		}
		try{
			UserAttributes ua = assembleAttributes(serverName, null);
			Owner owner = new Owner(getNormalizedCurrentUserName(), ua.uid, ua.gid);
			JSONObject o = new JSONObject();
			JSONArray jShares = new JSONArray();
			o.put("shares", jShares);
			Collection<ShareDAO> shares = shareDB.readAll(owner);
			for(ShareDAO share: shares){
				try{
					jShares.put(toJSON(share, serverName));
				}catch(JSONException je){}
			}
			o.put("accessible", getAccessible(shareDB, serverName));
			o.put("status", "OK");
			o.put("userInfo", getUserInfo(ua));
			return Response.ok().entity(o.toString()).build();
		}catch(Exception ex){
			return handleError(500, "", ex, logger);
		}
	}
	
	/**
	 * get info about a particular share for the current user
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/{serverName}/{uniqueID}")
	public Response getShareInfo(@PathParam("serverName") String serverName, @PathParam("uniqueID") String uniqueID) {
		ACLStorage shareDB = getShareDB(serverName);
		if(shareDB==null || !haveServer(serverName)){
			throw new WebApplicationException(404);
		}
		try{
			UserAttributes ua = assembleAttributes(serverName, null);
			Owner owner = new Owner(getNormalizedCurrentUserName(), ua.uid, ua.gid);
			ShareDAO share = shareDB.read(uniqueID);
			if(share == null){
				throw new WebApplicationException(404);
			}
			if(!share.getOwnerID().equals(owner.getName())){
				throw new WebApplicationException(401);
			}
			JSONObject o = new JSONObject();
			o.put("share",toJSON(share, serverName));
			o.put("owner",owner.getName());
			o.put("status", "OK");
			o.put("userInfo", getUserInfo(ua));
			return Response.ok().entity(o.toString()).build();
		}catch(Exception ex){
			return handleError(500, "", ex, logger);
		}
	}
	
	
	
	/**
	 * GET info about the configured servers
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/")
	public Response getCatchAll() {
		JSONObject o = new JSONObject();
		try{
			for(LogicalUFTPServer i : getAuthServiceProperties().getServers()){
				String name = i.getServerName();
				ACLStorage shareDB = getShareDB(name);
				if(shareDB==null)continue;
				String url = kernel.getContainerProperties().getContainerURL()+"/rest/share/"+name;
				JSONObject server = new JSONObject();
				server.put("href",url);
				server.put("description",i.getDescription());
				server.put("status",i.getConnectionStatusMessage());
				o.put(name, server);
				UserAttributes ua = assembleAttributes(i.getServerName(), null);
				o.put("userInfo", getUserInfo(ua));
			}
			return Response.ok().entity(o.toString(2)).build();
		}catch(Exception ex){
			return handleError(500, "", ex, logger);
		}
	}
	

	/**
	 * delete a share
	 */
	@DELETE
	@Path("/{serverName}/{uniqueID}")
	public Response delete(@PathParam("serverName") String serverName, @PathParam("uniqueID") String uniqueID) {
		ACLStorage shareDB = getShareDB(serverName);
		if(shareDB==null || !haveServer(serverName)){
			throw new WebApplicationException(404);
		};
		if(AuthZAttributeStore.getTokens()==null){
			throw new WebApplicationException(Status.UNAUTHORIZED);
		}
		try{
			shareDB.delete(uniqueID);
			return Response.ok().build();
		}catch(Exception ex){
			return handleError(500, "", ex, logger);
		}
	}

	/**
	 * validate that the current user can access the path - and thus can share it
	 */
	protected String validate(UFTPDInstance uftp, String path, UserAttributes ua, AccessType access) throws Exception {
		ShareServiceProperties spp = kernel.getAttribute(ShareServiceProperties.class);
		String clientIP = spp.getClientIP();
		AuthRequest authRequest = new AuthRequest();
		authRequest.serverPath = "/"+UFTPWorker.sessionModeTag;
		TransferRequest transferRequest = new TransferRequest(authRequest, ua, clientIP);
		AuthResponse response = new TransferInitializer().initTransfer(transferRequest,uftp);            
		if(!response.success){
			throw new WebApplicationException(401);
		}
		InetAddress[] server = new InetAddress[]{InetAddress.getByName(response.serverHost)};
		try(UFTPSessionClient sc = new UFTPSessionClient(server, response.serverPort)){
			sc.setSecret(transferRequest.getSecret());
			sc.connect();
			FileInfo f = sc.stat(path);
			if(access.compareTo(AccessType.READ)>0){
				// check if the requested write access is possible
				if(!f.isWritable() || (f.isDirectory() && !f.isExecutable())){
					throw new WebApplicationException(401);
				}
			}
			if(f.isDirectory()&&!path.endsWith("/")){
				path += "/";
			}
		}
		return path;
	}

}
