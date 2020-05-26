package eu.unicore.uftp.authserver.authenticate;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

/**
 * simple username password
 * 
 * @author schuller
 */
public class UsernamePassword implements AuthData {
	
	public static final String TYPE="USERNAMEPASSWORD";

	public String username;

	public String password;

	public UsernamePassword(String username, String password){
		this.username = username;
		this.password = password;
	}
	
	@Override
	public String getType() {
		return TYPE;
	}
	
	@Override
	public Map<String,String>getHttpHeaders() {
		Map<String,String> res = new HashMap<String, String>();
		res.put("Authorization",getBasicAuth(username, password));
		return res;
	}

	public static String getBasicAuth(String username, String password){
		return "Basic "+new String(Base64.encodeBase64((username+":"+password).getBytes()));
	}

	public static Header getBasicAuthHeader(String username, String password){
		return new BasicHeader("Authorization", getBasicAuth(username, password));
	}
}
