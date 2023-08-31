
package eu.unicore.uftp.standalone.authclient;

import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author jj
 */
public interface AuthClient {

    AuthResponse connect(String path, boolean send, boolean append) throws Exception;

    /** create session in default base directory **/
    default AuthResponse createSession() throws Exception {
        return createSession(null);
    }

    /** create session in the given base directory **/
    default AuthResponse createSession(String baseDir) throws Exception {
    	return createSession(baseDir, false);
    }
    
    /** create session in the given base directory **/
    AuthResponse createSession(String baseDir, boolean persistent) throws Exception;
    
    JSONObject getInfo() throws Exception;
    
    default String parseInfo(JSONObject obj) throws JSONException {
    	return "";
    }

}
