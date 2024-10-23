package eu.unicore.uftp.dpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.server.ClientServerTestBase;
import eu.unicore.uftp.server.UFTPCommands;
import eu.unicore.uftp.server.requests.UFTPPingRequest;
import eu.unicore.uftp.server.requests.UFTPSessionRequest;

public class TestServerSettings extends ClientServerTestBase {
	
	protected static InetAddress[] client_host;
	
	@BeforeAll
	public static void setupClientHost()throws Exception{
		client_host=new InetAddress[]{InetAddress.getByName("172.217.22.99")};
	}
	
	@Test
	public void testConnect() throws Exception {
		server.setCheckClientIP(true);
		String secret = UUID.randomUUID().toString();
		UFTPSessionRequest job = new UFTPSessionRequest(client_host, "nobody", secret, ".");
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);
		assertThrows(IOException.class, ()->{
			try(UFTPSessionClient client=new UFTPSessionClient(host, srvPort)){
				client.setSecret(secret);
				client.connect();
			}
		});
		server.setCheckClientIP(false);
		try(UFTPSessionClient client=new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();
			client.pwd();
		}
	}

	@Test
	public void testPing() throws Exception {
		UFTPPingRequest ping = new UFTPPingRequest();
		String reply = ping.sendTo(host[0], jobPort);
		System.out.println(reply);
	}
	
	@Test
	public void testRejectDuplicateSecret() throws Exception {
		UFTPSessionRequest job = new UFTPSessionRequest(client_host, "nobody", "test123",
				"");
		job.sendTo(host[0], jobPort);
		String reply = job.sendTo(host[0], jobPort);
		System.out.println(reply);
		assertTrue(reply.startsWith("500"));
	}

	@Test
	public void testRANGBug() throws Exception {
		server.setCheckClientIP(false);
		String fileName = "testdata.dat";
		File datafile = new File(dataDir, fileName);
		FileUtils.write(datafile, "0123456789", "UTF-8");
		
		String secret = UUID.randomUUID().toString();
		UFTPSessionRequest job = new UFTPSessionRequest(client_host, "nobody", secret,
				dataDir.getAbsolutePath());
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);
		server.setRFCRangeMode(false);
		
		try(UFTPSessionClient client=new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();
			assertFalse(client.getServerFeatures().contains(UFTPCommands.FEATURE_RFC_RANG));
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			client.get(fileName, 0, 5, bos);
			assertEquals("01234", bos.toString());
		}
		
		secret = UUID.randomUUID().toString();
		job = new UFTPSessionRequest(client_host, "nobody", secret,
				dataDir.getAbsolutePath());
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);
		server.setRFCRangeMode(true);

		try(UFTPSessionClient client=new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();
			System.out.println(client.getServerFeatures());
			assertTrue(client.getServerFeatures().contains(UFTPCommands.FEATURE_RFC_RANG));
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			client.get(fileName, 0, 5, bos);
			assertEquals("01234", bos.toString());
		}
	}


	@Test
	public void testPASVConnect() throws Exception {
		String secret = UUID.randomUUID().toString();
		UFTPSessionRequest job = new UFTPSessionRequest(client_host, "nobody", secret, ".");
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);
		server.setCheckClientIP(false);
		try(UFTPSessionClient client=new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();
			client.getServerFeatures().remove(UFTPCommands.EPSV);
			client.pwd();
		}
	}
}