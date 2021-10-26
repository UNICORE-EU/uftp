package eu.unicore.uftp.authserver.share;

import java.io.File;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetAddress;
import java.util.Collection;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.emi.security.authn.x509.impl.X500NameUtils;
import eu.unicore.security.Client;
import eu.unicore.services.security.util.AuthZAttributeStore;
import eu.unicore.uftp.authserver.ServiceBase;
import eu.unicore.uftp.authserver.TransferInitializer;
import eu.unicore.uftp.authserver.TransferRequest;
import eu.unicore.uftp.authserver.UFTPDInstance;
import eu.unicore.uftp.authserver.authenticate.UserAttributes;
import eu.unicore.uftp.authserver.messages.AuthRequest;
import eu.unicore.uftp.authserver.messages.AuthResponse;
import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.datashare.AccessType;
import eu.unicore.uftp.datashare.SharingUser;
import eu.unicore.uftp.datashare.Target;
import eu.unicore.uftp.datashare.db.ACLStorage;
import eu.unicore.uftp.datashare.db.ShareDAO;
import eu.unicore.uftp.dpc.UFTPConstants;
import eu.unicore.util.Log;

/**
 * common stuff for the share and access services
 * @author schuller
 */
public abstract class ShareServiceBase extends ServiceBase {

	protected static final Logger logger = Log.getLogger(Log.SERVICES, ShareService.class);

	protected String getNormalizedCurrentUserName() throws Exception {
		ShareServiceProperties ssp = kernel.getAttribute(ShareServiceProperties.class);
		Client c = AuthZAttributeStore.getClient();
		String identity = ssp.getIdentityExtractor().extractIdentity(c);
		return normalize(identity);
	}

	/**
	 * get stuff shared with the current user
	 */
	protected JSONArray getAccessible(ACLStorage shareDB, String serverName) throws Exception {
		JSONArray jShares = new JSONArray();
		Target target = new SharingUser(getNormalizedCurrentUserName());
		Collection<ShareDAO> shares = shareDB.readAll(target);
		for(ShareDAO share: shares){
			try{
				jShares.put(toJSONAccessible(share, serverName));
			}catch(JSONException je){}
		}
		return jShares;
	}

	/**
	 * download the file using the Unix user id and group from the share
	 *
	 * @param share
	 * @param uftp
	 * @param clientIP
	 */
	protected Response doDownload(ShareDAO share, UFTPDInstance uftp, String clientIP, String rangeHeader){
		Response r = null;
		try{
			boolean wantRange = rangeHeader!=null;

			AuthRequest authRequest = new AuthRequest();
			authRequest.send = true;
			authRequest.serverPath = new File(new File(share.getPath()).getParentFile(), 
					"/"+UFTPConstants.sessionModeTag).getPath();
			UserAttributes ua = new UserAttributes(share.getUid(), share.getGid(), null);
			TransferRequest transferRequest = new TransferRequest(authRequest, ua, clientIP);
			AuthResponse response = new TransferInitializer().initTransfer(transferRequest,uftp);            
			InetAddress[] server = new InetAddress[]{InetAddress.getByName(response.serverHost)};
			PipedOutputStream src = new PipedOutputStream();
			PipedInputStream pin = new PipedInputStream(src);

			final Range range = new Range(rangeHeader);
			final String remoteFile = new File(share.getPath()).getName();
			final Runnable task = new Runnable() {
				public void run() {
					try(final UFTPSessionClient uc = new UFTPSessionClient(server, response.serverPort)){
						uc.setSecret(transferRequest.getSecret());
						uc.connect();
						if(range.haveRange) {
							uc.get(remoteFile, range.offset, range.length, src);
						} else {
							uc.get(remoteFile, src);
						}
					}catch(Exception ex) {
						Log.logException("Error downloading", ex, logger);
					}
				}
			};
			kernel.getContainerProperties().getThreadingServices().getExecutorService().submit(task);

			String name = null;
			try {
				name = new File(share.getPath()).getName();
			}catch(Exception ex) {}

			ResponseBuilder rb = wantRange? Response.status(Status.PARTIAL_CONTENT) :Response.ok();
			rb.entity(pin);
			if(name!=null)rb.header("Content-Disposition", "attachment; filename=\""+name+"\"");
			r = rb.build();
		}catch(Exception ex){
			r = handleError(500, "", ex, logger);
		}
		return r;
	}
	
