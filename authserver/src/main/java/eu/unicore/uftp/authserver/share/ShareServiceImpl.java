package eu.unicore.uftp.authserver.share;

import java.net.InetAddress;
import java.net.URI;
import java.util.Collection;
import java.util.Iterator;

import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.unicore.services.security.util.AuthZAttributeStore;
import eu.unicore.uftp.authserver.TransferInitializer;
import eu.unicore.uftp.authserver.TransferRequest;
import eu.unicore.uftp.authserver.UFTPBackend;
import eu.unicore.uftp.authserver.UFTPDInstance;
import eu.unicore.uftp.authserver.UserAttributes;
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
import eu.unicore.util.Log;
import eu.unicore.util.Pair;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * @author schuller
 */
@Path("/")
public class ShareServiceImpl extends ShareServiceBase {

	private static final Logger logger = Log.getLogger(Log.SERVICES,ShareServiceImpl.class);

	/**
	 * create or update a share
	 */
	@POST
	@Path("/{serverName}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response share(@PathParam("serverName") String serverName, String jsonString) {
		UFTPBackend server = getLogicalServer(serverName);
		ACLStorage shareDB = getShareDB(serverName);
		if(shareDB==null || server==null){
			throw new WebApplicationException(404);
		}
		try{
			UFTPDInstance uftp = getUFTPD(serverName);
			String clientIP = AuthZAttributeStore.getTokens().getClientIP();
			logger.debug("Incoming request from: {} {}", clientIP, jsonString);

			JSONObject json = new JSONObject(jsonString);
			String requestedGroup = json.optString("group", null);
			UserAttributes ua = assembleAttributes(server, requestedGroup);
			if("nobody".equalsIgnoreCase(ua.uid)){
				return createErrorResponse(401, "Your User ID cannot share, please check your access level");
			}
			AccessType accessType = AccessType.valueOf(json.optString("access", "READ"));
			if(AccessType.WRITE.equals(accessType)&&!getShareServiceProperties().isWriteAllowed()){
				return createErrorResponse(401, "Writable shares not allowed");
			}
			String user = json.getString("user");
			Target target = new SharingUser(normalize(user));
			Owner owner = new Owner(getNormalizedCurrentUserName(), ua.uid, ua.gid);
			if(accessType.equals(AccessType.NONE)) {
				handleDelete(json.getString("path"), owner, shareDB);
				return Response.ok().build();
			}
			else {
				String path = validate(uftp, json.getString("path"), ua, accessType);
				long expires = 0;
				long lifetime = json.optLong("lifetime", 0);
				if(lifetime > 0) {
					expires = lifetime + System.currentTimeMillis()/1000;
				}
				boolean onetime= json.optBoolean("onetime", false);

				String unique = shareDB.grant(accessType, path, target, owner, expires, onetime);
				String location = getBaseURL()+"/"+serverName+"/"+unique;
				return Response.created(new URI(location)).build();
			}
		}catch(JSONException je){
			return handleError(400, "Could not process share", je, logger);
		}
		catch(Exception ex){
			return handleError(500, "Could not process share", ex, logger);
		}
	}

	private void handleDelete(String path, Owner owner,ACLStorage shareDB) throws Exception {
		Collection<ShareDAO> shares = shareDB.readAll(path, false, owner);
		if(shares.size()==0) {
			// try again with '/' appended or removed
			if(path.endsWith("/"))path=path.substring(0, path.length()-1);
			else path = path +"/";
			if(path.length()==0)return;
			shares = shareDB.readAll(path, false, owner);
		}
		for(ShareDAO share: shares) {
			shareDB.delete(share.getID());
			logger.debug("Deleted share: <{}> <{}>", path, owner);
		}
	}

	/**
	 * list all shares and accessible files for the current user
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/{serverName}")
	public Response getShares(@PathParam("serverName") String serverName) {
		UFTPBackend server = getLogicalServer(serverName);
		ACLStorage shareDB = getShareDB(serverName);
		if(shareDB==null || server==null){
			throw new WebApplicationException(404);
		}
		try{
			UserAttributes ua = assembleAttributes(server, null);
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
		UFTPBackend server = getLogicalServer(serverName);
		ACLStorage shareDB = getShareDB(serverName);
		if(shareDB==null || server==null){
			throw new WebApplicationException(404);
		}
		try{
			UserAttributes ua = assembleAttributes(server, null);
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
	 * update a particular share for the current user
	 */
	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/{serverName}/{uniqueID}")
	public Response updateShare(@PathParam("serverName") String serverName, @PathParam("uniqueID") String uniqueID,
			String json) {
		UFTPBackend server = getLogicalServer(serverName);
		ACLStorage shareDB = getShareDB(serverName);
		if(shareDB==null || server==null){
			throw new WebApplicationException(404);
		}
		try{
			UserAttributes ua = assembleAttributes(server, null);
			Owner owner = new Owner(getNormalizedCurrentUserName(), ua.uid, ua.gid);
			ShareDAO share = shareDB.read(uniqueID);
			if(share == null){
				throw new WebApplicationException(404);
			}
			if(!share.getOwnerID().equals(owner.getName())){
				throw new WebApplicationException(401);
			}
			JSONObject updates = new JSONObject(json);
			Iterator<?> i = updates.keys();
			JSONObject reply = new JSONObject();
			share = shareDB.getPersist().getForUpdate(uniqueID);
			try{
				while(i.hasNext()){
					boolean found = true;
					String propertyName = String.valueOf(i.next());
					String val = updates.getString(propertyName);
					if("path".equals(propertyName)) {
						share.setPath(val);
					}
					else if("lifetime".equals(propertyName)) {
						long lifetime = Long.valueOf(val);
						if(lifetime>0) {
							share.setExpires(lifetime + System.currentTimeMillis()/1000);
						}
						else {
							share.setExpires(0);
						}
					}
					else {
						found = false;
					}
					if(!found) {
						reply.put(propertyName, "Property not found!");
					}else {
						reply.put(propertyName, "OK");
					}
				}
			}finally {
				shareDB.getPersist().write(share);
			}
			return Response.ok().entity(reply.toString()).build();
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
			for(UFTPBackend i : getConfig().getServers()){
				String name = i.getServerName();
				ACLStorage shareDB = getShareDB(name);
				if(shareDB==null)continue;
				String url = kernel.getContainerProperties().getContainerURL()+"/rest/share/"+name;
				JSONObject server = new JSONObject();
				server.put("href",url);
				server.put("description",i.getDescription());
				server.put("status",i.getStatusDescription());
				o.put(name, server);
				UserAttributes ua = assembleAttributes(i, null);
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
		Pair<String,Integer> sn = getServerSpec(serverName);
		serverName = sn.getM1();
		UFTPBackend server = getConfig().getServer(serverName);
		ACLStorage shareDB = getShareDB(serverName);
		if(shareDB==null || server==null){
			throw new WebApplicationException(404);
		}
		if(AuthZAttributeStore.getTokens()==null){
			throw new WebApplicationException(Status.UNAUTHORIZED);
		}
		try{
			ShareDAO d = shareDB.read(uniqueID);
			if(d!=null) {
				if(!d.getOwnerID().equals(getNormalizedCurrentUserName())){
					return createErrorResponse(401, "");
				}
			}
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
		ShareServiceProperties spp = getShareServiceProperties();
		String clientIP = spp.getClientIP();
		AuthRequest authRequest = new AuthRequest();
		authRequest.serverPath = "/";
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
