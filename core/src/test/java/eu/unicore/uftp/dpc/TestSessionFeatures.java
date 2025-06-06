package eu.unicore.uftp.dpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.client.UFTPSessionClient.HashInfo;
import eu.unicore.uftp.dpc.Session.Mode;
import eu.unicore.uftp.dpc.Utils.EncryptionAlgorithm;
import eu.unicore.uftp.rsync.RsyncStats;
import eu.unicore.uftp.rsync.TestRsync;
import eu.unicore.uftp.server.ClientServerTestBase;
import eu.unicore.uftp.server.requests.UFTPSessionRequest;

public class TestSessionFeatures extends ClientServerTestBase{

	@Test
	public void testSync() throws Exception {
		String masterName="source-"+System.currentTimeMillis();
		File masterFile=new File(dataDir,masterName);
		String slaveName="copy-"+System.currentTimeMillis();
		File slaveFile=new File(dataDir,slaveName);
		TestRsync.writeTextFiles(masterFile, slaveFile, 500, 3);
		assertNotSame(Utils.md5(masterFile),Utils.md5(slaveFile));
		String secret = UUID.randomUUID().toString();
		String cwd = dataDir.getAbsolutePath();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", secret, cwd);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);
		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();
			client.syncLocalFile(masterName, slaveFile);
			assertEquals(Utils.md5(masterFile),Utils.md5(slaveFile));		
			TestRsync.writeTestFiles(masterFile, slaveFile, 50*1024*1024,1, 1);
			RsyncStats stats=client.syncRemoteFile(masterFile, slaveName);
			System.out.println(stats);
			Thread.sleep(2000);
			assertEquals(Utils.md5(masterFile),Utils.md5(slaveFile));
		}
	}

	@Test
	public void testACLSetup() throws Exception {
		System.setProperty("uftp-unit-test", "true");
		String secret = String.valueOf(System.currentTimeMillis());
		Properties properties = new Properties();
		properties.setProperty("client-ip",Utils.encodeInetAddresses(host));
		properties.setProperty("send","true");
		properties.setProperty("file", ".");
		properties.setProperty("streams", "1");
		properties.setProperty("secret",secret);
		properties.setProperty("user","nobody");
		properties.setProperty("group","nobody");
		properties.setProperty("includes","/foo:/bar:*ok*");
		properties.setProperty("excludes","*forbidden*");
		UFTPSessionRequest job = new UFTPSessionRequest(properties);
		Session s = new Session(null,job,Utils.getFileAccess(null),1);
		assertEquals(3,s.getIncludes().length);
		assertFalse(s.checkACL(new File("/ham"), Mode.READ));
		assertTrue(s.checkACL(new File("/foo"),Mode.READ));
		assertTrue(s.checkACL(new File("/bar"),Mode.READ));
		assertTrue(s.checkACL(new File("thisisok"),Mode.READ));
		assertFalse(s.checkACL(new File("/this/is/a/forbidden/directory/"),Mode.READ));
		assertFalse(s.checkACL(new File("/this/foo/is/forbidden/"),Mode.READ));
	}

	@Test
	public void testACLOperation() throws Exception {
		File cwd=dataDir.getAbsoluteFile();
		String secret = UUID.randomUUID().toString();
		Properties properties = new Properties();
		properties.setProperty("client-ip",Utils.encodeInetAddresses(host));
		properties.setProperty("send","true");
		properties.setProperty("file",cwd.getAbsolutePath());
		properties.setProperty("streams", "1");
		properties.setProperty("secret",secret);
		properties.setProperty("user","nobody");
		properties.setProperty("group","nobody");
		properties.setProperty("includes","*ok*");
		properties.setProperty("excludes","*forbidden*:*not*ok*");
		UFTPSessionRequest job = new UFTPSessionRequest(properties);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();
			assertThrows(IOException.class, ()->{
				client.mkdir("forbidden");

			});
			client.mkdir("ok");
			client.cd("ok");
			ByteArrayInputStream is=new ByteArrayInputStream("testdata".getBytes());
			assertThrows(IOException.class, ()->{
				client.put("not_ok", is.available(), is);
				fail("ACL does not work");
			});
		}
	}

	@Test
	public void testRename() throws Exception {
		File originalFile = new File("target/testdata/source-"+System.currentTimeMillis());
		String originalName = originalFile.getName();		
		Utils.writeToFile("this is a test for the session client", originalFile);
		String secret = UUID.randomUUID().toString();
		String cwd = dataDir.getAbsolutePath();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", secret, cwd);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();
			String newName = "source-"+System.currentTimeMillis();
			client.rename(originalName, newName);
			File newFile = new File("target/testdata/"+newName);
			assertTrue(newFile.exists());
			client.rm(newName);
			assertFalse(newFile.exists());
		}
	}

	@Test
	public void testRename2() throws Exception {
		String originalName = "testdata/source-"+System.currentTimeMillis();
		File originalFile = new File("target/"+originalName);
		Utils.writeToFile("this is a test for the session client", originalFile);
		String secret = UUID.randomUUID().toString();
		String cwd = dataDir.getParentFile().getAbsolutePath();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", secret, cwd);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();

			new File("target/testdata2").mkdirs();
			String newName = "testdata2/source-"+System.currentTimeMillis();
			client.rename(originalName, newName);
			File newFile = new File("target/"+newName);
			assertTrue(newFile.length()>0);
		}
	}

	@Test
	public void testSetModificationTime() throws Exception {
		String originalName = "source-"+System.currentTimeMillis();
		File originalFile = new File("target/testdata/"+originalName);
		Utils.writeToFile("this is a test for the session client", originalFile);
		String secret = UUID.randomUUID().toString();
		String cwd = dataDir.getAbsolutePath();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", secret, cwd);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();
			Calendar time = Calendar.getInstance();
			String setTo = new SimpleDateFormat("yyyyMMddhhmmss").format(time.getTime());
			client.setModificationTime(originalName, time);
			String now = new SimpleDateFormat("yyyyMMddhhmmss").format(new Date(originalFile.lastModified()));
			assertEquals(setTo,now);
		}
	}

	@Test
	public void testClientConnectEmptyIP() throws Exception {
		String realSourceName="target/testdata/source-"+System.currentTimeMillis();
		File realSource=new File(realSourceName);
		Utils.writeToFile("this is a test for the session client", realSource);
		String secret = UUID.randomUUID().toString();
		String cwd = dataDir.getAbsolutePath();
		UFTPSessionRequest job = new UFTPSessionRequest(new InetAddress[0], "nobody", secret, cwd);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);
		InetAddress[]server=host;

		try(UFTPSessionClient client = new UFTPSessionClient(server, srvPort)){
			client.setSecret(secret);
			client.connect();
			System.out.println("Files:\n"+client.getFileInfoList("."));
		}
	}


	@Test
	public void testUnpackArchiveStreams() throws Exception {
		String[] types = { "tar", "zip", };
		for(String type: types) {
			unpackArchiveStream(type);
		}
	}

	private void unpackArchiveStream(String type) throws Exception {
		String realSourceName="src/test/resources/archive."+type;
		File realSource=new File(realSourceName);
		String secret = UUID.randomUUID().toString();
		String cwd = dataDir.getAbsolutePath();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", secret, cwd);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();

			String newDir="test-"+type+System.currentTimeMillis();
			client.mkdir(newDir);
			List<String>ls=client.getFileList(".");
			assertTrue(ls.toString().contains(newDir));
			client.cd(newDir);
			client.setType(UFTPSessionClient.TYPE_ARCHIVE);
			try(FileInputStream fis=new FileInputStream(realSource)){
				client.put(".", realSource.length(), fis);
			}
			client.closeData();
			long rSize=client.getFileSize("file1");
			assertEquals(7,rSize);
			rSize=client.getFileSize("subdirectory/subfile1");
			assertEquals(7,rSize);
		}
	}

	@Test
	public void testRestrictSession() throws Exception {
		String secret = UUID.randomUUID().toString();
		String cwd = dataDir.getAbsolutePath();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", secret, cwd);
		job.setAccessPermissions(Session.Mode.NONE);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		IOException i = assertThrows(IOException.class,()->{
			try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort)){
				client.setSecret(secret);
				client.connect();
				client.stat(".");
			}
		});
		assertTrue(i.getMessage().toLowerCase().contains("access denied"));
		job = new UFTPSessionRequest(host, "nobody", secret, cwd);
		job.setAccessPermissions(Session.Mode.INFO);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();
			System.out.println(client.stat("."));
		}
	}

	@Test
	public void testAESEncryption() throws Exception {
		int numCon = 1;
		byte[] key = Utils.createKey(EncryptionAlgorithm.AES);
		boolean compress = true;
		File sourceFile = new File(dataDir,"testsourcefile-"+System.currentTimeMillis());
		makeTestFile2(sourceFile, 1024*500);
		String target = "target/testdata/testfile-" + System.currentTimeMillis();
		String secret = UUID.randomUUID().toString();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", secret,
				sourceFile.getParentFile().getAbsolutePath());
		job.setKey(key);
		job.setEncryptionAlgorithm(EncryptionAlgorithm.AES);
		job.setCompress(compress);
		job.setStreams(numCon);
		job.sendTo(host[0], jobPort);

		Thread.sleep(1000);
		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort);
				FileOutputStream fos = new FileOutputStream(target)){
			client.setSecret(secret);
			client.setNumConnections(numCon);
			client.setKey(key);
			client.setEncryptionAlgorithm(EncryptionAlgorithm.AES);
			client.setCompress(compress);
			client.connect();
			client.get(sourceFile.getName(),fos);
		}
		System.out.println("Finished client.");
		File targetFile = new File(target);
		String expected = Utils.md5(sourceFile);
		checkFile(targetFile, expected);
	}

	@Test
	public void testHashing() throws Exception {
		String fileName = "source-"+System.currentTimeMillis();
		File dataFile = new File("target/testdata/"+fileName);
		makeTestFile(dataFile, 32768, 10);
		String secret = UUID.randomUUID().toString();
		String cwd = dataDir.getAbsolutePath();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", secret, cwd);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();
			assertTrue(client.supportsHashes());
			System.out.println("Hash algorithm: " + client.getHashAlgorithm());
			IOException ok = assertThrows(IOException.class, ()->{
				client.setHashAlgorithm("no_such_algo");
			});
			assertTrue(ok.getMessage().contains("not supported"));
			String[] algos = new String[] {"MD5", "SHA-1", "SHA-256", "SHA-512"};
			for(String algo: algos) {
				System.out.println("Hash algorithm set to: " + client.setHashAlgorithm(algo));
				HashInfo hashInfo = client.getHash(fileName, 0, dataFile.length());
				System.out.println("Remote hash:   "+hashInfo.fullInfo());
				HashInfo hashInfo2 = client.getHash(fileName);
				System.out.println("Remote hash 2: "+hashInfo2.fullInfo());
				String localMD = Utils.hexString(Utils.digest(dataFile, algo));
				String remoteMD = hashInfo.toString();
				System.out.println(algo+" local: "+localMD+" remote: "+remoteMD);
				assertEquals(localMD, remoteMD);
				assertEquals(hashInfo2.toString(), remoteMD);
			}
		}
	}

	@Test
	public void testRCP() throws Exception {
		File originalFile = new File("target/testdata/source-"+System.currentTimeMillis());
		String originalName = originalFile.getName();		
		Utils.writeToFile("this is a test for the session client", originalFile);
		String secret1 = UUID.randomUUID().toString();
		String cwd = dataDir.getAbsolutePath();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", secret1, cwd);
		job.sendTo(host[0], jobPort);
		String secret2 = UUID.randomUUID().toString();
		UFTPSessionRequest job2 = new UFTPSessionRequest(host, "nobody", secret2, cwd);
		job2.sendTo(host[0], jobPort);
		String secret3 = UUID.randomUUID().toString();
		UFTPSessionRequest job3 = new UFTPSessionRequest(host, "nobody", secret3, cwd);
		job3.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret1);
			client.connect();
			String newName = originalName+"-copy";
			String rcpHost = String.format("%s:%s", host[0].getHostName(), srvPort);
			client.sendFile(originalName, newName, rcpHost, secret2);
			Thread.sleep(3000);
			File newFile = new File("target/testdata/"+newName);
			assertTrue(newFile.exists());
			client.rm(newName);
			assertFalse(newFile.exists());
			client.receiveFile(newName, originalName, rcpHost, secret3);
			Thread.sleep(3000);
			assertTrue(newFile.exists());
		}
	}

	@Test
	public void testList() throws Exception {
		String secret = UUID.randomUUID().toString();
		String cwd = dataDir.getAbsolutePath();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", secret, cwd);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			client.mlsd(".", os);
			os = new ByteArrayOutputStream();
			client.list(".", os);
		}
	}

	private void makeTestFile2(File file, int lines) throws IOException {
		try(FileOutputStream fos = new FileOutputStream(file)) {
			for (int i = 0; i < lines; i++) {
				fos.write( (i+": test1 test2 test3\n").getBytes());
			}
		}
	}

}
