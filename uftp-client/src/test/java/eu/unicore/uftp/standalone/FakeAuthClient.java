
package eu.unicore.uftp.standalone;

import java.io.IOException;

import org.apache.http.HttpResponse;

import eu.unicore.uftp.authserver.messages.AuthResponse;
import eu.unicore.uftp.standalone.authclient.AuthClient;

/**
 *
 * @author jj
 */
public class FakeAuthClient implements AuthClient {

    @Override
    public AuthResponse connect(String path, boolean send, boolean append) throws IOException {
        System.out.printf("Connecting and accessing %s\n", path);
        return new AuthResponse(true, "", "remoteUftp", 666, "secret");
    }

    @Override
    public AuthResponse createSession(String baseDir) throws IOException {
        System.out.printf("Creating session\n");
        return new AuthResponse(true, "", "remoteUftp", 666, "secret");
    }
    
    public AuthResponse createSession(String baseDir, boolean persistent) throws IOException {
        return createSession(baseDir);
    }
    
    public HttpResponse getInfo() throws IOException{
    	return null;
    }
}
