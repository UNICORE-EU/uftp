package eu.unicore.uftp.authserver.authenticate;

import java.util.Map;

/**
 * generic interface holding authentication info sent from client to 
 * auth server (e.g.: username and password) via HTTP headers
 * 
 * @author schuller
 */
public interface AuthData {

	public String getType();

	/**
	 * key-value pairs to be used as http headers
	 */
	public Map<String,String>getHttpHeaders();
}
