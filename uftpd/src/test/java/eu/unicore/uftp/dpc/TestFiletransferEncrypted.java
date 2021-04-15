package eu.unicore.uftp.dpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;

import org.junit.BeforeClass;
import org.junit.Test;

import eu.unicore.uftp.client.UFTPClient;
import eu.unicore.uftp.server.ClientServerTestBase;
import eu.unicore.uftp.server.requests.UFTPTransferRequest;

/**
 * test encrypted data transfer
 */
public class TestFiletransferEncrypted extends ClientServerTestBase{

	private static byte[] key=null;

	@BeforeClass
	public static void setUp2()throws Exception{
		key=Utils.createKey();
	}

	@Test
	public void testEncryptedSingleStream() throws Exception {
		int numCon=1;
		
		File sourceFile = new File(dataDir,"testsourcefile-"+System.currentTimeMillis());
		makeTestFile2(sourceFile, 1024);
		String target = "target/testdata/testfile-" + System.currentTimeMillis();

		// send job to server...
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", "secretCode", sourceFile, true);
		job.setStreams(numCon);
		job.setKey(key);
		
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);
		
		InetAddress[]servers=host;
		UFTPClient client = new UFTPClient(servers, srvPort, target, false, false);
		client.setSecret("secretCode");
		client.setNumConnections(numCon);
		client.setKey(key);
		client.run();
		System.out.println("Finished client.");
		// check that file exists and has correct content
		File targetFile = new File(target);
		assertTrue(targetFile.exists());
		String expected = Utils.md5(sourceFile);
		String actual = Utils.md5(targetFile);
		System.out.println("Source file "+sourceFile.getAbsolutePath()+" "+expected);
		System.out.println("Target file "+targetFile.getAbsolutePath()+" "+actual);
		assertEquals("File contents do not match", expected, actual);
		client.close();
	}
	
	@Test
	public void testEncryptedMultiStream() throws Exception {
		int numCon=2;
		//key=null;
		boolean compress = false;
		
		File sourceFile = new File(dataDir,"testsourcefile-"+System.currentTimeMillis());
		makeTestFile2(sourceFile, 1024*500);
		String target = "target/testdata/testfile-" + System.currentTimeMillis();

		// send job to server...
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", "secretCode", sourceFile, true);
		job.setKey(key);
		job.setCompress(compress);
		job.setStreams(numCon);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		InetAddress[]servers=host;
		UFTPClient client = new UFTPClient(servers, srvPort, target, false, false);
		client.setSecret("secretCode");
		client.setNumConnections(numCon);
		client.setKey(key);
		client.setCompress(compress);
		client.run();
		System.out.println("Finished client.");
		// check that file exists and has correct content
		File targetFile = new File(target);
		assertTrue(targetFile.exists());
		String expected = Utils.md5(sourceFile);
		String actual = Utils.md5(targetFile);
		System.out.println("Source file "+sourceFile.getAbsolutePath()+" "+expected);
		System.out.println("Target file "+targetFile.getAbsolutePath()+" "+actual);
		assertEquals("File contents do not match", expected, actual);
		client.close();
	}

	private void makeTestFile2(File file, int lines) throws IOException {
		FileOutputStream fos = new FileOutputStream(file);
		try {
			for (int i = 0; i < lines; i++) {
				fos.write( (i+": test1 test2 test3\n").getBytes());
			}
		} finally {
			fos.close();
		}
	}

}