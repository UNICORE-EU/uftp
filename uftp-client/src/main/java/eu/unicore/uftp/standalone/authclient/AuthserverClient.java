package eu.unicore.uftp.standalone.authclient;

import java.io.IOException;
import java.util.Formatter;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import eu.unicore.services.rest.client.IAuthCallback;
import eu.unicore.uftp.authserver.messages.AuthRequest;
import eu.unicore.uftp.authserver.messages.AuthResponse;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.util.Log;

/**
 *
 * @author jj
 */
public class AuthserverClient implements AuthClient {

	private final String uri;

	private final IAuthCallback authData;

	private final Gson gson = new GsonBuilder().create();

	private final ClientFacade client;
	
	private static final Logger LOG = Log.getLogger(Log.CLIENT, AuthserverClient.class);

	public AuthserverClient(String authUrl, IAuthCallback authData, ClientFacade client) {
		this.uri = authUrl;
		this.authData = authData;
		this.client = client;
	}

	@Override
	public AuthResponse connect(String path, boolean send, boolean append) throws Exception {
		return do_connect(path, send, append, false);
	}

	private AuthResponse do_connect(String path, boolean send, boolean append, boolean persistent) throws Exception {
		HttpClient httpClient = HttpClientFactory.getClient(uri);

		HttpPost postRequest = new HttpPost(uri);
		authData.addAuthenticationHeaders(postRequest);
		postRequest.addHeader("Accept", "application/json");
		String base64Key = client.getEncryptionKey()!=null? Utils.encodeBase64(client.getEncryptionKey()) : null;
		AuthRequest request = createRequestObject(path, send, append, 
				client.getStreams(), base64Key, client.isCompress(), client.getGroup(), client.getClientIP(), persistent);
		StringEntity input = new StringEntity(gson.toJson(request),
				ContentType.create("application/json", "UTF-8"));
		postRequest.setEntity(input);
		AuthResponse response = httpClient.execute(postRequest, new AuthResponseResponseHandler());

		if (response != null) {
			LOG.debug("Got AuthResponse: {}", response);
		}
		return response;
	}
	
	@Override
	public AuthResponse createSession(String baseDir, boolean persistent) throws Exception {
		if(baseDir!=null && !baseDir.endsWith("/")) {
			baseDir = baseDir+"/";
		}
		if(baseDir==null)baseDir="";
		LOG.debug("Initalizing session in <{}>", baseDir);
		return do_connect(baseDir, true, true, persistent);
	}

	String infoURL;
	
	public JSONObject getInfo() throws Exception {
		infoURL = makeInfoURL(uri);
		HttpClient client = HttpClientFactory.getClient(infoURL);
		HttpGet getRequest = new HttpGet(infoURL);
		authData.addAuthenticationHeaders(getRequest);
		getRequest.addHeader("Accept", "application/json");
		try(ClassicHttpResponse res = client.executeOpen(null, getRequest, HttpClientContext.create())){
			if(res.getCode()!=200){
				throw new Exception("Error getting info: "+new StatusLine(res));
			}
			else{
				return new JSONObject(EntityUtils.toString(res.getEntity()));
			}
		}
	}
	

	static String crlf = System.getProperty("line.separator");
	
	@Override
	public String parseInfo(JSONObject info) throws JSONException {
		StringBuilder sb = new StringBuilder();
		try(Formatter f = new Formatter(sb, null)){
			f.format("Client identity:    %s%s", getID(info),crlf);
			f.format("Client auth method: %s%s", authData.getType(),crlf);
			f.format("Auth server type:   AuthServer v%s%s", getServerVersion(info), crlf);
			for(String key: info.keySet()) {
				if("client".equals(key) || "server".equals(key))continue;
				JSONObject server = info.getJSONObject(key);
				f.format("Server: %s%s", key, crlf);
				f.format("  URL base:         %s/%s:%s", infoURL, key, crlf);
				f.format("  Description:      %s%s", server.optString("description", "N/A"), crlf);
				f.format("  Remote user info: %s%s", getUserInfo(server), crlf);
				f.format("  Sharing support:  %s%s", getSharingSupport(server), crlf);
				try {
					String serverStatus = getServerStatus(server);
					f.format("  Server status:    %s%s", serverStatus, crlf);
				}catch(JSONException e) {}
			}
		}
		return sb.toString();
	}

	private String getID(JSONObject info) throws JSONException {
		return info.getJSONObject("client").getString("dn");
	}
	
	private String getUserInfo(JSONObject info) throws JSONException {
		StringBuilder sb = new StringBuilder();
		String uid = info.optString("uid", "N/A");
		String gid = info.optString("gid","N/A");
		sb.append("uid=").append(uid);
		sb.append(";gid=").append(gid);
		return sb.toString();
	}
	
	private String getServerStatus(JSONObject info) throws JSONException {
		return info.optString("status", "N/A");
	}

	private String getServerVersion(JSONObject info) throws JSONException {
		return info.getJSONObject("server").optString("version", "???");
	}

	private String getSharingSupport(JSONObject info) throws JSONException {
		boolean enabled = Boolean.parseBoolean(info.getJSONObject("dataSharing").optString("enabled", "N/A"));
		return enabled? "enabled" : "not available";
	}
	
	public static String makeInfoURL(String url) {
		return url.split("/rest/auth")[0]+"/rest/auth";
	}
	
	AuthRequest createRequestObject(String destinationPath,	boolean send, boolean append, 
			int streamCount, String encryptionKey, boolean compress, String group, String clientIP, boolean persistent) {
		AuthRequest ret = new AuthRequest();
		ret.serverPath = destinationPath;
		ret.send = send;
		ret.append = append;
		ret.streamCount = streamCount;
		ret.encryptionKey = encryptionKey;
		ret.compress = compress;
		ret.group = group;
		ret.client = clientIP;
		ret.persistent = persistent;
		return ret;
	}

	public IAuthCallback getAuthData() {
		return authData;
	}

	class AuthResponseResponseHandler implements HttpClientResponseHandler<AuthResponse> {

		@Override
		public AuthResponse handleResponse(ClassicHttpResponse hr) throws HttpException, IOException {
			HttpEntity entity = hr.getEntity();
			if (hr.getCode() == 200) {
				if (entity == null) {
					throw new ClientProtocolException("Invalid server response: Entity empty");
				}
				return gson.fromJson(EntityUtils.toString(entity), AuthResponse.class);
			}
			String msg = "Unable to authenticate (error code: "+ new StatusLine(hr)+")";
			int code = hr.getCode();
			if (code == 401 || code==403) {
				msg += "==> Please check your user name and/or credentials!";
			}
			else if (code == 404) {
				msg += "==> Please check the server URL!";
			}
			else  {
				try {
					JSONObject err = new JSONObject(EntityUtils.toString(entity));
					msg += err.optString("errorMessage", "");
				}catch(Exception ex) {}
			}
			throw new IOException(msg);
		}
	}

}
