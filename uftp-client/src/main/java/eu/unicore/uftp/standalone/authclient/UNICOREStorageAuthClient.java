package eu.unicore.uftp.standalone.authclient;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Random;

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

import eu.unicore.uftp.authserver.authenticate.AuthData;
import eu.unicore.uftp.authserver.messages.AuthResponse;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.util.Log;

/**
 * create a session using the UNICORE Storage API
 * 
 * @author schuller
 */
public class UNICOREStorageAuthClient implements AuthClient {

	private final String uri;

	private final AuthData authData;

	private final ClientFacade client;

	private static final Logger LOG = Log.getLogger(Log.CLIENT, UNICOREStorageAuthClient.class);

	public UNICOREStorageAuthClient(String authUrl, AuthData authData, ClientFacade client) {
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

	public HttpResponse getInfo() throws IOException {
		String infoURL = makeInfoURL(uri);
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
	public String parseInfo(JSONObject info) throws JSONException {
		StringBuilder sb = new StringBuilder();
		sb.append("Client identity:    ").append(getID(info)).append(crlf);
		sb.append("Client auth method: ").append(authData.getType()).append(crlf);
		sb.append("Auth server type:   UNICORE/X").append(crlf);
		sb.append("Remote user info:   ").append(getUserInfo(info)).append(crlf);
		try {
			String serverStatus = getServerStatus(info);
			sb.append("UFTP Server status: ").append(serverStatus).append(crlf);
		}catch(JSONException e) {}
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
		return status.optString("UFTPD Server", "N/A");
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

	public AuthData getAuthData() {
		return authData;
	}


	class UNICOREResponseHandler implements ResponseHandler<AuthResponse> {

		@Override
		public AuthResponse handleResponse(HttpResponse hr) throws ClientProtocolException, IOException {
			StatusLine statusLine = hr.getStatusLine();
			HttpEntity entity = hr.getEntity();
			JSONObject reply = null;
			if (statusLine.getStatusCode() == 200) {
				if (entity == null) {
					throw new ClientProtocolException("Invalid server response: Entity empty");
				}
				ContentType contentType = ContentType.getOrDefault(entity);
				Charset charset = contentType.getCharset();
				try{
					reply = new JSONObject(IOUtils.toString(entity.getContent(), charset));
					AuthResponse response = new AuthResponse(true, "OK");
					response.serverHost = reply.getString("uftp.server.host");
					response.serverPort = Integer.parseInt(reply.getString("uftp.server.port"));
					return response;
				}catch(JSONException js){
					throw new IOException("Invalid reply", js);
				}
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
					reply = new JSONObject(IOUtils.toString(entity.getContent(), charset));
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