	/**
	 * upload data to the shared file using the Unix user id and group from the share
	 */
	protected Response doUpload(InputStream content, ShareDAO share, UFTPDInstance uftp, String clientIP){
		Response r = null;
		try{
			AuthRequest authRequest = new AuthRequest();
			authRequest.send = false;
			authRequest.serverPath = new File(new File(share.getPath()).getParentFile(), 
					"/"+UFTPConstants.sessionModeTag).getPath();
			String fileName = new File(share.getPath()).getName();
			UserAttributes ua = new UserAttributes(share.getUid(), share.getGid(), null);
			TransferRequest transferRequest = new TransferRequest(authRequest, ua, clientIP);
			AuthResponse response = new TransferInitializer().initTransfer(transferRequest,uftp);            
			InetAddress[] server = new InetAddress[]{InetAddress.getByName(response.serverHost)};
			try (UFTPSessionClient uc = new UFTPSessionClient(server, response.serverPort)){
				uc.setSecret(transferRequest.getSecret());
				uc.connect();
				uc.writeAll(fileName, content, true);
				r = Response.ok().build();
			}
		}catch(Exception ex){
			r = handleError(500, "", ex, logger);
		}
		return r;
	}

	protected ACLStorage getShareDB(String serverName){
		return kernel.getAttribute(ShareServiceProperties.class).getDB(serverName);
	}

	protected String normalize(String targetUser){
		return X500NameUtils.getComparableForm(targetUser);
	}

	protected boolean equal(String targetUser, String currentUser){
		return normalize(targetUser).equals(normalize(currentUser));
	}

	protected ShareDAO createShare(JSONObject o) throws JSONException {
		ShareDAO share = new ShareDAO();
		share.setPath(o.getString("path"));
		share.setAccess(AccessType.valueOf(o.optString("access", "READ")));
		share.setTargetID(o.getString("user"));
		return share;
	}
	
	protected JSONObject toJSON(ShareDAO share, String serverName) throws JSONException {
		JSONObject o = new JSONObject();
		o.put("path", share.getPath());
		o.put("directory", share.isDirectory());
		o.put("user", share.getTargetID());
		o.put("access", share.getAccess());
		o.put("id", share.getID());
		String url = (baseURL+"/"+serverName+"/"+share.getID()).replaceFirst("/rest/share/", "/rest/access/");
		o.put("http", url);
		String auth = baseURL+"/"+serverName+"/auth:"+share.getPath();
		o.put("uftp", auth);
		return o;
	}

	protected JSONObject toJSONAccessible(ShareDAO share, String serverName) throws JSONException {
		JSONObject o = new JSONObject();
		o.put("path", share.getPath());
		o.put("owner", share.getOwnerID());
		o.put("access", share.getAccess());
		o.put("id", share.getID());
		String url = (baseURL+"/"+serverName+"/"+share.getID()).replaceFirst("/rest/share/", "/rest/access/");
		o.put("http", url);
		String auth = baseURL+"/"+serverName+"/auth:"+share.getPath();
		o.put("uftp", auth);
		return o;
	}
	
	protected JSONObject getUserInfo(UserAttributes ua) throws Exception {
		JSONObject o = new JSONObject();
		o.put("uid", ua.uid);
		o.put("gid", ua.gid);
		o.put("groups", ua.groups);
		o.put("dn", getNormalizedCurrentUserName());
		return o;
	}
	
	protected ShareServiceProperties getShareServiceProperties(){
		return kernel.getAttribute(ShareServiceProperties.class);
	}
	
	
	protected void logUsage(boolean write, String path, ShareDAO share){
		if(!logger.isInfoEnabled())return;
		String uid = share.getUid();
		String gid = share.getGid()!=null?share.getGid():"NONE";
		String clientDN = AuthZAttributeStore.getClient().getDistinguishedName();
		String clientIP = AuthZAttributeStore.getTokens().getClientIP();
		String access = write? "WRITE" : "READ";
		String msg = String.format("USAGE [%s][%s][%s:%s][%s] %s", 
				clientDN, clientIP, uid, gid, access, path);
		logger.info(msg);
	}

	public static class Range {

		public long offset;

		public long length;

		public boolean haveRange = false;

		public Range(String rangeHeader) {
			offset = 0;
			length = -1;
			if(rangeHeader!=null) {
				haveRange = true;
				String[] rangeSpec = rangeHeader.split("=");
				if(!"bytes".equals(rangeSpec[0]))throw new IllegalArgumentException("Range header "+rangeHeader+ " cannot be parsed");
				String range = rangeSpec[1];

				String[] tok = range.split("-");
				offset = Long.parseLong(tok[0]);
				if(offset<0)throw new IllegalArgumentException("Range header "+rangeHeader+ " cannot be parsed");
				if(tok.length>1){
					long last = Long.parseLong(tok[1]);
					if(last<offset)throw new IllegalArgumentException("Range header "+rangeHeader+ " cannot be parsed");
					length = last+1-offset;
				}
			}
		}
	}

}
