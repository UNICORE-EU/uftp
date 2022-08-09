package eu.unicore.uftp.rsync;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.Random;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import eu.unicore.uftp.dpc.Utils;

public class TestRsync {
	
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
	public void testDryRunFilesWithErors() throws Exception{
		File masterFile=new File(dataDir,"master");
		File slaveFile=new File(dataDir,"slave");
		writeTestFiles(masterFile, slaveFile, 16384, 4, 1);
		LocalChannel channel=new LocalChannel(true);
		Slave slave=new Slave(new RandomAccessFile(slaveFile, "r"),channel,slaveFile.getAbsolutePath());
		slave.setDryRun();
		Master master=new Master(new RandomAccessFile(masterFile,"r"),channel,masterFile.getAbsolutePath());
		slave.computeChecksums();
		System.out.println("Num of rsync blocks: "+slave.getWeakChecksums().size()+ " sized "+slave.getBlocksize());
		RsyncStats stats=slave.call();
		System.out.println(stats);
		stats=master.call();
		System.out.println(stats);
		System.out.println();
	}
	
	@Test
	public void testDryRunFilesOffsetBlocks() throws Exception{
		File masterFile=new File(dataDir,"master");
		File slaveFile=new File(dataDir,"slave");
		writeTestFilesOffset(masterFile, slaveFile, 512, 20, 30);
		LocalChannel channel=new LocalChannel(true);
		Slave slave=new Slave(new RandomAccessFile(slaveFile, "r"),channel,slaveFile.getAbsolutePath());
		slave.setDryRun();
		Master master=new Master(new RandomAccessFile(masterFile,"r"),channel,masterFile.getAbsolutePath());
		slave.computeChecksums();
		System.out.println("Num of rsync blocks: "+slave.getWeakChecksums().size()+ " sized "+slave.getBlocksize());
		RsyncStats stats=slave.call();
		System.out.println(stats);
		stats=master.call();
		System.out.println(stats);
		System.out.println();
	}
	

	@Test
	public void testWritingFiles()throws Exception{
		doTest(Slave.DEFAULT_BLOCKSIZE);
		doTest(2*Slave.DEFAULT_BLOCKSIZE);
		doTest(4*Slave.DEFAULT_BLOCKSIZE);
	}
	
	void doTest(int blocksize)throws Exception{
		System.out.println("Rsync using blocksize: "+blocksize);
		System.out.println("");
		File masterFile=new File(dataDir,"master");
		File slaveFile=new File(dataDir,"slave");
		String masterMd5=writeTestFiles(masterFile, slaveFile, 16394, 20, 1);
		LocalChannel channel=new LocalChannel(false);
		Slave slave=new Slave(new RandomAccessFile(slaveFile, "r"),channel,slaveFile.getAbsolutePath());
		Master master=new Master(new RandomAccessFile(masterFile,"r"),channel,masterFile.getAbsolutePath());
		Future<RsyncStats> f=Utils.getExecutor().submit(slave);
		RsyncStats masterStats=master.call();
		RsyncStats slaveStats=f.get();
		System.out.println(slaveStats);
		System.out.println(masterStats);
		String synched=Utils.md5(slaveFile);
		System.out.println("Synched : "+synched);
		Assert.assertEquals(masterMd5, synched);
		Assert.assertTrue(channel.closed);
		System.out.println();
	}

	@Test
	public void testWithTextFiles()throws Exception{
		File masterFile=new File(dataDir,"master");
		File slaveFile=new File(dataDir,"slave");
		writeTextFiles(masterFile, slaveFile, 500, 1);
		String masterMd5=Utils.md5(masterFile);
		LocalChannel channel=new LocalChannel(false);
		Slave slave=new Slave(new RandomAccessFile(slaveFile, "r"),channel,slaveFile.getAbsolutePath());
		Master master=new Master(new RandomAccessFile(masterFile,"r"),channel,masterFile.getAbsolutePath());
		Future<RsyncStats> f=Utils.getExecutor().submit(slave);
		RsyncStats masterStats=master.call();
		RsyncStats slaveStats=f.get();
		System.out.println(slaveStats);
		System.out.println(masterStats);
		String original=Utils.md5(masterFile);
		System.out.println("Original: "+original);
		String synched=Utils.md5(slaveFile);
		System.out.println("Synched : "+synched);
		Assert.assertEquals(masterMd5, synched);
	}
	
