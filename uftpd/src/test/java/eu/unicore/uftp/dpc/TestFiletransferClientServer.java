package eu.unicore.uftp.dpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.InetAddress;

import org.junit.Test;

import eu.unicore.uftp.client.UFTPClient;
import eu.unicore.uftp.dpc.TestFiletransferClientServerMultiStream.MockProgressListener;
import eu.unicore.uftp.server.ClientServerTestBase;
import eu.unicore.uftp.server.requests.UFTPPingRequest;
import eu.unicore.uftp.server.requests.UFTPTransferRequest;

public class TestFiletransferClientServer extends ClientServerTestBase {

	@Test
	public void testClientReadFileSingleConnection() throws Exception {
		for(int i=0; i<5; i++){
			doClientRead(1, null, 1, false,0);
		}
	}

	@Test
	public void testClientReadFileSingleConnectionCompress() throws Exception {
		for(int i=0; i<5; i++){
			doClientRead(1, null, 1, true,0);
		}
	}

	@Test
	public void testClientReadLargeFile() throws Exception {
		doClientRead(50, null, 1, false,0);
	}

	@Test
	public void testClientReadWithCompressionAndEncryption() throws Exception {
		doClientRead(5, "test123".getBytes(), 1, true,0);
		doClientRead(5, "test123".getBytes(), 2, true,0);
	}

	private long doClientRead(int sizeMBs, byte[] key, int numConnections,boolean compress, long rateLimit) throws Exception {
		File sourceFile = new File(dataDir,"testsourcefile-"+System.currentTimeMillis());
		sourceFile.deleteOnExit();
		makeTestFile(sourceFile, 1024*1024,sizeMBs);
		String target = "target/testdata/testfile-" + System.currentTimeMillis();
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", "secretCode", sourceFile, true);
		job.setCompress(compress);
		job.setRateLimit(rateLimit);
		job.setStreams(numConnections);
		job.setKey(key);
		job.sendTo(host[0], jobPort);
		Thread.sleep(2000);

		InetAddress[]servers=host;
		UFTPClient client = new UFTPClient(servers, srvPort, target, false, false);
		client.setSecret("secretCode");
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
	public void testClientReadLargeFile_WithRateLimit() throws Exception {
		long limit=1024*1024; // 1MB/s limit
		long finalRate=doClientRead(10, null, 1, false, limit);
		assertTrue(finalRate<1.1*limit);
		assertTrue(finalRate>0.9*limit);
	}

	@Test
	public void testClientCannotReadFileTwice() throws Exception {
		File sourceFile = new File("src/test/resources/testfile");
		String target = "target/testdata/testfile-" + System.currentTimeMillis();
		String secret1 = "secret_1";
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret1, sourceFile, true);
		
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);
		InetAddress[]server=host;
		UFTPClient client = new UFTPClient(server, srvPort, target, 
				/* send= */ false, 
				/* append=*/ false);
		client.setSecret(secret1);
		client.run();
		client.close();
		String secret2 = "secret_2";
		File sourceFile2 = new File("src/test/resources/testfile2");
		
		UFTPTransferRequest job2 = new UFTPTransferRequest(host, "nobody", secret2, sourceFile2, true);
		
		job2.sendTo(host[0], jobPort);

		//try to read *first file* another time

		client = new UFTPClient(server, srvPort, target, 
				/* send= */ false, 
				/* append=*/ false);
		client.setSecret(secret1);

		try{
			client.run();
			fail("Expected an error");
		}catch(Exception ex){
			// OK
		}
		client.close();
	}

	@Test
	public void testClientPutFileSingleConnection() throws Exception {
		for(int i=0; i<5; i++){
			doClientPut(1, null, 1, false, 0);
		}
	}

	@Test
	public void testClientPutCompressEncrypt() throws Exception {
		doClientPut(10, "test123".getBytes(), 1, true, 0);
		doClientPut(10, "test123".getBytes(), 1, false, 0);
		doClientPut(10, null, 1, true, 0);
		doClientPut(10, null, 2, false, 0);
		doClientPut(10, "test123".getBytes(), 2, false, 0);
		doClientPut(10, "test123".getBytes(), 2, true, 0);
	}

	private long doClientPut(int sizeMBs, byte[] key, int numConnections,boolean compress, long rateLimit) throws Exception {
		String source="target/testdata/source-" + System.currentTimeMillis();
		File sourceFile = new File(source);
		makeTestFile(sourceFile, 1024*1024, sizeMBs);

		String target = "target/testdata/testfile-" + System.currentTimeMillis();
		String secret = String.valueOf(System.currentTimeMillis());

		//tell server to receive data
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret, new File(target), false);
		job.setStreams(numConnections);
		job.setRateLimit(rateLimit);
		job.setKey(key);
		job.setCompress(compress);
		job.sendTo(host[0], jobPort);
		Thread.sleep(500);

		InetAddress[]server=host;
		UFTPClient client = new UFTPClient(server, srvPort, source, 
				/* send= */ true, 
				/* append=*/ false);
		client.setSecret(secret);
		client.setCompress(compress);
		client.setNumConnections(numConnections);
		client.setKey(key);
		client.run();
		client.close();
		Thread.sleep(1000);

		// check that file exists and has correct content
		File targetFile = new File(target);
		assertTrue("File "+targetFile+" does not exist",targetFile.exists());
		String expected = Utils.md5(sourceFile);
		String actual = Utils.md5(targetFile);
		assertEquals("File contents do not match", expected, actual);
		return client.getFinalTransferRate();
	}

	@Test
	public void testPing() throws Exception {
		//tell server to receive data
		UFTPPingRequest ping = new UFTPPingRequest();
		String reply = ping.sendTo(host[0], jobPort);
		System.out.println(reply);
	}

}