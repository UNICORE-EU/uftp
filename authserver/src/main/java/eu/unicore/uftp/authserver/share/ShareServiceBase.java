package eu.unicore.uftp.authserver.share;

import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Collection;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.emi.security.authn.x509.impl.X500NameUtils;
import eu.unicore.services.security.util.AuthZAttributeStore;
import eu.unicore.uftp.authserver.ServiceBase;
import eu.unicore.uftp.authserver.TransferInitializer;
import eu.unicore.uftp.authserver.TransferRequest;
import eu.unicore.uftp.authserver.UFTPDInstance;
import eu.unicore.uftp.authserver.UserAttributes;
import eu.unicore.uftp.authserver.messages.AuthRequest;
import eu.unicore.uftp.authserver.messages.AuthResponse;
import eu.unicore.uftp.client.BackedInputStream;
import eu.unicore.uftp.client.FileInfo;
import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.datashare.AccessType;
import eu.unicore.uftp.datashare.SharingUser;
import eu.unicore.uftp.datashare.Target;
import eu.unicore.uftp.datashare.db.ACLStorage;
import eu.unicore.uftp.datashare.db.ShareDAO;
import eu.unicore.uftp.dpc.Session.Mode;
import eu.unicore.util.Log;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.Response.Status;

/**
 * common stuff for the share and access services
 * @author schuller
 */
public abstract class ShareServiceBase extends ServiceBase {

	protected static final Logger logger = Log.getLogger(Log.SERVICES, ShareService.class);