	@Test
	public void testLargeFile()throws Exception{
		File masterFile=new File(dataDir,"master");
		File slaveFile=new File(dataDir,"slave");
		//"large" files with a single byte difference
		writeTestFiles(masterFile, slaveFile, 50*1024*1024, 1, 1);
		String masterMd5=Utils.md5(masterFile);
		LocalChannel channel=new LocalChannel(false);
		int blockSize=Slave.reasonableBlockSize(slaveFile);
		System.out.println("Blocksize: "+blockSize);
		Slave slave=new Slave(new RandomAccessFile(slaveFile, "r"),channel,slaveFile.getAbsolutePath(),blockSize);
		Master master=new Master(new RandomAccessFile(masterFile,"r"),channel,masterFile.getAbsolutePath());
		Future<RsyncStats> f=Utils.getExecutor().submit(slave);
		RsyncStats masterStats=master.call();
		RsyncStats slaveStats=f.get();
		System.out.println(slaveStats);
		System.out.println(masterStats);
		String original=Utils.md5(masterFile);
		System.out.println("Original: "+original);
		String synched=Utils.md5(slaveFile);
		System.out.println("Synched : "+synched);
		Assert.assertEquals(masterMd5, synched);
	}

	/**
	 * param blockSize
	 * @param numBlocks - number of blocks
	 * @param numErrorsPerBlock - errors per block
	 * @throws Exception
	 */
	public static String writeTestFiles(File masterFile, File slaveFile, int blockSize,
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

	/*
	 * blocks in master are offset wrt slave
	 */
	public static String writeTestFilesOffset(File masterFile, File slaveFile, int blockSize,
			int numBlocks, int maxOffset) throws Exception{
		FileOutputStream os1=new FileOutputStream(masterFile);
		FileOutputStream os2=new FileOutputStream(slaveFile);
		Random r=new Random();
		byte[]data=new byte[blockSize];
		for(int i=0;i<numBlocks;i++){
			r.nextBytes(data);
			os2.write(data);
			//introduce offset
			int offset=r.nextInt(maxOffset);
			for(int e=0; e<offset; e++){
				os2.write(r.nextInt());
			}
			os1.write(data);
		}
		os1.close();
		os2.close();
		String original=Utils.md5(masterFile);
		System.out.println("Original: "+original);
		System.out.println("Modified: "+Utils.md5(slaveFile));
		System.out.println("File sizes: master "+masterFile.length()+" slave "+slaveFile.length());
		return original;
	}
	
	public static String writeTextFiles(File masterFile, File slaveFile,
			int numLines, int errorProb) throws Exception{
		FileOutputStream os1=new FileOutputStream(masterFile);
		FileOutputStream os2=new FileOutputStream(slaveFile);
		Random r=new Random();
		
		//single extra line
		if(errorProb>0)os2.write("abcd\n".getBytes());
		
		for(int i=0;i<numLines;i++){
			byte[]data=("This is line "+i+"\n").getBytes();
			os1.write(data);
			if(r.nextInt(100)<errorProb){
				//insert line
				os2.write("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n".getBytes());
			}
			os2.write(data);
		}
		os1.close();
		os2.close();
		String original=Utils.md5(masterFile);
		System.out.println("Original: "+original);
		System.out.println("Modified: "+Utils.md5(slaveFile));
		System.out.println("File sizes: master "+masterFile.length()+" slave "+slaveFile.length());
		return original;
	}
	
}
