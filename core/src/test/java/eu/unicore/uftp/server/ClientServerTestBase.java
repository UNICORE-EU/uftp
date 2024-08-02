package eu.unicore.uftp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import eu.unicore.uftp.client.UFTPProgressListener;
import eu.unicore.uftp.dpc.Utils;

public abstract class ClientServerTestBase {

	protected static int jobPort = 62434;

	protected static int srvPort = 62435;

	protected static InetAddress[] host;
	
	//all temporary test files should go into this dir ("target/testdata")
	protected static File dataDir=new File("target","testdata");
	
	@BeforeAll
	public static void initUFTPServer()throws Exception{
		FileUtils.deleteQuietly(dataDir);
		dataDir.mkdirs();
		host=new InetAddress[]{InetAddress.getByName("localhost")};
		assertNotNull(host);
		startServer();
		Thread.sleep(2000);
	}

	@AfterAll
	public static void stopUFTPServer()throws Exception{
		server.stop();
	}

	protected static UFTPServer server;
	
	private static void startServer() throws Exception {
		server = new UFTPServer(host[0], jobPort, host[0], srvPort);
		Thread serverThread = new Thread(server);
		serverThread.start();
	}

	protected static void makeTestFile(File file, int chunkSize, int chunks)
			throws IOException {
		FileOutputStream fos = new FileOutputStream(file);
		try {
			Random r = new Random();
			byte[] buf = new byte[chunkSize];
			for (int i = 0; i < chunks; i++) {
				r.nextBytes(buf);
				fos.write(buf);
			}
		} finally {
			fos.close();
		}
	}


	/**
	 * validates the MD5 checksum of the given file
	 * @param target - file path
	 * @param md5 - expected MD5 hash
	 */
	protected void checkFile(File target, String md5)throws Exception{
		assertTrue(target.exists());
		String actual = Utils.md5(target);
		assertEquals(md5, actual, "File <"+target+">: contents "+target.getName()+" do not match");
	}

	public static class MockProgressListener implements UFTPProgressListener{

		public static boolean gotNotify=false;
		
		public void notifyTotalBytesTransferred(long totalBytesSent) {
			gotNotify=true;
		}
	}
}