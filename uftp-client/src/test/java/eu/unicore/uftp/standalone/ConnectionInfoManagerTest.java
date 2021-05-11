package eu.unicore.uftp.standalone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URISyntaxException;
import java.util.Map;

import org.junit.Test;

import eu.unicore.uftp.authserver.authenticate.UsernamePassword;

/**
 *
 * @author jj
 */
public class ConnectionInfoManagerTest {

    ConnectionInfoManager instance;
    final String scheme = "http";
    final String userName = "user";
    final String password = "pass";
    final String host = "example.server.com";
    String path = "/foo/bar/file.dat";
    final int port = 666;
    final String uriFormatingString = "%s://%s:%s@%s:%d/UFTP:%s";
    String mainUri = String.format(uriFormatingString, scheme, userName, password, host, port, path);

    private static final String remoteUri = "http://localhost/UFTP:/path";
    private static final String localUri = "/home/jj/path";
    private static final String notUri = "fda:/aa@d";

    public ConnectionInfoManagerTest() throws URISyntaxException {
        instance = new ConnectionInfoManager(new UsernamePassword(userName, password));
        instance.init(mainUri);
    }

    @Test
    public void testGetAuthURL() {
        String result = instance.getAuthURL();
        assertEquals(scheme+"://"+host+":"+port+"/UFTP", result);
    }
    
    @Test
    public void testGetAuthURL2() throws Exception {
        mainUri = String.format(uriFormatingString, scheme, userName, password, host, port, path);
        instance = new ConnectionInfoManager(new UsernamePassword(userName, password));
        instance.init(mainUri);
        String result = instance.getAuthURL();
        assertEquals(scheme+"://"+host+":"+port+"/UFTP", result);
    }

    @Test
    public void testGetPath() {
        String result = instance.getPath();
        assertEquals(path, result);
    }

    @Test
    public void testEmptyPath() throws Exception {
    	ConnectionInfoManager cim = new ConnectionInfoManager(new UsernamePassword(userName, password));
    	String authUrl = "https://host:1234/SITE/rest/auth/SERVER";
    	cim = new ConnectionInfoManager(new UsernamePassword(userName, password));
    	cim.init(authUrl+":");
    	assertEquals("", cim.getPath());
    	assertEquals(authUrl, cim.getAuthURL());
    	cim.init(authUrl);
    	assertEquals("", cim.getPath());
    	assertEquals(authUrl, cim.getAuthURL());
    }
    
    @Test
    public void testGetPort() {
        int result = instance.getPort();
        assertEquals(port, result);
    }

    @Test
    public void testGetScheme() {
        String result = instance.getScheme();
        assertEquals(scheme, result);
    }

    @Test
    public void testSameServer() {
        boolean result = instance.isSameServer(mainUri);
        assertTrue(result);

        //different server:
        result = instance.isSameServer(remoteUri);
        assertFalse(result);

        String sameHostDifferentPath = String.format(uriFormatingString, scheme,
                userName, password, host, port, "/some/other/path");
        result = instance.isSameServer(sameHostDifferentPath);
        assertTrue(result);

        //no  credentials:        
        String noCredentials = String.format(uriFormatingString, scheme, userName,
                password, host, port, "/some/other/path").replaceFirst(userName + ":" + password, "");
        result = instance.isSameServer(noCredentials);
        assertTrue(result);

        String localPath = "/home/homer/dat.dat";
        assertFalse(instance.isSameServer(localPath));
    }

    @Test
    public void testExtractConnectionParameters() throws URISyntaxException {
        String uriString = remoteUri;

        Map<String, String> result = instance.extractConnectionParameters(uriString);
        assertTrue(result.containsKey("host"));
        assertTrue(result.containsKey("path"));
        assertTrue(result.containsKey("scheme"));
        assertTrue(result.containsKey("port"));

        assertEquals("localhost", result.get("host"));
        assertEquals("/path", result.get("path"));
        assertEquals(scheme, result.get("scheme"));
        assertEquals(String.valueOf(ConnectionInfoManager.defaultPort), result.get("port"));
    }

    @Test
    public void testExtractDefaultPort() throws URISyntaxException {
        Map<String, String> result = instance.extractConnectionParameters("uftp://localhost:666/path/to/file.dat");
        assertTrue(result.containsKey("host"));
        assertTrue(result.containsKey("path"));
        assertTrue(result.containsKey("port"));
        int localPort = Integer.valueOf(result.get("port"));
        assertEquals(666, localPort);
    }

    @Test
    public void testIsLocal2() {
        String argument = remoteUri;

        boolean result = ConnectionInfoManager.isLocal(argument);
        assertFalse(result);

        argument = localUri;
        result = ConnectionInfoManager.isLocal(argument);
        assertTrue(result);
    }

    @Test
    public void testWrongLocal() {
        assertFalse(ConnectionInfoManager.isLocal("http://some"));
        assertFalse(ConnectionInfoManager.isLocal("https://some"));
        assertFalse(ConnectionInfoManager.isLocal("uftp://some"));
    }

    @Test
    public void testIsRemot2e() {
        String argument = remoteUri;
        boolean expResult = true;
        boolean result = ConnectionInfoManager.isRemote(argument);
        assertEquals(expResult, result);

        argument = localUri;
        expResult = false;
        result = ConnectionInfoManager.isRemote(argument);
        assertEquals(expResult, result);
    }

    @Test
    public void testUftpRemote() {
        String argument = remoteUri.replace("http", "uftp");
        boolean result = ConnectionInfoManager.isRemote(argument);
        assertTrue(result);
    }

    @Test
    public void testWrongRemote() {
        boolean result = ConnectionInfoManager.isRemote(notUri);
        assertFalse(result);
    }
}
