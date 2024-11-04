package eu.unicore.uftp.server;

import java.net.InetAddress;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TestServerSSL {

	protected static int jobPort = 62434;

	protected static int srvPort = 62435;

	protected static InetAddress[] host;

	@BeforeAll
	public static void initUFTPServer()throws Exception{
		System.setProperty("uftpd-ssl.conf", "src/test/resources/certs/uftpd-ssl.conf");
		System.setProperty("uftpd.acl", "src/test/resources/certs/uftpd.acl");
		host=new InetAddress[]{InetAddress.getByName("localhost")};
		startServer();
		Thread.sleep(2000);
	}

	@AfterAll
	public static void stopUFTPServer()throws Exception{
		server.stop();
		System.getProperties().remove("uftpd-ssl.conf");
		System.getProperties().remove("uftpd.acl");
	}

	protected static UFTPServer server;
	
	private static void startServer() throws Exception {
		server = new UFTPServer(host[0], jobPort, host[0], srvPort);
		Thread serverThread = new Thread(server);
		serverThread.start();
	}

	@Test
	public void test1() throws Exception {
		
	}
}