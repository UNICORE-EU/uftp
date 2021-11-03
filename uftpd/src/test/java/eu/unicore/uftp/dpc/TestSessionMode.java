package eu.unicore.uftp.dpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import eu.unicore.uftp.client.FileInfo;
import eu.unicore.uftp.client.SessionCommands.Get;
import eu.unicore.uftp.client.UFTPSessionClient.HashInfo;
import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.dpc.Session.Mode;
import eu.unicore.uftp.rsync.RsyncStats;
import eu.unicore.uftp.rsync.TestRsync;
import eu.unicore.uftp.server.ClientServerTestBase;
import eu.unicore.uftp.server.requests.UFTPTransferRequest;
import eu.unicore.uftp.server.workers.UFTPWorker;
import eu.unicore.util.Log;

public class TestSessionMode extends ClientServerTestBase{

	@Test
	public void testClientRead() throws Exception {
		String realSourceName="target/testdata/source-"+System.currentTimeMillis();
		File realSource=new File(realSourceName);
		Utils.writeToFile("this is a test for the session client", realSource);

		String target = "target/testdata/testfile-" + System.currentTimeMillis();
		String secret = String.valueOf(System.currentTimeMillis());
		File cwd=new File(".").getAbsoluteFile();
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret, new File(cwd,UFTPWorker.sessionModeTag), true);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);
		InetAddress[]server=host;

		UFTPSessionClient client = new UFTPSessionClient(server, srvPort);
		client.setSecret(secret);

		client.connect();
		FileOutputStream fos=new FileOutputStream(target);

		System.out.println("Files:\n"+client.getFileInfoList("."));
		client.cd("src");
		System.out.println("Files in 'src':\n"+client.getFileInfoList("."));
		client.cdUp();

		//check size
		long size=client.getFileSize(realSourceName);
		assertEquals(realSource.length(),size);

		client.get(realSourceName, fos);
		fos.close();

		// check that file exists and has correct content
		String expected = Utils.md5(realSource);
		checkFile(new File(target), expected);

		// and read it again
		fos=new FileOutputStream(target);
		client.get(realSourceName, fos);
		fos.close();

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

		client.close();
		// check that file exists and has correct content
		checkFile(new File(target), expected);

	}


	@Test
	public void testClientReadAbsolutePaths() throws Exception {
		String realSourceName="target/testdata/source-"+System.currentTimeMillis();
		File realSource=new File(realSourceName);
		Utils.writeToFile("this is a test for the session client", realSource);

		String target = "target/testdata/testfile-" + System.currentTimeMillis();
		String secret = String.valueOf(System.currentTimeMillis());
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret, new File(UFTPWorker.sessionModeTag), true);
		
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		UFTPSessionClient client = new UFTPSessionClient(host, srvPort);
		client.setSecret(secret);

		client.connect();
		FileOutputStream fos=new FileOutputStream(target);

		client.get(realSource.getAbsolutePath(), fos);

		// check that file exists and has correct content
		String expected = Utils.md5(realSource);
		checkFile(new File(target), expected);

		//delete it
		client.rm(realSource.getAbsolutePath());
		assertTrue("File was not deleted", !realSource.exists());

		client.close();

	}

	@Test
	public void testClientReadCompress() throws Exception {
		String realSourceName="target/testdata/source-"+System.currentTimeMillis();
		File realSource=new File(realSourceName);
		Utils.writeToFile("this is a test for the session client", realSource);

		String target = "target/testdata/testfile-" + System.currentTimeMillis();
		String secret = String.valueOf(System.currentTimeMillis());
		File cwd=new File(".").getAbsoluteFile();
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret, new File(cwd,UFTPWorker.sessionModeTag), true);
		job.setCompress(true);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);
		
		UFTPSessionClient client = new UFTPSessionClient(host, srvPort);
		client.setSecret(secret);
		client.setCompress(true);

		client.connect();
		FileOutputStream fos=new FileOutputStream(target);

		System.out.println("Files:\n"+client.getFileInfoList("."));
		client.cd("src");
		System.out.println("Files in 'src':\n"+client.getFileInfoList("."));
		client.cdUp();

		//check size
		long size=client.getFileSize(realSourceName);
		assertEquals(realSource.length(),size);

		client.get(realSourceName, fos);
		fos.close();

		// check that file exists and has correct content
		String expected = Utils.md5(realSource);
		checkFile(new File(target), expected);

		// and read it again
		fos=new FileOutputStream(target);
		client.get(realSourceName, fos);
		fos.close();

		client.close();
		// check that file exists and has correct content
		checkFile(new File(target), expected);

	}

	@Test
	public void testClientWrite() throws Exception {
		String realSourceName="target/testdata/source-"+System.currentTimeMillis();
		File realSource=new File(realSourceName);
		Utils.writeToFile("this is a test for the session client", realSource);

		String secret = String.valueOf(System.currentTimeMillis());
		File cwd=dataDir.getAbsoluteFile();

		//initiate session mode
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret, new File(cwd,UFTPWorker.sessionModeTag), false);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		UFTPSessionClient client = new UFTPSessionClient(host, srvPort);
		client.setSecret(secret);

		client.connect();

		String newDir="test-"+System.currentTimeMillis();
		client.mkdir(newDir);
		List<FileInfo>ls=client.getFileInfoList(".");
		System.out.println("Files:\n"+ls);
		assertTrue(ls.toString().contains(newDir));
		client.cd(newDir);

		String pwd=client.pwd();
		assertTrue(pwd.contains(newDir));
		FileInputStream fis=new FileInputStream(realSource);
		client.put("test", realSource.length(), fis);
		fis.close();
		long rSize=client.getFileSize("test");
		assertEquals(realSource.length(),rSize);
		client.close();
	}

	@Test
	public void testClientWriteMultipleParts() throws Exception {
		String realSourceName="target/testdata/sourcefile";
		File realSource=new File(realSourceName);
		FileUtils.deleteQuietly(realSource);

		String testString = "this is a test for the session client";
		Utils.writeToFile(testString, realSource);

		String secret = String.valueOf(System.currentTimeMillis());
		File cwd=dataDir.getAbsoluteFile();

		//initiate session mode
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret, new File(cwd,UFTPWorker.sessionModeTag), true);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		UFTPSessionClient client = new UFTPSessionClient(host, srvPort);
		client.setSecret(secret);
		client.connect();

		String newDir="test-"+System.currentTimeMillis();
		client.mkdir(newDir);
		client.cd(newDir);

		int offset = 20;
		
		// write the first 20 bytes
		FileInputStream fis=new FileInputStream(realSource);
		client.put("test", offset, fis);
		fis.close();

		// write second part of test file
		fis=new FileInputStream(realSource);
		fis.skip(offset);
		client.put("test", realSource.length()-offset, Long.valueOf(offset), fis);
		fis.close();


		// checks
		long rSize=client.getFileSize("test");
		assertEquals(realSource.length(),rSize);

		String md5Orig = Utils.md5(realSource);
		String md5Remote = Utils.md5(new File(cwd, newDir+"/test"));

		assertEquals("File content mismatch",md5Orig, md5Remote);

		client.close();
	}

	@Test
	public void testClientWriteCompress() throws Exception {
		String realSourceName="target/testdata/source-"+System.currentTimeMillis();
		File realSource=new File(realSourceName);
		Utils.writeToFile("this is a test for the session client", realSource);

		String secret = String.valueOf(System.currentTimeMillis());
		File cwd=dataDir.getAbsoluteFile();

		//initiate session mode
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret, new File(cwd,UFTPWorker.sessionModeTag), true);
		job.setCompress(true);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		UFTPSessionClient client = new UFTPSessionClient(host, srvPort);
		client.setSecret(secret);
		client.setCompress(true);

		client.connect();

		String newDir="test-"+System.currentTimeMillis();
		client.mkdir(newDir);
		List<String>ls=client.getFileList(".");
		System.out.println("Files:\n"+ls);
		assertTrue(ls.toString().contains(newDir));
		client.cd(newDir);

		String pwd=client.pwd();
		assertTrue(pwd.contains(newDir));
		FileInputStream fis=new FileInputStream(realSource);
		client.put("test", realSource.length(), fis);
		fis.close();
		long rSize=client.getFileSize("test");
		assertEquals(realSource.length(),rSize);
		client.close();
	}
	
	@Test
	public void testClientAppendMultipleParts() throws Exception {
		String realSourceName="target/testdata/sourcefile";
		File realSource=new File(realSourceName);
		FileUtils.deleteQuietly(realSource);

		String testString = "this is a test for the session client";
		Utils.writeToFile(testString, realSource);

		String secret = String.valueOf(System.currentTimeMillis());
		File cwd=dataDir.getAbsoluteFile();

		//initiate session mode
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret, new File(cwd,UFTPWorker.sessionModeTag), true);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		UFTPSessionClient client = new UFTPSessionClient(host, srvPort);
		client.setSecret(secret);
		client.connect();
		System.out.println(client.getServerFeatures());
		
		String newDir="test-"+System.currentTimeMillis();
		client.mkdir(newDir);
		client.cd(newDir);

		int offset = 20;
		
		// write the first 20 bytes
		FileInputStream fis=new FileInputStream(realSource);
		client.put("test", offset, fis);
		fis.close();

		// write second part of test file
		fis=new FileInputStream(realSource);
		fis.skip(offset);
		client.append("test", realSource.length()-offset, fis);
		fis.close();

		// checks
		long rSize=client.getFileSize("test");
		assertEquals(realSource.length(),rSize);

		String md5Orig = Utils.md5(realSource);
		String md5Remote = Utils.md5(new File(cwd, newDir+"/test"));

		assertEquals("File content mismatch",md5Orig, md5Remote);

		client.close();
	}

	@Test
	public void testSync() throws Exception {
		String masterName="source-"+System.currentTimeMillis();
		File masterFile=new File(dataDir,masterName);
		String slaveName="copy-"+System.currentTimeMillis();
		File slaveFile=new File(dataDir,slaveName);
		TestRsync.writeTextFiles(masterFile, slaveFile, 500, 3);
		assertNotSame(Utils.md5(masterFile),Utils.md5(slaveFile));
		String secret = String.valueOf(System.currentTimeMillis());
		File cwd=dataDir.getAbsoluteFile();
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret, new File(cwd,UFTPWorker.sessionModeTag), false);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);
		UFTPSessionClient client = new UFTPSessionClient(host, srvPort);
		client.setSecret(secret);
		client.connect();

		client.syncLocalFile(masterName, slaveFile);
		assertEquals(Utils.md5(masterFile),Utils.md5(slaveFile));		

		TestRsync.writeTestFiles(masterFile, slaveFile, 50*1024*1024,1, 1);
		RsyncStats stats=client.syncRemoteFile(masterFile, slaveName);
		System.out.println(stats);
		Thread.sleep(2000);
		assertEquals(Utils.md5(masterFile),Utils.md5(slaveFile));
		client.close();
	}

	@Test
	public void testClientGetCmd() throws Exception {
		String realSourceName="target/testdata/source-"+System.currentTimeMillis();
		File realSource=new File(realSourceName);
		Utils.writeToFile("this is a test for the session client", realSource);

		String target = "target/testdata/"+realSource.getName();
		String secret = String.valueOf(System.currentTimeMillis());
		File cwd=new File(".").getAbsoluteFile();
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret, new File(cwd,UFTPWorker.sessionModeTag), true);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		UFTPSessionClient client = new UFTPSessionClient(host, srvPort);
		client.setSecret(secret);
		client.connect();
		client.setBaseDirectory(new File("target/testdata"));
		List<String>args=new ArrayList<String>();
		args.add(realSourceName);
		args.add(realSource.getName());
		Get get=new Get(args);
		get.setClient(client);
		get.run();
		//check size
		long size=client.getFileSize(realSourceName);
		assertEquals(realSource.length(),size);

		client.close();

		// check that file exists and has correct content
		String expected = Utils.md5(realSource);
		checkFile(new File(target), expected);
	}

	@Test
	public void testSessonCMDFile() throws Exception {
		File commandFile=new File("src/test/resources/session_cmd_file");
		File source=commandFile;
		File target=new File("target/foo_1");
		File target2=new File("target/foo_2");
		FileUtils.deleteQuietly(target);
		FileUtils.deleteQuietly(target2);
		String secret = String.valueOf(System.currentTimeMillis());
		File cwd=new File(".").getAbsoluteFile();
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret, new File(cwd,UFTPWorker.sessionModeTag), true);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		UFTPSessionClient client = new UFTPSessionClient(host, srvPort);
		client.setSecret(secret);
		client.setBaseDirectory(cwd);
		client.setCommandFile(commandFile.getAbsolutePath());
		client.run();
		// check that file exists and has correct content
		String expected = Utils.md5(source);
		checkFile(target, expected);
		checkFile(target2, expected);
		client.close();
	}


	@Test
	public void testClientWriteStreamMode() throws Exception {
		String realSourceName="target/testdata/source-"+System.currentTimeMillis();
		File realSource=new File(realSourceName);
		Utils.writeToFile("this is a test for the session client", realSource);
		String secret = String.valueOf(System.currentTimeMillis());
		File cwd=dataDir.getAbsoluteFile();
		//initiate session mode
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret, new File(cwd,UFTPWorker.sessionModeTag), false);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);
		UFTPSessionClient client = new UFTPSessionClient(host, srvPort);
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
		FileInputStream fis=new FileInputStream(realSource);
		client.writeAll("test", fis, true);
		fis.close();
		long rSize=client.getFileSize("test");
		assertEquals(realSource.length(),rSize);
		client.close();
	}


	@Test
	public void testACLSetup() throws Exception {
		System.setProperty("uftp-unit-test", "true");
		String secret = String.valueOf(System.currentTimeMillis());
		Properties properties = new Properties();
		properties.setProperty("client-ip",Utils.encodeInetAddresses(host));
		properties.setProperty("send","true");
		properties.setProperty("file",UFTPWorker.sessionModeTag);
		properties.setProperty("streams", "1");
		properties.setProperty("secret",secret);
		properties.setProperty("user","nobody");
		properties.setProperty("group","nobody");
		properties.setProperty("includes","/foo:/bar:*ok*");
		properties.setProperty("excludes","*forbidden*");
		UFTPTransferRequest job = new UFTPTransferRequest(properties);
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
		String secret = String.valueOf(System.currentTimeMillis());
		Properties properties = new Properties();
		properties.setProperty("client-ip",Utils.encodeInetAddresses(host));
		properties.setProperty("send","true");
		properties.setProperty("file",new File(cwd,UFTPWorker.sessionModeTag).getAbsolutePath());
		properties.setProperty("streams", "1");
		properties.setProperty("secret",secret);
		properties.setProperty("user","nobody");
		properties.setProperty("group","nobody");
		properties.setProperty("includes","*ok*");
		properties.setProperty("excludes","*forbidden*:*not*ok*");
		UFTPTransferRequest job = new UFTPTransferRequest(properties);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);
		UFTPSessionClient client = new UFTPSessionClient(host, srvPort);
		client.setSecret(secret);
		client.connect();
		try{
			client.mkdir("forbidden");
			fail("ACL does not work");
		}catch(IOException e){
			assertTrue(Utils.getDetailMessage(e).contains("Access denied"));
		}
		client.mkdir("ok");
		client.cd("ok");
		ByteArrayInputStream is=new ByteArrayInputStream("testdata".getBytes());
		try{
			client.put("not_ok", is.available(), is);
			fail("ACL does not work");
		}catch(IOException e){
			assertTrue(Utils.getDetailMessage(e).contains("Access denied"));
		}
		client.close();
	}

	@Test
	public void testRename() throws Exception {
		String originalName = "target/testdata/source-"+System.currentTimeMillis();
		File originalFile = new File(originalName);
		Utils.writeToFile("this is a test for the session client", originalFile);

		String secret = String.valueOf(System.currentTimeMillis());
		File cwd = new File(".").getAbsoluteFile();

		//initiate session mode
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret, new File(cwd,UFTPWorker.sessionModeTag), false);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		UFTPSessionClient client = new UFTPSessionClient(host, srvPort);
		client.setSecret(secret);
		client.connect();

		String newName = "target/testdata/source-"+System.currentTimeMillis();
		client.rename(originalName, newName);
		File newFile = new File(newName);
		assertTrue(newFile.exists());

		client.close();
	}

	@Test
	public void testRename2() throws Exception {
		String originalName = "target/testdata/source-"+System.currentTimeMillis();
		File originalFile = new File(originalName);
		Utils.writeToFile("this is a test for the session client", originalFile);

		String secret = String.valueOf(System.currentTimeMillis());
		File cwd = new File(".").getAbsoluteFile();

		//initiate session mode
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret, new File(cwd,UFTPWorker.sessionModeTag), false);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		UFTPSessionClient client = new UFTPSessionClient(host, srvPort);
		client.setSecret(secret);
		client.connect();

		new File("target/testdata2").mkdirs();
		String newName = "target/testdata2/source-"+System.currentTimeMillis();
		client.rename(originalName, newName);
		File newFile = new File(newName);
		assertTrue(newFile.length()>0);

		client.close();
	}

	@Test
	public void testSetModificationTime() throws Exception {
		String originalName = "target/testdata/source-"+System.currentTimeMillis();
		File originalFile = new File(originalName);
		Utils.writeToFile("this is a test for the session client", originalFile);

		String secret = String.valueOf(System.currentTimeMillis());
		File cwd = new File(".").getAbsoluteFile();

		//initiate session mode
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret, new File(cwd,UFTPWorker.sessionModeTag), false);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		UFTPSessionClient client = new UFTPSessionClient(host, srvPort);
		client.setSecret(secret);
		client.connect();

		Calendar time = Calendar.getInstance();
		String setTo = new SimpleDateFormat("yyyyMMddhhmmss").format(time.getTime());
		client.setModificationTime(originalName, time);
		String now = new SimpleDateFormat("yyyyMMddhhmmss").format(new Date(new File(originalName).lastModified()));
		assertEquals(setTo,now);
		client.close();
	}
	
	@Test
	public void testClientConnectEmptyIP() throws Exception {
		String realSourceName="target/testdata/source-"+System.currentTimeMillis();
		File realSource=new File(realSourceName);
		Utils.writeToFile("this is a test for the session client", realSource);

		String secret = UUID.randomUUID().toString();
		File cwd=new File(".").getAbsoluteFile();
		UFTPTransferRequest job = new UFTPTransferRequest(new InetAddress[0], "nobody", secret, new File(cwd,UFTPWorker.sessionModeTag), true);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);
		InetAddress[]server=host;

		UFTPSessionClient client = new UFTPSessionClient(server, srvPort);
		client.setSecret(secret);

		client.connect();
	
		System.out.println("Files:\n"+client.getFileInfoList("."));
	
		client.close();
	
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

		String secret = String.valueOf(System.currentTimeMillis());
		File cwd=dataDir.getAbsoluteFile();

		//initiate session mode
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret, new File(cwd,UFTPWorker.sessionModeTag), false);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		UFTPSessionClient client = new UFTPSessionClient(host, srvPort);
		client.setSecret(secret);
		client.connect();

		String newDir="test-"+type+System.currentTimeMillis();
		client.mkdir(newDir);
		List<String>ls=client.getFileList(".");
		assertTrue(ls.toString().contains(newDir));
		client.cd(newDir);
		client.setType(UFTPSessionClient.TYPE_ARCHIVE);
		FileInputStream fis=new FileInputStream(realSource);
		client.put(".", realSource.length(), fis);
		fis.close();
		client.closeData();
		long rSize=client.getFileSize("file1");
		assertEquals(7,rSize);
		rSize=client.getFileSize("subdirectory/subfile1");
		assertEquals(7,rSize);
		client.close();
	}

	@Test
	public void testRestrictSession() throws Exception {
		
		String secret = String.valueOf(System.currentTimeMillis());
		File cwd = new File(".").getAbsoluteFile();

		//initiate session mode
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret, new File(cwd,UFTPWorker.sessionModeTag), false);
		job.setAccessPermissions(Session.Mode.NONE);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();
			client.stat(".");
			fail();	
		}catch(Exception ex) {
			System.out.println(Log.createFaultMessage("", ex));
		}
		
		job = new UFTPTransferRequest(host, "nobody", secret, new File(cwd,UFTPWorker.sessionModeTag), false);
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
	public void testMultiStreamEncrypted() throws Exception {
		int numCon = 2;
		byte[] key = Utils.createKey();
		boolean compress = true;
		
		File sourceFile = new File(dataDir,"testsourcefile-"+System.currentTimeMillis());
		makeTestFile2(sourceFile, 1024*500);
		String target = "target/testdata/testfile-" + System.currentTimeMillis();

		// send job to server...
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", "secretCode", 
				new File(sourceFile.getParentFile().getAbsoluteFile(),UFTPWorker.sessionModeTag), true);
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
		// check that file exists and has correct content
		File targetFile = new File(target);
		assertTrue(targetFile.exists());
		String expected = Utils.md5(sourceFile);
		String actual = Utils.md5(targetFile);
		System.out.println("Source file "+sourceFile.getAbsolutePath()+" "+expected);
		System.out.println("Target file "+targetFile.getAbsolutePath()+" "+actual);
		assertEquals("File contents do not match", expected, actual);
	}

	@Test
	public void testHashing() throws Exception {
		String fileName = "target/testdata/source-"+System.currentTimeMillis();
		File dataFile = new File(fileName);
		makeTestFile(dataFile, 32768, 10);
		String secret = String.valueOf(System.currentTimeMillis());
		File cwd = new File(".").getAbsoluteFile();
		//initiate session mode
		UFTPTransferRequest job = new UFTPTransferRequest(host, "nobody", secret, new File(cwd,UFTPWorker.sessionModeTag), false);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);
		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort)){
			client.setSecret(secret);
			client.connect();
			assertTrue(client.supportsHashes());
			System.out.println("Hash algorithm: " + client.getHashAlgorithm());
			try {
				client.setHashAlgorithm("no_such_algo");
			}catch(IOException ok) {
				System.out.println("Expected error: "+ok.getMessage());
			}
			String[] algos = new String[] {"MD5", "SHA-1", "SHA-256", "SHA-512"};
			for(String algo: algos) {
				System.out.println("Hash algorithm set to: " + client.setHashAlgorithm(algo));
				// compute local hash
				HashInfo hashInfo = client.getHash(fileName, 0, dataFile.length());
				String localMD = Utils.hexString(Utils.digest(dataFile, algo));
				String remoteMD = hashInfo.hash;
				System.out.println(algo+" local: "+localMD+" remote: "+remoteMD);
				assertEquals(localMD, remoteMD);
			}
		}
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
