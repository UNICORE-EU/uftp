
package eu.unicore.uftp.standalone.authclient;

import junit.framework.TestCase;

import org.junit.Test;

import eu.unicore.uftp.authserver.authenticate.AuthData;
import eu.unicore.uftp.authserver.authenticate.UsernamePassword;
import eu.unicore.uftp.authserver.messages.AuthRequest;

/**
 *
 * @author jj
 */
public class AuthClientTest extends TestCase {
    
    public AuthClientTest(String testName) {
        super(testName);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }
    
    @Test
    public void testGetRequest() {
        System.out.println("getRequest");
        String destinationPath = "/some/path";
        boolean send = false;
        boolean append = false;
        int streamCount = 20;
        String encryptionKey = "encryptionKey";
        boolean compress = true;
        String clientIP = "127.0.0.1";
        String username = "user";
        String password = "pass";
        AuthData auth = new UsernamePassword(username, password);
        AuthserverClient instance = new AuthserverClient("https://server:9991", auth, null);
        AuthRequest result = instance.createRequestObject(destinationPath, send, append, streamCount, 
        		encryptionKey, compress, null, clientIP, true);
        
        assertNotNull(result);
        assertEquals(append, result.append);
        assertEquals(destinationPath, result.serverPath);
        assertEquals(send, result.send);
        assertEquals(streamCount, result.streamCount);
        assertEquals(compress, result.compress);
        UsernamePassword up = (UsernamePassword)instance.getAuthData();
        assertEquals(username, up.username);
        assertEquals(password, up.password);
        assertEquals(clientIP, result.client);
        assertTrue(result.persistent);
    }
    
    @Test
    public void testUP() {
    	 assertEquals("Basic ZGVtb3VzZXI6dGVzdDEyMw==", 
    			 new UsernamePassword("demouser", "test123").getHttpHeaders().get("Authorization"));
    }
    
    @Test
    public void testInfoURL() {
    	String u1 = "https://foo:1234/X/rest/core/storages/WORK:fjslfjlsdf/:/";
    	assertEquals("https://foo:1234/X/rest/core", UNICOREStorageAuthClient.makeInfoURL(u1));
    	String u2 = "https://foo:1234/X/rest/auth/TEST:fjslfjlsdf/:/";
    	assertEquals("https://foo:1234/X/rest/auth", AuthserverClient.makeInfoURL(u2));
    }
}
