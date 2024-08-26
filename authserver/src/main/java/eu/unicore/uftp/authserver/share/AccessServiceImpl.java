package eu.unicore.uftp.authserver.share;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;

import org.apache.commons.io.FilenameUtils;

import eu.unicore.security.Client;
import eu.unicore.services.security.util.AuthZAttributeStore;
import eu.unicore.uftp.authserver.UFTPBackend;
import eu.unicore.uftp.authserver.TransferInitializer;
import eu.unicore.uftp.authserver.TransferRequest;
import eu.unicore.uftp.authserver.UFTPDInstance;
import eu.unicore.uftp.authserver.UserAttributes;
import eu.unicore.uftp.authserver.messages.AuthRequest;
import eu.unicore.uftp.authserver.messages.AuthResponse;
import eu.unicore.uftp.datashare.AccessType;
import eu.unicore.uftp.datashare.SharingUser;
import eu.unicore.uftp.datashare.Target;
import eu.unicore.uftp.datashare.db.ACLStorage;
import eu.unicore.uftp.datashare.db.ShareDAO;
import eu.unicore.uftp.dpc.Session.Mode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
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
 * Provides HTTP access to shared files, including anonymous access
 * 
 * @author schuller
 */
@Path("/")
public class AccessServiceImpl extends ShareServiceBase {

	/**
	 * download a file, which has to be either shared anonymously or with the current user
	 * 
	 * @param serverName
	 * @param uniqueID - unique ID of the share
	 */
	@GET
	@Path("/{serverName}/{uniqueID}")
	public Response download(@PathParam("serverName") String serverName, @PathParam("uniqueID") String uniqueID,
			@HeaderParam("range")String range) {
		return downloadPath(serverName, uniqueID, null, range); 
	}

	/**
	 * download a file, which has to be either shared anonymously 
	 * or with the current user
	 * 
	 * @param serverName
	 * @param uniqueID - unique ID of the share
	 * @param path - if the share is a directory, this is used to denote the file path
	 */
	@GET
	@Path("/{serverName}/{uniqueID}/{path:.*}")
	public Response downloadPath(@PathParam("serverName") String serverName, @PathParam("uniqueID") String uniqueID, 
			@PathParam("path") String path, @HeaderParam("range")String range) {
		UFTPBackend server = getLogicalServer(serverName);
		ACLStorage shareDB = getShareDB(serverName);
		if(shareDB==null || server==null){
			return handleError(404, "No server or no data sharing for '"+serverName+"'", null, logger);
		}
		Response r = null;
		try{
			UFTPDInstance uftp = getUFTPD(serverName);
			ShareDAO share = shareDB.read(uniqueID);
			if(share==null){
				return handleError(404, "No such share '"+uniqueID+"'", null, logger);
			}
			if(equal(share.getTargetID(), getNormalizedCurrentUserName())
				|| equal(share.getTargetID(), Client.ANONYMOUS_CLIENT_DN)){
				ShareServiceProperties spp = kernel.getAttribute(ShareServiceProperties.class);
				String clientIP = spp.getClientIP();
				handlePath(path, share);
				logUsage("HTTP-download", share.getPath(), share);
				r = doDownload(share, uftp, clientIP, range);
				if(share.isOneTime()) {
					shareDB.delete(uniqueID);
				}
				else{
					shareDB.incrementAccessCount(uniqueID);
				}
			}else{
				throw new WebApplicationException(Status.UNAUTHORIZED);
			}
		}catch(Exception ex){
			r = handleError(500, "", ex, logger);
		}
		return r;
	}

	private void handlePath(String path, ShareDAO share) throws WebApplicationException{
		if(path!=null){
			if(share.isDirectory()){
				String fullPath = FilenameUtils.normalize(share.getPath()+"/"+path, true);
				share.setPath(fullPath);
			}
			else{
				throw new WebApplicationException("Not a directory", 404);
			}
		}
	}

	/**
	 * upload a file, which has to be either shared anonymously 
	 * or with the current user
	 * 
	 * @param content - the data to store
	 * @param serverName
	 * @param uniqueID - unique ID of the share
	 */
	@PUT
	@Path("/{serverName}/{uniqueID}")
	public Response upload(InputStream content, @PathParam("serverName") String serverName, 
			@PathParam("uniqueID") String uniqueID) {
		return uploadPath(content, serverName, uniqueID, null);
	}
	
