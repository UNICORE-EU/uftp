package eu.unicore.uftp.standalone.authclient;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import eu.unicore.uftp.authserver.authenticate.AuthData;
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

	private final AuthData authData;

	private final Gson gson = new GsonBuilder().create();

	private final ClientFacade client;
	
	private static final Logger LOG = Log.getLogger(Log.CLIENT, AuthserverClient.class);

	public AuthserverClient(String authUrl, AuthData authData, ClientFacade client) {
		uri = authUrl;
		this.authData = authData;
		this.client = client;
	}

	@Override
	public AuthResponse connect(String path, boolean send, boolean append) throws IOException {
		return do_connect(path, send, append, false);
	}

	private AuthResponse do_connect(String path, boolean send, boolean append, boolean persistent) throws IOException {
		HttpClient httpClient = HttpClientFactory.getClient(uri);

		HttpPost postRequest = new HttpPost(uri);
		for(Map.Entry<String,String> e: authData.getHttpHeaders().entrySet()){
			postRequest.addHeader(e.getKey(), e.getValue());
		}
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
	public AuthResponse createSession(String baseDir, boolean persistent) throws IOException {
		if(baseDir!=null && !baseDir.endsWith("/")) {
			baseDir = baseDir+"/";
		}
		if(baseDir==null)baseDir="";
		LOG.debug("Initalizing session in <{}>", baseDir);
		return do_connect(baseDir+sessionModeTag, true, true, persistent);
	}

	String infoURL;
	
	public HttpResponse getInfo() throws IOException {
		infoURL = makeInfoURL(uri);
		HttpClient client = HttpClientFactory.getClient(infoURL);
		HttpGet getRequest = new HttpGet(infoURL);
		for(Map.Entry<String,String> e: authData.getHttpHeaders().entrySet()){
			getRequest.addHeader(e.getKey(), e.getValue());
		}
		getRequest.addHeader("Accept", "application/json");
		return client.execute(getRequest);
	}
	

	static String crlf = System.getProperty("line.separator");
	
	@Override
	@SuppressWarnings("unchecked")
	public String parseInfo(JSONObject info) throws JSONException {
		StringBuilder sb = new StringBuilder();
		sb.append("Client identity:    ").append(getID(info)).append(crlf);
		sb.append("Client auth method: ").append(authData.getType()).append(crlf);
		sb.append("Auth server type:   AuthServer").append(crlf);
		Iterator<String>keys = info.sortedKeys();
		while(keys.hasNext()) {
			String key = keys.next();
			if("client".equals(key) || "server".equals(key))continue;
			JSONObject server = info.getJSONObject(key);
			sb.append("Server: ").append(key).append(crlf);
			sb.append("  URL base:         ").append(infoURL).append("/").append(key).append(":").append(crlf);
			sb.append("  Description:      ").append(server.optString("description", "N/A")).append(crlf);
			sb.append("  Remote user info: ").append(getUserInfo(server)).append(crlf);
			sb.append("  Sharing support:  ").append(getSharingSupport(server)).append(crlf);
			try {
				String serverStatus = getServerStatus(server);
				sb.append("  Server status:    ").append(serverStatus).append(crlf);
			}catch(JSONException e) {}
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

	public AuthData getAuthData() {
		return authData;
	}

	class AuthResponseResponseHandler implements ResponseHandler<AuthResponse> {

		@Override
		public AuthResponse handleResponse(HttpResponse hr) throws ClientProtocolException, IOException {
			StatusLine statusLine = hr.getStatusLine();
			HttpEntity entity = hr.getEntity();
			String reply = null;
			if (statusLine.getStatusCode() == 200) {
				if (entity == null) {
					throw new ClientProtocolException("Invalid server response: Entity empty");
				}
				ContentType contentType = ContentType.getOrDefault(entity);
				Charset charset = contentType.getCharset();
				reply = IOUtils.toString(entity.getContent(), charset);
				return gson.fromJson(reply, AuthResponse.class);
			}
			String msg = "Unable to authenticate (error code: "
						+ statusLine.getStatusCode()+" "+statusLine.getReasonPhrase()+") ";
			int code = statusLine.getStatusCode();
			if (code == 401 || code==403) {
				msg += "==> Please check your user name and/or credentials!";
			}
			else if (code == 404) {
				msg += "==> Please check the server URL!";
			}
			else  {
				try {
					ContentType contentType = ContentType.getOrDefault(entity);
					Charset charset = contentType.getCharset();
					reply = IOUtils.toString(entity.getContent(), charset);
					JSONObject err = new JSONObject(reply);
					msg += err.optString("errorMessage", "");
				}catch(Exception ex) {}
			}
			throw new IOException(msg);
		}
	}

}