	protected String getNormalizedCurrentUserName() throws Exception {
		return normalize(AuthZAttributeStore.getClient().getDistinguishedName());
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
	protected Response doDownload(ShareDAO share, UFTPDInstance uftp, String clientIP, String rangeHeader) throws Exception {
		boolean wantRange = rangeHeader!=null;
		AuthRequest authRequest = new AuthRequest();
		authRequest.send = true;
		File targetFile = new File(share.getPath());
		authRequest.serverPath = targetFile.getParentFile().getPath();
		UserAttributes ua = new UserAttributes(share.getUid(), share.getGid(), null);
		TransferRequest transferRequest = new TransferRequest(authRequest, ua, clientIP);
		transferRequest.setAccessPermissions(Mode.WRITE);
		transferRequest.setIncludes(targetFile.getName());
		AuthResponse response = new TransferInitializer().initTransfer(transferRequest,uftp);
		response.assertSuccess();
		InetAddress[] server = new InetAddress[]{InetAddress.getByName(response.serverHost)};
		final Range range = new Range(rangeHeader);
		final String remoteFile = new File(share.getPath()).getName();
		final UFTPSessionClient uc = new UFTPSessionClient(server, response.serverPort);
		uc.setSecret(transferRequest.getSecret());
		uc.connect();
		FileInfo fi = uc.stat(remoteFile);
		if(fi.isDirectory()) {
			return getListing(share, uftp, clientIP);
		}
		long offset = 0;
		long size = fi.getSize();
		if(range.haveRange) {
			offset = range.offset;
			size = range.length;
		}
		BackedInputStream in = uc.getInputStream(remoteFile, offset, size, -1);
		in.addCleanupHandler(()->{
			uc.close();
		});
		String name = null;
		try {
			name = new File(share.getPath()).getName();
		}catch(Exception ex) {}
		ResponseBuilder rb = wantRange? Response.status(Status.PARTIAL_CONTENT) :Response.ok();
		rb.entity(in);
		if(name!=null)rb.header("Content-Disposition", "attachment; filename=\""+name+"\"");
		// TODO need Content-Range header!? test with wget
		return rb.build();
	}

	/**
	 * upload data to the shared file using the Unix user id and group from the share
	 */
	protected Response doUpload(InputStream content, ShareDAO share, UFTPDInstance uftp, String clientIP) throws Exception {
		AuthRequest authRequest = new AuthRequest();
		authRequest.send = false;
		authRequest.serverPath = new File(share.getPath()).getParentFile().getPath();
		String fileName = new File(share.getPath()).getName();
		UserAttributes ua = new UserAttributes(share.getUid(), share.getGid(), null);
		TransferRequest transferRequest = new TransferRequest(authRequest, ua, clientIP);
		AuthResponse response = new TransferInitializer().initTransfer(transferRequest,uftp);
		response.assertSuccess();
		InetAddress[] server = new InetAddress[]{InetAddress.getByName(response.serverHost)};
		try (UFTPSessionClient uc = new UFTPSessionClient(server, response.serverPort)){
			uc.setSecret(transferRequest.getSecret());
			uc.connect();
			uc.writeAll(fileName, content, true);
			return Response.ok().build();
		}
	}

	protected Response getListing(ShareDAO share, UFTPDInstance uftp, String clientIP) throws Exception {
		AuthRequest authRequest = new AuthRequest();
		authRequest.send = true;
		authRequest.serverPath = new File(share.getPath()).getParentFile().getPath();
		UserAttributes ua = new UserAttributes(share.getUid(), share.getGid(), null);
		TransferRequest transferRequest = new TransferRequest(authRequest, ua, clientIP);
		AuthResponse response = new TransferInitializer().initTransfer(transferRequest,uftp);
		response.assertSuccess();
		InetAddress[] server = new InetAddress[]{InetAddress.getByName(response.serverHost)};
		final String remoteFile = new File(share.getPath()).getName();
		try(UFTPSessionClient uc = new UFTPSessionClient(server, response.serverPort)){
			uc.setSecret(transferRequest.getSecret());
			uc.connect();
			FileInfo fi = uc.stat(remoteFile);
			if(!fi.isDirectory())throw new Exception("Not a directory");
			List<FileInfo> fileList = uc.getFileInfoList(remoteFile);
			StringBuilder sb = new StringBuilder();
			for(FileInfo f: fileList) {
				sb.append(f.getLISTFormat());
			}
			return Response.ok(sb.toString()).build();
		}
	}

	protected ACLStorage getShareDB(String serverName){
		return getShareServiceProperties().getDB(serverName);
	}

	protected String normalize(String targetUser){
		return X500NameUtils.getComparableForm(targetUser);
	}

	protected boolean equal(String targetUser, String currentUser){
		return normalize(targetUser).equals(normalize(currentUser));
	}

	protected JSONObject toJSON(ShareDAO share, String serverName) throws JSONException {
		JSONObject o = new JSONObject();
		o.put("user", share.getTargetID());
		o.put("id", share.getID());
		o.put("directory", share.isDirectory());
		addCommon(o, share, serverName);
		return o;
	}

	protected JSONObject toJSONAccessible(ShareDAO share, String serverName) throws JSONException {
		JSONObject o = new JSONObject();
		o.put("owner", share.getOwnerID());
		addCommon(o, share, serverName);
		return o;
	}

	protected void addCommon(JSONObject o, ShareDAO share, String serverName) throws JSONException{
		o.put("path", share.getPath());
		o.put("access", share.getAccess());
		if(share.getExpires()>0) {
			o.put("lifetime", share.getExpires()-System.currentTimeMillis()/1000);
		}
		o.put("onetime", share.isOneTime());
		o.put("accessCount", share.getAccessCount());
		String url = (baseURL+"/"+serverName+"/"+share.getID()).replaceFirst("/rest/share/", "/rest/access/");
		o.put("http", url);
		String auth = (baseURL+"/"+serverName+":"+share.getPath()).replaceFirst("/rest/share/", "/rest/access/");
		o.put("uftp", auth);
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

	protected void logUsage(String type, String path, ShareDAO share){
		if(!logger.isInfoEnabled())return;
		if(path==null)path="n/a";
		String uid = share.getUid();
		String gid = share.getGid()!=null?share.getGid():"NONE";
		String clientDN = AuthZAttributeStore.getClient().getDistinguishedName();
		String clientIP = AuthZAttributeStore.getTokens().getClientIP();
		String msg = String.format("USAGE [SHARE via %s] [%s][%s][%s:%s] %s",
				type, clientDN, clientIP, uid, gid, path.replace(_tag, ""));
		logger.info(msg);
	}

	/**
	 * check access
	 * @param want - desired access
	 * @param allowed - allowed access
	 * @return true of wanted access is allowed
	 */
	public static boolean checkAccess(AccessType want, AccessType allowed) {
		return want.compareTo(allowed)<=0;
	}

	public static boolean checkAccess(AccessType want, String allowed) {
		return checkAccess(want, AccessType.valueOf(allowed));
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
