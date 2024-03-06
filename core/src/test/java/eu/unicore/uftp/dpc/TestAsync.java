package eu.unicore.uftp.dpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

import eu.unicore.uftp.client.FileInfo;
import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.client.async.AsyncDownloader;
import eu.unicore.uftp.client.async.AsyncProducer;
import eu.unicore.uftp.client.async.AsyncUploader;
import eu.unicore.uftp.server.ClientServerTestBase;
import eu.unicore.uftp.server.requests.UFTPSessionRequest;
import eu.unicore.util.Pair;

public class TestAsync extends ClientServerTestBase{

	@Test
	public void testClientRead() throws Exception {
		String realSourceName="target/testdata/source-"+System.currentTimeMillis();
		File realSource=new File(realSourceName);
		Utils.writeToFile("this is a test for the session client", realSource);
		String target = "target/testdata/testfile-" + System.currentTimeMillis();
		File realSource2=new File(realSourceName+"-2");
		Utils.writeToFile("this is a 2nd test for the session client", realSource2);
		String target2 = target+"-2";
		String secret = UUID.randomUUID().toString();
		String secret2 = UUID.randomUUID().toString();
		String cwd=new File(".").getAbsolutePath();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", secret, cwd);
		job.sendTo(host[0], jobPort);
		UFTPSessionRequest job2 = new UFTPSessionRequest(host, "nobody", secret2, cwd);
		job2.sendTo(host[0], jobPort);

		Thread.sleep(1000);
		InetAddress[]server=host;

		try(UFTPSessionClient client = new UFTPSessionClient(server, srvPort);
			FileOutputStream fos = new FileOutputStream(target);
			UFTPSessionClient client2 = new UFTPSessionClient(server, srvPort);
			FileOutputStream fos2 = new FileOutputStream(target2))
		{
			client.setSecret(secret);
			client.connect();
			client2.setSecret(secret2);
			client2.connect();
			AsyncDownloader ad = new AsyncDownloader();

			Pair<SocketChannel, Long> p = client.prepareAsyncGet(realSourceName, 0, -1);
			Pair<SocketChannel, Long> p2 = client2.prepareAsyncGet(realSourceName+"-2", 0, -1);

			ad.add(p.getM1(), fos.getChannel(), p.getM2(), client);
			ad.add(p2.getM1(), fos2.getChannel(), p2.getM2(), client2);

			new Thread(ad, "Downloader").start();
			while(ad.getRunningTasks()>0)Thread.sleep(1000);
			ad.stop();
			// check that client is still in-sync with the UFTP protocol
			System.out.println(client.pwd());
		}
		// check that file exists and has correct content
		checkFile(new File(target), Utils.md5(realSource));
		checkFile(new File(target2), Utils.md5(realSource2));
	}

	@Test
	public void testClientWrite() throws Exception {
		String realSourceName="target/testdata/source-"+System.currentTimeMillis();
		File realSource=new File(realSourceName);
		Utils.writeToFile("this is a test for the session client", realSource);

		String secret = String.valueOf(System.currentTimeMillis());
		String cwd = dataDir.getAbsolutePath();
		UFTPSessionRequest job = new UFTPSessionRequest(host, "nobody", secret, cwd);
		job.sendTo(host[0], jobPort);
		Thread.sleep(1000);

		try(UFTPSessionClient client = new UFTPSessionClient(host, srvPort);
			FileInputStream fis=new FileInputStream(realSource))
		{
			client.setSecret(secret);
			client.connect();
			String newDir="test-"+System.currentTimeMillis();
			client.mkdir(newDir);
			List<FileInfo>ls=client.getFileInfoList(".");
			System.out.println("Files:\n"+ls);
			assertTrue(ls.toString().contains(newDir));
			client.cd(newDir);
			Pair<SocketChannel, Long> p = client.prepareAsyncPut("test", realSource.length(), 0l);
			Deque<ByteBuffer> queue = new ArrayDeque<>();
			AsyncUploader ad = new AsyncUploader();
			ad.add(p.getM1(), queue, p.getM2(), client);
			new Thread(ad, "Uploader").start();
			
			AsyncProducer producer = new AsyncProducer();
			producer.add(fis.getChannel(), queue, realSource.length());
			new Thread(producer, "Producer").start();
			while(ad.getRunningTasks()>0)Thread.sleep(1000);
			ad.stop();
			
			long rSize=client.getFileSize("test");
			assertEquals(realSource.length(),rSize);
		}
	}
	
}
