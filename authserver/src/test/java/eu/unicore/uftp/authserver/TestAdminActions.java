package eu.unicore.uftp.authserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import eu.unicore.uftp.authserver.admin.ModifyShare;
import eu.unicore.uftp.authserver.admin.ShowUserInfo;
import eu.unicore.uftp.authserver.share.ShareServiceProperties;
import eu.unicore.uftp.datashare.AccessType;
import eu.unicore.uftp.datashare.db.ACLStorage;
import eu.unicore.uftp.datashare.db.ShareDAO;
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
	
	@Test
	public void testModifyShare() throws Exception {
		// add a share
		ACLStorage db = k.getAttribute(ShareServiceProperties.class).getDB("TEST");
		ShareDAO d1 = new ShareDAO();
		d1.setAccess(AccessType.READ);
		d1.setPath("/foo/bar");
		d1.setUid("user1");
		d1.setGid("group1");
		d1.setOwnerID("cn=someone");
		d1.setTargetID("cn=anon");
		System.out.println("Added share: "+d1);
		db.getPersist().write(d1);

		AdminAction a = new ModifyShare();
		System.out.println("Invoking "+a.getName()+" "+a.getDescription());
		Map<String,String>params = new HashMap<>();
		AdminActionResult res = a.invoke(params, k);
		assertFalse(res.successful());
		params.put("id", d1.getID());
		res = a.invoke(params, k);
		assertFalse(res.successful());
		params.put("id", d1.getID());
		params.put("serverName", "TEST");
		params.put("x_id", "false");
		res = a.invoke(params, k);
		assertFalse(res.successful());
		params.remove("x_id");
		params.put("id", d1.getID());
		params.put("serverName", "TEST");
		params.put("uid", "user2");
		params.put("gid", "group2");
		params.put("path", "/foo/spam");
		params.put("owner", "cn=someone_else");
		params.put("target", "cn=foo");
		res = a.invoke(params, k);
		assertTrue(res.successful());
		System.out.println("Modified to: "+res.getMessage());
		ShareDAO d2 = db.read(d1.getID());
		assertEquals(res.getMessage(), d2.toString());
	}

	protected static UFTPServer server;
	static int cmdPort = 63321;
	static int listenPort = 63320;
	protected static Kernel k;

	@BeforeAll
	public static void startServers() throws Exception {
		InetAddress host = InetAddress.getByName("localhost");
		server = new UFTPServer(host, cmdPort, host, listenPort);
		Thread serverThread = new Thread(server);
		serverThread.start();
		k = new Kernel("src/test/resources/container.properties");
		k.start();
	}

	@AfterAll
	public static void stopServers() throws Exception {
		if(server!=null)server.stop();
		if(k!=null)k.shutdown();
	}

}
