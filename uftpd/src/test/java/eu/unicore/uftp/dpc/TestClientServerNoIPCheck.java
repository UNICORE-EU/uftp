package eu.unicore.uftp.dpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.InetAddress;

import org.junit.BeforeClass;
import org.junit.Test;

import eu.unicore.uftp.client.UFTPClient;
import eu.unicore.uftp.dpc.TestFiletransferClientServerMultiStream.MockProgressListener;
import eu.unicore.uftp.server.ClientServerTestBase;
import eu.unicore.uftp.server.requests.UFTPPingRequest;
import eu.unicore.uftp.server.requests.UFTPTransferRequest;

public class TestClientServerNoIPCheck extends ClientServerTestBase {
	
	protected static InetAddress[] client_host;
	
	@BeforeClass
	public static void disableClientIPCheck()throws Exception{
		client_host=new InetAddress[]{InetAddress.getByName("172.217.22.99")};
		server.setCheckClientIP(false);
	}
	
	@Test
	public void testReadFile() throws Exception {
		for(int i=0; i<5; i++){
			doClientRead(1, null, 1, false, 0);
		}
	}

	private long doClientRead(int sizeMBs, byte[] key, int numConnections,boolean compress, long rateLimit) throws Exception {
		File sourceFile = new File(dataDir,"testsourcefile-"+System.currentTimeMillis());
		sourceFile.deleteOnExit();
		makeTestFile(sourceFile, 1024*1024,sizeMBs);
		String target = "target/testdata/testfile-" + System.currentTimeMillis();
		String secret = "secretCode";
		UFTPTransferRequest job = new UFTPTransferRequest(client_host, "nobody", secret, sourceFile, true);
		job.setCompress(compress);
		job.setRateLimit(rateLimit);
		job.setStreams(numConnections);
		job.setKey(key);
		job.sendTo(host[0], jobPort);
		Thread.sleep(2000);

		InetAddress[]servers=host;
		UFTPClient client = new UFTPClient(servers, srvPort, target, false, false);
		client.setSecret(secret);
		client.setKey(key);
		client.setNumConnections(numConnections);
		client.setCompress(compress);
		client.setProgressListener(new MockProgressListener());
		client.run();
		client.close();
		System.out.println("Finished client.");
		// check that file exists and has correct content
		File targetFile = new File(target);
		assertTrue(targetFile.exists());
		String expected = Utils.md5(sourceFile);
		String actual = Utils.md5(targetFile);
		assertEquals("File contents do not match", expected, actual);
		assertTrue(MockProgressListener.gotNotify);
		return client.getFinalTransferRate();
	}

	@Test
	public void testPing() throws Exception {
		//tell server to receive data
		UFTPPingRequest ping = new UFTPPingRequest();
		String reply = ping.sendTo(host[0], jobPort);
		System.out.println(reply);
	}
	
	@Test
	public void testRejectDuplicateSecret() throws Exception {
		UFTPTransferRequest job = new UFTPTransferRequest(client_host, "nobody", "test123", new File("./foo"), true);
		job.sendTo(host[0], jobPort);
		String reply = job.sendTo(host[0], jobPort);
		System.out.println(reply);
		assertTrue(reply.startsWith("500"));
	}


}