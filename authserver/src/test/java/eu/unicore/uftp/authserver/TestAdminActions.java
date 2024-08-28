package eu.unicore.uftp.authserver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import eu.unicore.services.Kernel;
import eu.unicore.services.admin.AdminAction;
import eu.unicore.services.admin.AdminActionResult;
import eu.unicore.uftp.authserver.admin.ShowUserInfo;
import eu.unicore.uftp.server.UFTPServer;

public class TestAdminActions {

	@Test
	public void testShowUserInfo() {
		AdminAction a = new ShowUserInfo();
		System.out.println(a.getName()+": "+a.getDescription());
		Map<String,String>params = new HashMap<>();
		params.put("uid", System.getProperty("user.name"));
		AdminActionResult res = a.invoke(params, k);
		assertTrue(res.successful());
		System.out.println(res.getMessage());
		System.out.println(res.getResults());
	}
	
	@Test
	public void testShowUserInfo2() {
		AdminAction a = new ShowUserInfo();
		Map<String,String>params = new HashMap<>();
		params.put("uid", System.getProperty("user.name"));
		params.put("serverName", "TEST");
		AdminActionResult res = a.invoke(params, k);
		assertTrue(res.successful());
		System.out.println(res.getMessage());
		System.out.println(res.getResults());
	}

	@Test
	public void testShowUserInfoErrors() {
		AdminAction a = new ShowUserInfo();
		Map<String,String>params = new HashMap<>();
		AdminActionResult res = a.invoke(params, k);
		assertFalse(res.successful());
		System.out.println(res.getMessage());
		params.put("uid", System.getProperty("user.name"));
		params.put("serverName", "X");
		res = a.invoke(params, k);
		assertFalse(res.successful());
		System.out.println(res.getMessage());
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
