package eu.unicore.uftp.authserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.Properties;

import org.apache.hc.core5.http.ClassicHttpResponse;
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
import eu.unicore.services.server.JettyServer;
import eu.unicore.uftp.authserver.messages.AuthRequest;
import eu.unicore.uftp.server.UFTPServer;

public class TestServiceSSL {

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

	protected static UFTPServer server;
	static int cmdPort = 63321;
	static int listenPort = 63320;
	protected static Kernel k;

	@BeforeAll
	public static void startUFTPD() throws Exception {
		System.setProperty("uftpd-ssl.conf", "src/test/resources/uftpd/uftpd-ssl.conf");
		System.setProperty("uftpd.acl", "src/test/resources/uftpd/uftpd.acl");
		InetAddress host = InetAddress.getByName("localhost");
		server = new UFTPServer(host, cmdPort, host, listenPort);
		Thread serverThread = new Thread(server);
		serverThread.start();
		// modify config since uftpd is set to ssl
		Properties extra = new Properties();
		extra.setProperty("authservice.server.TEST.ssl", "true");
		extra.setProperty("authservice.server.MULTI.1.ssl", "true");
		extra.setProperty("authservice.server.MULTI.2.ssl", "true");
		k = new Kernel("src/test/resources/container.properties", extra);
		k.start();
	}

	@AfterAll
	public static void stopUFTPD() throws Exception {
		if(server!=null)server.stop();
		if(k!=null)k.shutdown();
	}

}
