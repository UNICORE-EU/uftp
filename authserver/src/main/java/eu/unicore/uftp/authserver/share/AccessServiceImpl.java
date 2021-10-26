package eu.unicore.uftp.authserver.share;

import java.io.InputStream;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.FilenameUtils;

import eu.unicore.security.Client;
import eu.unicore.uftp.authserver.UFTPDInstance;
import eu.unicore.uftp.datashare.db.ACLStorage;
import eu.unicore.uftp.datashare.db.ShareDAO;

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
		Response r = null;
		ACLStorage shareDB = getShareDB(serverName);
		if(shareDB==null || !haveServer(serverName)){
			return handleError(404, "No server or no data sharing for '"+serverName+"'", null, logger);
		}
		try{
			UFTPDInstance uftp = getServer(serverName);
			ShareDAO share = shareDB.read(uniqueID);
			if(share==null){
				return handleError(404, "No such share '"+uniqueID+"'", null, logger);
			}
			if(equal(share.getTargetID(), getNormalizedCurrentUserName())
				|| equal(share.getTargetID(), Client.ANONYMOUS_CLIENT_DN)){
				ShareServiceProperties spp = kernel.getAttribute(ShareServiceProperties.class);
				String clientIP = spp.getClientIP();
				handlePath(path, share);
				logUsage(false, path, share);
				r = doDownload(share, uftp, clientIP, range);
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
		else{
			if(share.isDirectory()){
				// TODO we could return a listing
				throw new WebApplicationException(404);
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
		Response r = null;
		ACLStorage shareDB = getShareDB(serverName);
		if(shareDB==null || !haveServer(serverName)) {
			return handleError(404, "No server or no data sharing for '"+serverName+"'", null, logger);
		}
		try{
			UFTPDInstance uftp = getServer(serverName);
			
			ShareDAO share = shareDB.read(uniqueID);
			if(share==null){
				throw new WebApplicationException(404);
			}
			if(equal(share.getTargetID(), getNormalizedCurrentUserName())
				|| equal(share.getTargetID(), Client.ANONYMOUS_CLIENT_DN)){
				ShareServiceProperties spp = kernel.getAttribute(ShareServiceProperties.class);
				String clientIP = spp.getClientIP();
				handlePath(path, share);
				logUsage(true, path, share);
				r = doUpload(content, share, uftp, clientIP);
			}else{
				throw new WebApplicationException(Status.UNAUTHORIZED);
			}
		}catch(Exception ex){
			r = handleError(500, "", ex, logger);
		}
		return r;
	}
	
}
