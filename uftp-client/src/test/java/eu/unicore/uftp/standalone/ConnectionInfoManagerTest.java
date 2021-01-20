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
    final String scheme = "uftp";
    final String userName = "user";
    final String password = "pass";
    final String host = "example.server.com";
    String path = "/foo/bar/file.dat";
    final int port = 666;
    final String uriFormatingString = "%s://%s:%s@%s:%d%s";
    String mainUri = String.format(uriFormatingString, scheme, userName, password, host, port, path);

    private static final String remoteUri = "uftp://localhost/path";
    private static final String localUri = "/home/jj/path";
    private static final String notUri = "fda:/aa@d";

    public ConnectionInfoManagerTest() throws URISyntaxException {
        instance = new ConnectionInfoManager(new UsernamePassword(userName, password));
        instance.init(mainUri);
    }

    @Test
    public void testGetAuthURL() {
        System.out.println("getAuthURL");
        String result = instance.getAuthURL();
        assertEquals("https://"+host+":"+port, result);
    }
    
    @Test
    public void testGetAuthURL2() throws Exception {
        System.out.println("getAuthURL2");
        path = "/UFTP:/foo/bar/file.dat";
        mainUri = String.format(uriFormatingString, scheme, userName, password, host, port, path);
        System.out.println(mainUri);
        instance = new ConnectionInfoManager(new UsernamePassword(userName, password));
        instance.init(mainUri);
        String result = instance.getAuthURL();
        assertEquals("https://"+host+":"+port+"/UFTP", result);
    }

    @Test
    public void testGetPath() {
        System.out.println("getPath");
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
        System.out.println("getPort");
        int result = instance.getPort();
        assertEquals(port, result);
    }

    @Test
    public void testGetScheme() {
        System.out.println("getScheme");
        String result = instance.getScheme();
        assertEquals(scheme, result);
    }

    @Test
    public void testSameServer() {
        System.out.println("sameUri");
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
        System.out.println("extractConnectionParameters");
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
        System.out.println("Extract default port");
        Map<String, String> result = instance.extractConnectionParameters("uftp://localhost:666/path/to/file.dat");
        assertTrue(result.containsKey("host"));
        assertTrue(result.containsKey("path"));
        assertTrue(result.containsKey("port"));
        int localPort = Integer.valueOf(result.get("port"));
        assertEquals(666, localPort);
    }

    @Test
    public void testIsLocal2() {
        System.out.println("isLocal2");
        String argument = remoteUri;

        boolean result = ConnectionInfoManager.isLocal(argument);
        assertFalse(result);

        argument = localUri;
        result = ConnectionInfoManager.isLocal(argument);
        assertTrue(result);
    }

    @Test
    public void testWrongLocal() {
        System.out.println("wrongLocal");
        assertFalse(ConnectionInfoManager.isLocal("http://some"));
        assertFalse(ConnectionInfoManager.isLocal("https://some"));
        assertFalse(ConnectionInfoManager.isLocal("uftp://some"));
    }

    @Test
    public void testIsRemot2e() {
        System.out.println("isRemote");
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
    public void testNonUftpRemote() {
        System.out.println("nonUftp");
        String argument = remoteUri.replace("uftp", "http");
        boolean result = ConnectionInfoManager.isRemote(argument);
        assertTrue(result);
    }

    @Test
    public void testWrongRemote() {
        System.out.println("wrongRemote");
        boolean result = ConnectionInfoManager.isRemote(notUri);
        assertFalse(result);
    }
}
