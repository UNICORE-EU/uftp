package eu.unicore.uftp.dpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.InetAddress;

import org.junit.Test;

import eu.unicore.uftp.client.UFTPClient;
import eu.unicore.uftp.client.UFTPProgressListener;
import eu.unicore.uftp.jparss.PReader;
import eu.unicore.uftp.jparss.PWriter;
import eu.unicore.uftp.server.ClientServerTestBase;
import eu.unicore.uftp.server.requests.UFTPTransferRequest;

public class TestFiletransferClientServerMultiStream extends ClientServerTestBase{

	@Test
	public void testClientReadLargeFileMultiConnection() throws Exception {
		int numCon=2;
		
		File sourceFile = new File(dataDir,"testsourcefile-"+System.currentTimeMillis());
		sourceFile.deleteOnExit();
		makeTestFile(sourceFile, 1024*1024,50);
		String target = "target/testdata/testfile-" + System.currentTimeMillis();

		// send job to server...
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", "secretCode", sourceFile, true);
		job.setStreams(numCon);
		job.sendTo(host[0], jobPort);
		Thread.sleep(2000);

		InetAddress[]servers=host;
		UFTPClient client = new UFTPClient(servers, srvPort, target, false, false);
		client.setSecret("secretCode");
		client.setNumConnections(2);
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
		System.out.println("Stopping Server.");
		server.stop();
		assertTrue(MockProgressListener.gotNotify);

		assertEquals(0, PReader.activeReaders.get());
		assertEquals(0, PWriter.activeWriters.get());
	}
	
	
	public static class MockProgressListener implements UFTPProgressListener{

		public static boolean gotNotify=false;
		
		public void notifyTotalBytesTransferred(long totalBytesSent) {
			gotNotify=true;
		}

	}

}