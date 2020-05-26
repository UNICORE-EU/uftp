package eu.unicore.uftp.authserver.authenticate.sshkey;

import java.util.HashMap;
import java.util.Map;

import eu.unicore.uftp.authserver.authenticate.AuthData;
import eu.unicore.uftp.authserver.authenticate.UsernamePassword;

/**
 * token used to verify that the user owns an accepted private key (SSH key)
 * 
 * @author schuller
 */
public class SSHKey implements AuthData {
	
	public static String HEADER_PLAINTEXT_TOKEN = "X-UNICORE-SSHKEY-AUTH-PLAINTEXT";
	
	public static final String TYPE="SSHKEY";

	public String username;

	/**
	 * Base64 encoded and sha1 hashed token
	 */
	public String token;

	/**
	 * Base64 encoded signature
	 */
	public String signature;

	@Override
	public String getType() {
		return TYPE;
	}

	public Map<String,String>getHttpHeaders() {
		Map<String,String> res = new HashMap<String, String>();
		res.put("Authorization",UsernamePassword.getBasicAuth(username, signature));
		res.put(HEADER_PLAINTEXT_TOKEN,token);
		return res;
	}
	
	public String toString(){
		return "[SSH auth "+username+":"+token+":"+signature+"]";
	}

}
