package eu.unicore.uftp.standalone.oidc;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

import eu.unicore.uftp.authserver.authenticate.AuthData;

public class OIDCAgentAuth implements AuthData {
	
	private String account;

	private OIDCAgentProxy ap;
	
	private String token;
	
	public OIDCAgentAuth(String account) {
		this.account = account;
	}
	
	
	@Override
	public Map<String,String>getHttpHeaders() {
		if(token==null) {
			try{
				retrieveToken();
			}catch(Exception ex){
				throw new RuntimeException(ex);
			}
		}
		Map<String,String> res = new HashMap<String, String>();
		res.put("Authorization","Bearer "+token);
		return res;
	}
	
	protected void retrieveToken() throws Exception {
		setupOIDCAgent();
		
		JSONObject request = new JSONObject();
		request.put("request", "access_token");
		request.put("account", account);
		
		JSONObject reply = new JSONObject(ap.send(request.toString()));
		boolean success = "success".equalsIgnoreCase(reply.getString("status"));
		if(!success){
			String error = reply.getString("error");
			throw new IOException("Error received from oidc-agent: <"+error+">");
		}
		
		token = reply.getString("access_token");
		
	}

	protected void setupOIDCAgent() throws Exception {
		if(!OIDCAgentProxy.isConnectorAvailable())throw new IOException("oidc-agent is not available");
		ap = new OIDCAgentProxy();
	}
	
	@Override
	public String getType() {
		return "OIDC-AGENT";
	}
}
