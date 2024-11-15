package eu.unicore.uftp.authserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.InetAddress;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import eu.unicore.services.Kernel;
import eu.unicore.services.restclient.BaseClient;
import eu.unicore.services.restclient.IAuthCallback;
import eu.unicore.services.restclient.UsernamePassword;
import eu.unicore.services.restclient.jwt.JWTUtils;
import eu.unicore.services.restclient.sshkey.PasswordSupplierImpl;
import eu.unicore.services.restclient.sshkey.SSHKey;
import eu.unicore.services.server.JettyServer;
import eu.unicore.uftp.authserver.messages.AuthRequest;
import eu.unicore.uftp.server.UFTPServer;

public class TestService {

	@Test
	public void testAuthService() throws Exception {
		System.out.println(k.getConnectionStatus());
		String desc = k.getAttribute(AuthServiceConfig.class).getStatusDescription();
		assertTrue(desc.contains("TEST"));
		assertTrue(desc.contains("MULTI"));
		System.out.println("Configured "+desc);
		// do a get to find the configured servers
		JettyServer server=k.getServer();
		String url = server.getUrls()[0].toExternalForm()+"/rest/auth";
		IAuthCallback auth = new UsernamePassword("demouser", "test123");
		BaseClient client = new BaseClient(url, k.getClientConfiguration(), auth);
		JSONObject o = client.getJSON();
		System.out.println("Service reply: "+o.toString(2));

		JSONObject serverInfo = o.getJSONObject("TEST");
		// check attributes
		assertEquals("user1", serverInfo.getString("uid"));
		assertEquals("hpc1", serverInfo.getString("gid"));
		assertEquals(209715200, serverInfo.getLong("rateLimit"));
		
		// authenticate
		String authUrl = serverInfo.getString("href");
		client.setURL(authUrl);
		Gson gson = new GsonBuilder().create();
		AuthRequest req = new AuthRequest();
		req.serverPath="/tmp/foo";
		try(ClassicHttpResponse response = client.post(new JSONObject(gson.toJson(req)))){
			System.out.println("Service reply: " + EntityUtils.toString(response.getEntity()));
		}
	}

	@Test
	public void testSSHKeyAuthentication() throws Exception {
		// static public key from file
		IAuthCallback auth = new SSHKey("demouser", new File("src/test/resources/ssh/id_ed25519"),
				new PasswordSupplierImpl("test123".toCharArray()));
		JettyServer server=k.getServer();
		String url = server.getUrls()[0].toExternalForm()+"/rest/auth";
		BaseClient client = new BaseClient(url, k.getClientConfiguration(), auth);
		JSONObject o = client.getJSON();
		assertEquals("CN=Demo User, O=UNICORE, C=EU", o.getJSONObject("client").getString("dn"));
		assertEquals("user", o.getJSONObject("client").getJSONObject("role").getString("selected"));

		// public key loaded via UserInfoSource class in defined in container.properties
		auth = new SSHKey("demouser-dyn", new File("src/test/resources/ssh/id_ed25519"),
				new PasswordSupplierImpl("test123".toCharArray()));
		client = new BaseClient(url, k.getClientConfiguration(), auth);
		o = client.getJSON();
		assertEquals("CN=demouser-dyn, OU=ssh-local-users", o.getJSONObject("client").getString("dn"));
		assertEquals("user", o.getJSONObject("client").getJSONObject("role").getString("selected"));
	}
	
	@Test
	public void testReloadConfig() throws Exception {
		AuthServiceConfig conf = k.getAttribute(AuthServiceConfig.class);
		conf.reloadConfig(k);
	}

	@Test
	public void testGetToken() throws Exception {
		JettyServer server=k.getServer();
		String url = server.getUrls()[0].toExternalForm()+"/rest/auth/token";
		IAuthCallback auth = new UsernamePassword("demouser", "test123");
		BaseClient client = new BaseClient(url, k.getClientConfiguration(), auth);
		try(ClassicHttpResponse res = client.get(ContentType.TEXT_PLAIN)){
			String tokS = EntityUtils.toString(res.getEntity(), "UTF-8");
			JSONObject tok= JWTUtils.getPayload(tokS);
			System.out.println("Issued token payload: "+tok.toString(2));
			assertEquals("CN=Demo User, O=UNICORE, C=EU", tok.getString("sub"));
		}
	}

	protected static UFTPServer server;
	static int cmdPort = 63321;
	static int listenPort = 63320;
	protected static Kernel k;

	@BeforeAll
	public static void startUFTPD() throws Exception {
		InetAddress host = InetAddress.getByName("localhost");
		server = new UFTPServer(host, cmdPort, host, listenPort);
		Thread serverThread = new Thread(server);
		serverThread.start();
		k = new Kernel("src/test/resources/container.properties");
		k.start();
	}

	@AfterAll
	public static void stopUFTPD() throws Exception {
		if(server!=null)server.stop();
		if(k!=null)k.shutdown();
	}

}
