package eu.unicore.uftp.rsync;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import eu.unicore.uftp.dpc.Utils;


public class TestRsyncViaSocket {
	
	static File dataDir=new File("target","data");
	
	@Before
	public void init(){
		FileUtils.deleteQuietly(dataDir);
		dataDir.mkdirs();
	}
	
	@After
	public void cleanup(){
		FileUtils.deleteQuietly(dataDir);
	}
	
	@Test
	public void testWritingFiles()throws Exception{
		doTest(Slave.DEFAULT_BLOCKSIZE);
	//	doTest(2*Slave.DEFAULT_BLOCKSIZE);
	//	doTest(4*Slave.DEFAULT_BLOCKSIZE);
	}
	
	void doTest(int blocksize)throws Exception{
		System.out.println("Rsync using blocksize: "+blocksize);
		System.out.println("");
		File masterFile=new File(dataDir,"master");
		File slaveFile=new File(dataDir,"slave");
		String masterMd5=writeTestFiles(masterFile, slaveFile, 16394, 20, 1);
		
		ServerSocket serverSocket=new ServerSocket(0);
		Future<Socket>getServerSock=Utils.getExecutor().submit(new GetServerSideSocket(serverSocket)); 
		Socket client=new Socket("localhost",serverSocket.getLocalPort());
		Socket server=getServerSock.get();
		System.out.println("Client "+client.getLocalAddress()+" port "+client.getLocalPort());
		System.out.println("Server "+server.getLocalAddress()+" port "+server.getLocalPort());
		SocketSlaveChannel slaveChannel=new SocketSlaveChannel(client);
		SocketMasterChannel masterChannel=new SocketMasterChannel(server);
		
		Slave slave=new Slave(new RandomAccessFile(slaveFile, "r"),slaveChannel,slaveFile.getAbsolutePath());
		Master master=new Master(new RandomAccessFile(masterFile,"r"),masterChannel,masterFile.getAbsolutePath());
		Future<RsyncStats> f=Utils.getExecutor().submit(slave);
		RsyncStats masterStats=master.call();
		RsyncStats slaveStats=f.get();
		System.out.println(slaveStats);
		System.out.println(masterStats);
		String synched=Utils.md5(slaveFile);
		System.out.println("Synched : "+synched);
		Assert.assertEquals(masterMd5, synched);
		
		System.out.println();
		server.close();
		client.close();
		serverSocket.close();
	}
	
	/**
	 * param blockSize
	 * @param numBlocks - number of blocks
	 * @param numErrorsPerBlock - errors per block
	 * @throws Exception
	 */
	private String writeTestFiles(File masterFile, File slaveFile, int blockSize,
			int numBlocks, int numErrorsPerBlock) throws Exception{
		FileOutputStream os1=new FileOutputStream(masterFile);
		FileOutputStream os2=new FileOutputStream(slaveFile);
		Random r=new Random();
		byte[]data=new byte[blockSize];
		for(int i=0;i<numBlocks;i++){
			r.nextBytes(data);
			os1.write(data);
			//introduce some errors
			for(int e=0; e<numErrorsPerBlock; e++){
				int index=r.nextInt(data.length);
				data[index]=(byte)r.nextInt();
			}
			os2.write(data);
		}
		os1.close();
		os2.close();
		String original=Utils.md5(masterFile);
		System.out.println("Original: "+original);
		System.out.println("Slave : "+Utils.md5(slaveFile));
		System.out.println("Sizes : master "+masterFile.length()+" slave "+slaveFile.length());
		return original;
	}
	
	public class GetServerSideSocket implements Callable<Socket>{

		final ServerSocket server;
		
		GetServerSideSocket(ServerSocket server){
			this.server=server;
		}
		@Override
		public Socket call() throws Exception {
			return server.accept();
		}
		
	}
	
	
}
