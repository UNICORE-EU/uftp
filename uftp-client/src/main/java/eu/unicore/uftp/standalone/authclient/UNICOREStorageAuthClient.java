package eu.unicore.uftp.standalone.authclient;

import java.io.IOException;
import java.util.Formatter;
import java.util.Random;

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
import org.json.JSONException;
import org.json.JSONObject;

import eu.unicore.services.rest.client.IAuthCallback;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.standalone.ClientFacade;

/**
 * create a session using the UNICORE Storage API
 * 
 * @author schuller
 */
public class UNICOREStorageAuthClient implements AuthClient {

	private final String uri;

	private final IAuthCallback authData;

	private final ClientFacade client;

	public UNICOREStorageAuthClient(String authUrl, IAuthCallback authData, ClientFacade client) {
		this.uri = authUrl;
		this.authData = authData;
		this.client = client;
	}

	@Override
	public AuthResponse connect(String path) throws Exception {
		return do_connect(path, false);
	}

	private AuthResponse do_connect(String path, boolean persistent) throws Exception {
		HttpClient httpClient = HttpClientFactory.getClient(uri);
		HttpPost postRequest = new HttpPost(uri);
		authData.addAuthenticationHeaders(postRequest);
		postRequest.addHeader("Accept", "application/json");
		String base64Key = client.getEncryptionKey()!=null? Utils.encodeBase64(client.getEncryptionKey()) : null;
		JSONObject request = createRequestObject(path, base64Key, client.isCompress(), client.getClientIP(), persistent);
		StringEntity input = new StringEntity(request.toString(),
				ContentType.create("application/json", "UTF-8"));
		postRequest.setEntity(input);
		AuthResponse response = httpClient.execute(postRequest, new UNICOREResponseHandler());
		try{
			response.secret = request.getJSONObject("extraParameters").getString("uftp.secret");
		}catch(JSONException e) {
			throw new IOException(e);
		}
		return response;
	}
	
	@Override
	public AuthResponse createSession(String baseDir, boolean persistent) throws Exception {
		if(baseDir!=null && !baseDir.endsWith("/")) {
			baseDir = baseDir+"/";
		}
		if(baseDir==null)baseDir="";
		return do_connect(baseDir, persistent);
	}

	@Override
	public JSONObject getInfo() throws Exception {
		String infoURL = makeInfoURL(uri);
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
			f.format("Auth server type:   UNICORE/X v%s%s", getServerVersion(info), crlf);
			f.format("Remote user info:   %s%s", getUserInfo(info), crlf);
			try {
				String serverStatus = getServerStatus(info);
				f.format("UFTP Server status: %s%s", serverStatus, crlf);
			}catch(JSONException e) {}
		}
		return sb.toString();
	}

	private String getID(JSONObject info) throws JSONException {
		return info.getJSONObject("client").getString("dn");
	}
	
	private String getUserInfo(JSONObject info) throws JSONException {
		JSONObject client = info.getJSONObject("client");
		StringBuilder sb = new StringBuilder();
		String role = client.getJSONObject("role").getString("selected");
		String uid = client.getJSONObject("xlogin").optString("UID", "N/A");
		String gid = client.getJSONObject("xlogin").optString("group","N/A");
		sb.append("uid=").append(uid);
		sb.append(";gid=").append(gid);
		sb.append(";role=").append(role);
		return sb.toString();
	}


	private String getServerStatus(JSONObject info) throws JSONException {
		JSONObject status = info.getJSONObject("server").getJSONObject("externalConnections");
		for(String key: status.keySet()) {
			if(!key.startsWith("UFTPD"))continue;
			return status.getString(key);
		}
		return "N/A";
	}

	private String getServerVersion(JSONObject info) throws JSONException {
		return info.getJSONObject("server").optString("version", "???");
	}
	
	public static String makeInfoURL(String url) {
		return url.split("/rest/core")[0]+"/rest/core";
	}
	
	private JSONObject createRequestObject(String path, String encryptionKey, boolean compress, String clientIP, boolean persistent) throws IOException {
		try {
			JSONObject ret = new JSONObject();
			ret.put("file", path);
			ret.put("protocol", "UFTP");
			JSONObject params = new JSONObject();
			ret.put("extraParameters", params);

			params.put("uftp.secret", generateSecret());
			if(clientIP!=null) {
				params.put("uftp.client.host", clientIP);
			}
			if(encryptionKey!=null) {
				params.put("uftp.encryption", "true");
			}
			params.put("uftp.compression", compress);
			params.put("uftp.persistent", persistent);
			
			return ret;
		} catch(JSONException e) {
			throw new IOException(e);
		}
	}

	public IAuthCallback getAuthData() {
		return authData;
	}


	class UNICOREResponseHandler implements HttpClientResponseHandler<AuthResponse> {

		@Override
		public AuthResponse handleResponse(ClassicHttpResponse hr) throws HttpException, IOException {
			HttpEntity entity = hr.getEntity();
			int statusCode = hr.getCode();
			JSONObject reply = null;
			if (statusCode == 200) {
				if (entity == null) {
					throw new ClientProtocolException("Invalid server response: Entity empty");
				}
				try{
					reply = new JSONObject(EntityUtils.toString(entity));
					AuthResponse response = new AuthResponse(true, "OK");
					response.serverHost = reply.getString("uftp.server.host");
					response.serverPort = Integer.parseInt(reply.getString("uftp.server.port"));
					return response;
				}catch(JSONException js){
					throw new IOException("Invalid reply", js);
				}
			}
			String msg = "Unable to authenticate (error code: "
					+ new StatusLine(hr)+") ";
			
			if (statusCode == 401 || statusCode==403) {
				msg += "==> Please check your user name and/or credentials!";
			}
			else if (statusCode == 404) {
				msg += "==> Please check the server URL!";
			}
			else  {
				try {
					reply = new JSONObject(EntityUtils.toString(entity));
					msg += reply.optString("errorMessage", "");
				}catch(Exception ex) {}
			}
			throw new IOException(msg);
		}
	}
		
	private static final char[] chars = "abcdefghijklmnopqrstuvwxyz".toCharArray();

	static String generateSecret() {
		StringBuilder sb = new StringBuilder();
		Random random = new Random();
		for (int i = 0; i < 20; i++) {
			char c = chars[random.nextInt(chars.length)];
			sb.append(c);
		}
		String output = sb.toString();
		return output;
	}
}
