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
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import eu.unicore.uftp.authserver.authenticate.AuthData;
import eu.unicore.uftp.authserver.messages.AuthRequest;
import eu.unicore.uftp.authserver.messages.AuthResponse;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.standalone.ClientFacade;

/**
 *
 * @author jj
 */
public class AuthserverClient implements AuthClient {

	private final String uri;

	private final AuthData authData;

	private final Gson gson = new GsonBuilder().create();

	private final ClientFacade client;
	
	private static final Logger LOG = Logger.getLogger(AuthserverClient.class.getName());

	public AuthserverClient(String authUrl, AuthData authData, ClientFacade client) {
		uri = authUrl;
		this.authData = authData;
		this.client = client;
	}

	@Override
	public AuthResponse connect(String path, boolean send, boolean append) throws IOException {
		HttpClient httpClient = HttpClientFactory.getClient(uri);

		HttpPost postRequest = new HttpPost(uri);
		for(Map.Entry<String,String> e: authData.getHttpHeaders().entrySet()){
			postRequest.addHeader(e.getKey(), e.getValue());
		}
		postRequest.addHeader("Accept", "application/json");
		String base64Key = client.getEncryptionKey()!=null? Utils.encodeBase64(client.getEncryptionKey()) : null;
		AuthRequest request = createRequestObject(path, send, append, 
				client.getStreams(), base64Key, client.isCompress(), client.getGroup(), client.getClientIP());
		StringEntity input = new StringEntity(gson.toJson(request),
				ContentType.create("application/json", "UTF-8"));
		postRequest.setEntity(input);
		AuthResponse response = httpClient.execute(postRequest, new AuthResponseResponseHandler());

		if (response != null && LOG.isDebugEnabled()) {
			LOG.debug("Got AuthResponse: " + response.toString());
		}
		return response;
	}

	@Override
	public AuthResponse createSession() throws IOException {
		LOG.debug("Initalizing session");
		return this.connect(sessionModeTag, true, true);
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
		LOG.debug("GET on "+infoURL);
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
			if("client".equals(key))continue;
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
		String uid = info.getString("uid");
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
			int streamCount, String encryptionKey, boolean compress, String group, String clientIP) {
		AuthRequest ret = new AuthRequest();
		ret.serverPath = destinationPath;
		ret.send = send;
		ret.append = append;
		ret.streamCount = streamCount;
		ret.encryptionKey = encryptionKey;
		ret.compress = compress;
		ret.group = group;
		ret.client = clientIP;
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
				try{
					AuthResponse response = gson.fromJson(reply, AuthResponse.class);
					return response;
				}catch(JsonSyntaxException js){
					LOG.error("Invalid reply: "+reply);
					throw js;
				}
			}

			if (statusLine.getStatusCode() == 401) {
				LOG.info("Authorization failed. Check credentials!");
			}

			LOG.error("Invalid server response " + statusLine.getStatusCode());
			throw new IOException("Unable to authorize the transfer request (HTTP " + statusLine.getStatusCode() + ")");
		}
	}

}