	/**
	 * upload a file, which has to be either shared anonymously 
	 * or with the current user
	 * 
	 * @param content - the data to store
	 * @param serverName
	 * @param uniqueID - unique ID of the share
	 * @param path - if the share is a directory, this is used to denote the file path
	 */
	@PUT
	@Path("/{serverName}/{uniqueID}/{path:.*}")
	public Response uploadPath(InputStream content, @PathParam("serverName") String serverName, 
			@PathParam("uniqueID") String uniqueID, @PathParam("path") String path) {
		UFTPBackend server = getLogicalServer(serverName);
		ACLStorage shareDB = getShareDB(serverName);
		if(shareDB==null || server==null) {
			return handleError(404, "No server or no data sharing for '"+serverName+"'", null, logger);
		}
		Response r = null;
		try{
			UFTPDInstance uftp = getUFTPD(serverName);
			ShareDAO share = shareDB.read(uniqueID);
			if(share==null){
				throw new WebApplicationException(404);
			}
			if( (checkAccess(AccessType.WRITE, share.getAccess()) &&
				( equal(share.getTargetID(), getNormalizedCurrentUserName())
				|| equal(share.getTargetID(), Client.ANONYMOUS_CLIENT_DN))))
			{
				ShareServiceProperties spp = kernel.getAttribute(ShareServiceProperties.class);
				String clientIP = spp.getClientIP();
				handlePath(path, share);
				logUsage("HTTP-upload", share.getPath(), share);
				r = doUpload(content, share, uftp, clientIP);
				if(share.isOneTime()) {
					shareDB.delete(uniqueID);
				}else {
					shareDB.incrementAccessCount(uniqueID);
				}
			}else{
				throw new WebApplicationException(Status.UNAUTHORIZED);
			}
		}catch(Exception ex){
			r = handleError(500, "", ex, logger);
		}
		return r;
	}
	
	/**
	 * authenticate a transfer of a shared file using the current client's attributes
	 */
	@POST
	@Path("/{serverName}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response auth(@PathParam("serverName") String serverName, String json) {
		UFTPBackend server = getLogicalServer(serverName);
		ACLStorage shareDB = getShareDB(serverName);
		if(shareDB==null || server==null){
			return handleError(404, "No sharing for / no server '"+serverName+"', please check your URL", null, logger);
		}
		String clientIP = AuthZAttributeStore.getTokens().getClientIP();
		Response r = null;
		try{
			UFTPDInstance uftp = getUFTPD(serverName);
			AuthRequest authRequest = gson.fromJson(json, AuthRequest.class);
			UserAttributes ua = assembleAttributes(server, null);
			String targetID = getNormalizedCurrentUserName();
			String path = FilenameUtils.normalize(authRequest.serverPath, true);
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
				if(!authRequest.send && access.compareTo(AccessType.WRITE)<0){
					return handleError(401, "Not allowed to write to '"+path+"'", null, logger);
				}
				File requested = new File(path);
				String parent = requested.getParent();
				String file = requested.getName();
				authRequest.serverPath = parent;
				ua.uid = share.getUid();
				ua.gid = share.getGid();
				ua.excludes = null;
				if(!_tag.equals(file)) {
					ua.includes = file;
				}
				TransferRequest transferRequest = new TransferRequest(authRequest, ua, clientIP);
				// limit UFTP session to read/write the specified path
				boolean canWrite = checkAccess(AccessType.WRITE, access);
				transferRequest.setAccessPermissions(canWrite ? Mode.WRITE : Mode.READ);

				AuthResponse response = new TransferInitializer().initTransfer(transferRequest,uftp);
				response.secret = transferRequest.getSecret();
				r = Response.ok().entity(gson.toJson(response)).build();
				if(share.isOneTime()) {
					shareDB.delete(share.getID());
				}
				else {
					shareDB.incrementAccessCount(share.getID());
				}
				logUsage("UFTP", path, share);
			}
		}catch(Exception ex){
			r = handleError(500, "", ex, logger);
		}
		return r;
	}

}
