package eu.unicore.uftp.authserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import eu.unicore.services.Kernel;
import eu.unicore.services.admin.AdminAction;
import eu.unicore.services.admin.AdminActionResult;
import eu.unicore.services.rest.client.BaseClient;
import eu.unicore.services.rest.client.IAuthCallback;
import eu.unicore.services.rest.client.UsernamePassword;
import eu.unicore.services.rest.security.sshkey.PasswordSupplierImpl;
import eu.unicore.services.rest.security.sshkey.SSHKey;
import eu.unicore.services.server.JettyServer;
import eu.unicore.uftp.authserver.admin.ShowUserInfo;
import eu.unicore.uftp.authserver.messages.AuthRequest;
import eu.unicore.uftp.authserver.messages.CreateTunnelRequest;
import eu.unicore.uftp.server.UFTPServer;

public class TestService {

	@Test
	public void testAuthService() throws Exception {
		// do a get to find the configured servers
		JettyServer server=k.getServer();
		String url = server.getUrls()[0].toExternalForm()+"/rest/auth";
		IAuthCallback auth = new UsernamePassword("demouser", "test123");
		BaseClient client = new BaseClient(url, k.getClientConfiguration(), auth);
		JSONObject o = client.getJSON();
		System.out.println("Service reply: "+o.toString(2));

		JSONObject serverInfo = o.getJSONObject("TEST");
		String authUrl = serverInfo.getString("href");
		client.setURL(authUrl);
		// now do an actual authn post
		Gson gson = new GsonBuilder().create();
		AuthRequest req = new AuthRequest();
		req.serverPath="/tmp/foo";
		try(ClassicHttpResponse response = client.post(new JSONObject(gson.toJson(req)))){
			System.out.println("Service reply: " + EntityUtils.toString(response.getEntity()));
		}
		authUrl = serverInfo.getString("href")+"/tunnel";
		client.setURL(authUrl);
		CreateTunnelRequest req2 = new CreateTunnelRequest();
		req2.targetHost = "localhost";
		req2.targetPort = 9001;
		try(ClassicHttpResponse response = client.post(new JSONObject(gson.toJson(req)))){
			System.out.println("Service reply: " + EntityUtils.toString(response.getEntity()));
		}
	}


	@Test
	public void testAdminShowUserInfo() {
		AdminAction a = new ShowUserInfo();
		Map<String,String>params = new HashMap<>();
		params.put("uid", System.getProperty("user.name"));
		AdminActionResult res = a.invoke(params, k);
		assertTrue(res.successful());
		System.out.println(res.getMessage());
		System.out.println(res.getResults());
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

	protected static UFTPServer server;
	static int cmdPort = 63321;
	static int listenPort = 63320;
	protected static Kernel k;

	@BeforeClass
	public static void startUFTPD() throws Exception {
		InetAddress host = InetAddress.getByName("localhost");
		server = new UFTPServer(host, cmdPort, host, listenPort);
		Thread serverThread = new Thread(server);
		serverThread.start();
		k = new Kernel("src/test/resources/container.properties");
		k.start();
	}

	@AfterClass
	public static void stopUFTPD() throws Exception {
		if(server!=null)server.stop();
		if(k!=null)k.shutdown();
	}

}
