package eu.unicore.uftp.authserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.InetAddress;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.fzj.unicore.wsrflite.Kernel;
import de.fzj.unicore.wsrflite.server.JettyServer;
import eu.unicore.uftp.authserver.authenticate.UsernamePassword;
import eu.unicore.uftp.authserver.messages.AuthRequest;
import eu.unicore.uftp.authserver.messages.CreateTunnelRequest;
import eu.unicore.uftp.server.UFTPServer;
import eu.unicore.util.httpclient.HttpUtils;

public class TestService {

	@Test
	public void testAuthService() throws Exception {
		Kernel k = new Kernel("src/test/resources/container.properties");
		k.start();

		//setup basic auth
		String userName = "demouser";
		String password = "test123";
		// do a get to find the configured servers
		JettyServer server=k.getServer();
		String url = server.getUrls()[0].toExternalForm()+"/rest";
		HttpClient client = HttpUtils.createClient(url, k.getClientConfiguration());
		String resource  = url+"/auth";
		HttpGet get=new HttpGet(resource);
		get.addHeader(UsernamePassword.getBasicAuthHeader(userName, password));
		HttpResponse response=client.execute(get);
		int status=response.getStatusLine().getStatusCode();
		assertEquals("got "+response.getStatusLine(),200, status);
		String reply=IOUtils.toString(response.getEntity().getContent());
		System.out.println("Service reply: "+reply);
		JSONObject o = new JSONObject(reply);
		JSONObject s = o.getJSONObject("TEST");
		assertNotNull(s.getString("href"));
		String authUrl = s.getString("href");

		// now do an actual authn post
		HttpPost post=new HttpPost(authUrl);
		post.addHeader(UsernamePassword.getBasicAuthHeader(userName, password));
		Gson gson = new GsonBuilder().create();
		AuthRequest req = new AuthRequest();
		req.serverPath="/tmp/foo";
		post.setEntity(new StringEntity(gson.toJson(req), ContentType.APPLICATION_JSON));
		response=client.execute(post);
		status=response.getStatusLine().getStatusCode();
		assertEquals("got "+response.getStatusLine(),200, status);
		reply=IOUtils.toString(response.getEntity().getContent());
		System.out.println("Service reply: "+reply);
	
		url = server.getUrls()[0].toExternalForm()+"/rest";
		resource  = url+"/auth";
		get=new HttpGet(resource);
		get.addHeader(UsernamePassword.getBasicAuthHeader(userName, password));
		response=client.execute(get);
		status=response.getStatusLine().getStatusCode();
		assertEquals("got "+response.getStatusLine(),200, status);
		reply=IOUtils.toString(response.getEntity().getContent());
		System.out.println("Service reply: "+reply);
		o = new JSONObject(reply);
		s = o.getJSONObject("TEST");
		assertNotNull(s.getString("href"));
		authUrl = s.getString("href")+"/tunnel";

		post=new HttpPost(authUrl);
		post.addHeader(UsernamePassword.getBasicAuthHeader(userName, password));
		CreateTunnelRequest req2 = new CreateTunnelRequest();
		req2.targetHost = "localhost";
		req2.targetPort = 9001;
		post.setEntity(new StringEntity(gson.toJson(req2), ContentType.APPLICATION_JSON));
		response=client.execute(post);
		status=response.getStatusLine().getStatusCode();
		assertEquals("got "+response.getStatusLine(),200, status);
		reply=IOUtils.toString(response.getEntity().getContent());
		System.out.println("Service reply: "+reply);
		k.shutdown();
		stopUFTPD();
	}

	protected static UFTPServer server;
	static int cmdPort = 63321;
	static int listenPort = 63320;

	@Before
	public void startUFTPD() throws Exception {
		InetAddress host = InetAddress.getByName("localhost");
		server = new UFTPServer(host, cmdPort, host, listenPort);
		Thread serverThread = new Thread(server);
		serverThread.start();
	}

	@After
	public void stopUFTPD() throws Exception {
		server.stop();
	}

}
