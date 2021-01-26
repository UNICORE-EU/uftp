
package eu.unicore.uftp.standalone.authclient;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.json.JSONException;
import org.json.JSONObject;

import eu.unicore.uftp.authserver.messages.AuthResponse;

/**
 *
 * @author jj
 */
public interface AuthClient {

    AuthResponse connect(String path, boolean send, boolean append) throws IOException;

    /** create session in default base directory **/
    default AuthResponse createSession() throws IOException {
        return createSession(null);
    }

    /** create session in the given base directory **/
    default AuthResponse createSession(String baseDir) throws IOException {
    	return createSession(baseDir, false);
    }
    
    /** create session in the given base directory **/
    AuthResponse createSession(String baseDir, boolean persistent) throws IOException;
    
    HttpResponse getInfo() throws IOException;
    
    default String parseInfo(JSONObject obj) throws JSONException {
    	return "";
    }
    
	public static final String sessionModeTag = "___UFTP___MULTI___FILE___SESSION___MODE___";

}
