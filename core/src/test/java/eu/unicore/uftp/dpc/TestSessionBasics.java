package eu.unicore.uftp.dpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import eu.unicore.uftp.client.FileInfo;
import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.dpc.Utils.EncryptionAlgorithm;
import eu.unicore.uftp.jparss.PConfig;
import eu.unicore.uftp.server.ClientServerTestBase;
import eu.unicore.uftp.server.UFTPCommands;
import eu.unicore.uftp.server.requests.UFTPSessionRequest;

public class TestSessionBasics extends ClientServerTestBase{

	@Test
	public void testClientRead() throws Exception {
		_clientRead(1, false);
	}

	@Test
	public void testClientReadCompress() throws Exception {
		_clientRead(1, true);
	}

	@Test
	public void testClientReadParallel() throws Exception {
		_clientRead(2, false);
	}

	@Test
	public void testClientReadStreamLimit() throws Exception {
		_clientRead(8, false);
	}

	@Test
	public void testClientReadParallelNoThreads() throws Exception {
		PConfig.usethreads = false;
		_clientRead(2, false);
		PConfig.usethreads = true;
	}

	private void _clientRead(int streams, boolean compress) throws Exception {
		String fID = UUID.randomUUID().toString();
		String realSourceName="target/testdata/source-"+fID;
		File realSource=new File(realSourceName);
		Utils.writeToFile("this is a test for the session client", realSource);
		String target = "target/testdata/testfile-"+fID;
		String secret = String.valueOf(System.currentTimeMillis());
		String cwd = new File(".").getAbsolutePath();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", secret, cwd);
		job.setCompress(compress);
		job.setStreams(streams);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);
		InetAddress[]server=host;
		try(UFTPSessionClient client = new UFTPSessionClient(server, srvPort)){
			client.setSecret(secret);
			client.setCompress(compress);
			client.setNumConnections(streams);
			client.connect();
			try(FileOutputStream fos=new FileOutputStream(target)){
				System.out.println("Files:\n"+client.getFileInfoList("."));
				client.cd("src");
				System.out.println("Files in 'src':\n"+client.getFileInfoList("."));
				client.cdUp();
				//check size
				long size=client.getFileSize(realSourceName);
				assertEquals(realSource.length(),size);
				client.get(realSourceName, fos);
			}
			// check that file exists and has correct content
			String expected = Utils.md5(realSource);
			checkFile(new File(target), expected);
			try(FileOutputStream fos=new FileOutputStream(target)){
				// and read it again
				client.get(realSourceName, fos);
			}
			// stat single file and directory
			FileInfo fileInfo = client.stat(target);
			assertFalse(fileInfo.getUnixPermissions("-").contains("x"));
			assertFalse(fileInfo.isExecutable());
			assertTrue(fileInfo.isReadable());
			assertTrue(fileInfo.isWritable());
			String mlstEntry = fileInfo.toMListEntry();
			assertTrue(mlstEntry.contains(";perm=rw"));

			FileInfo dirInfo = client.stat(".");
			System.out.println(dirInfo);
			assertTrue(dirInfo.isDirectory());
			checkFile(new File(target), expected);
		}
	}

	@Test
	public void testClientWrite() throws Exception {
		_clientWrite(1, false);
	}

	@Test
	public void testClientWriteCompress() throws Exception {
		_clientWrite(1, true);
	}

	@Test
	public void testClientWriteParallel() throws Exception {
		_clientWrite(2, false);
	}

	@Test
	public void testClientWriteParallelNoThreads() throws Exception {
		PConfig.usethreads = false;
		_clientWrite(2, false);
		PConfig.usethreads = true;
		System.setProperty(PConfig.USE_THREADS, "true");
	}
	
	private void _clientWrite(int streams, boolean compress) throws Exception {
		String realSourceName="target/testdata/source-"+System.currentTimeMillis();
		File realSource=new File(realSourceName);
		Utils.writeToFile("this is a test for the session client", realSource);
		String secret = String.valueOf(System.currentTimeMillis());
		String cwd=dataDir.getAbsolutePath();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", secret, cwd);
		job.setCompress(compress);
		job.setStreams(streams);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.setCompress(compress);
			client.setNumConnections(streams);
			client.connect();
			String newDir="test-"+System.currentTimeMillis();
			client.mkdir(newDir);
			List<FileInfo>ls=client.getFileInfoList(".");
			System.out.println("Files:\n"+ls);
			assertTrue(ls.toString().contains(newDir));
			client.cd(newDir);

			String pwd=client.pwd();
			assertTrue(pwd.contains(newDir));
			try(FileInputStream fis=new FileInputStream(realSource)){
				client.put("test", realSource.length(), fis);
			}
			long rSize=client.getFileSize("test");
			assertEquals(realSource.length(),rSize);
		}
	}

	@Test
	public void testClientWriteMultipleParts() throws Exception {
		String realSourceName="target/testdata/sourcefile";
		File realSource=new File(realSourceName);
		FileUtils.deleteQuietly(realSource);
		String testString = "this is a test for the session client";
		Utils.writeToFile(testString, realSource);
		String secret = String.valueOf(System.currentTimeMillis());
		String cwd=dataDir.getAbsolutePath();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", secret, cwd);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();

			String newDir="test-"+System.currentTimeMillis();
			client.mkdir(newDir);
			client.cd(newDir);

			int offset = 20;
			// write the first 20 bytes
			try(FileInputStream fis=new FileInputStream(realSource)){
				client.put("test", offset, fis);
			}
			// write second part of test file
			try(FileInputStream fis=new FileInputStream(realSource)){
				fis.skip(offset);
				client.put("test", realSource.length()-offset, Long.valueOf(offset), fis);
			}
			long rSize=client.getFileSize("test");
			assertEquals(realSource.length(),rSize);
			String md5Orig = Utils.md5(realSource);
			String md5Remote = Utils.md5(new File(cwd, newDir+"/test"));
			assertEquals(md5Orig, md5Remote, "File content mismatch");
		}
	}

	@Test
	public void testClientAppendMultipleParts() throws Exception {
		String realSourceName="target/testdata/sourcefile";
		File realSource=new File(realSourceName);
		FileUtils.deleteQuietly(realSource);
		String testString = "this is a test for the session client";
		Utils.writeToFile(testString, realSource);
		String secret = String.valueOf(System.currentTimeMillis());
		String cwd = dataDir.getAbsolutePath();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", secret, cwd);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();
			System.out.println(client.getServerFeatures());

			String newDir="test-"+System.currentTimeMillis();
			client.mkdir(newDir);
			client.cd(newDir);

			int offset = 20;

			// write the first 20 bytes
			try(FileInputStream fis=new FileInputStream(realSource)){
				client.put("test", offset, fis);
			}
			// write second part of test file
			try(FileInputStream fis=new FileInputStream(realSource)){
				fis.skip(offset);
				client.append("test", realSource.length()-offset, fis);
			}
			long rSize=client.getFileSize("test");
			assertEquals(realSource.length(),rSize);
			String md5Orig = Utils.md5(realSource);
			String md5Remote = Utils.md5(new File(cwd, newDir+"/test"));
			assertEquals(md5Orig, md5Remote, "File content mismatch");
		}
	}

	@Test
	public void testClientWriteStreamMode() throws Exception {
		String realSourceName="target/testdata/source-"+System.currentTimeMillis();
		File realSource=new File(realSourceName);
		Utils.writeToFile("this is a test for the session client", realSource);
		String secret = String.valueOf(System.currentTimeMillis());
		String cwd = dataDir.getAbsolutePath();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", secret, cwd);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();
			String newDir="test-"+System.currentTimeMillis();
			client.mkdir(newDir);
			List<String>ls=client.getFileList(".");
			System.out.println("Files:\n"+ls);
			assertTrue(ls.toString().contains(newDir));
			client.cd(newDir);

			String pwd=client.pwd();
			assertTrue(pwd.contains(newDir));
			try(FileInputStream fis=new FileInputStream(realSource)){
				client.writeAll("test", fis, true);
			}
			long rSize=client.getFileSize("test");
			assertEquals(realSource.length(),rSize);
		}
	}

	@Test
	public void testMultiStreamEncrypted() throws Exception {
		int numCon = 2;
		byte[] key = Utils.createKey(EncryptionAlgorithm.BLOWFISH);
		boolean compress = true;
		File sourceFile = new File(dataDir,"testsourcefile-"+System.currentTimeMillis());
		makeTestFile2(sourceFile, 1024*500);
		String target = "target/testdata/testfile-" + System.currentTimeMillis();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", "secretCode",
				sourceFile.getParentFile().getAbsolutePath());
		job.setKey(key);
		job.setCompress(compress);
		job.setStreams(numCon);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort); 
				FileOutputStream fos = new FileOutputStream(target)){
			client.setSecret("secretCode");
			client.setNumConnections(numCon);
			client.setKey(key);
			client.setCompress(compress);
			client.connect();
			client.get(sourceFile.getName(),fos);
		}
		System.out.println("Finished client.");
		File targetFile = new File(target);
		String expected = Utils.md5(sourceFile);
		checkFile(targetFile, expected);
	}

	private void makeTestFile2(File file, int lines) throws IOException {
		try(FileOutputStream fos = new FileOutputStream(file)) {
			for (int i = 0; i < lines; i++) {
				fos.write( (i+": test1 test2 test3\n").getBytes());
			}
		}
	}

	@Test
	public void testPASV() throws Exception {
		String secret = UUID.randomUUID().toString();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", secret, ".");
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);
		try(UFTPSessionClient client=new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();
			client.getServerFeatures().remove(UFTPCommands.EPSV);
			client.closeData();
			client.openDataConnection();
			client.closeData();
			client.setNumConnections(2);
			client.openDataConnection();
		}
	}
}
