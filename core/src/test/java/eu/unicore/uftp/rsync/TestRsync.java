package eu.unicore.uftp.rsync;

import java.io.ByteArrayOutputStream;
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
		//FileUtils.deleteQuietly(dataDir);
	}
	
	@Test
	public void testDryRunFilesWithErors() throws Exception{
		File masterFile=new File(dataDir,"master");
		File slaveFile=new File(dataDir,"slave");
		writeTestFiles(masterFile, slaveFile, 16384, 4, 1);
		LocalChannel channel=new LocalChannel(true);
		Follower slave=new Follower(new RandomAccessFile(slaveFile, "r"),channel,slaveFile.getAbsolutePath());
		slave.setDryRun();
		Leader master=new Leader(new RandomAccessFile(masterFile,"r"),channel,masterFile.getAbsolutePath());
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
		Follower slave=new Follower(new RandomAccessFile(slaveFile, "r"),channel,slaveFile.getAbsolutePath());
		slave.setDryRun();
		Leader master=new Leader(new RandomAccessFile(masterFile,"r"),channel,masterFile.getAbsolutePath());
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
		doTest(Follower.DEFAULT_BLOCKSIZE);
		doTest(2*Follower.DEFAULT_BLOCKSIZE);
		doTest(4*Follower.DEFAULT_BLOCKSIZE);
	}
	
	void doTest(int blocksize)throws Exception{
		System.out.println("Rsync using blocksize: "+blocksize);
		System.out.println("");
		File masterFile=new File(dataDir,"master");
		File slaveFile=new File(dataDir,"slave");
		String masterMd5=writeTestFiles(masterFile, slaveFile, 16394, 20, 1);
		LocalChannel channel=new LocalChannel(false);
		Follower slave=new Follower(new RandomAccessFile(slaveFile, "r"),channel,slaveFile.getAbsolutePath());
		Leader master=new Leader(new RandomAccessFile(masterFile,"r"),channel,masterFile.getAbsolutePath());
		Future<RsyncStats> f=Utils.getExecutor().submit(slave);
		RsyncStats masterStats=master.call();
		RsyncStats slaveStats=f.get();
		System.out.println(slaveStats);
		System.out.println(masterStats);
		String synched=Utils.md5(slaveFile);
		System.out.println("Synched : "+synched);
		Assert.assertEquals(masterMd5, synched);
		System.out.println();
	}

	@Test
	public void testWithTextFiles()throws Exception{
		File masterFile=new File(dataDir,"master");
		File slaveFile=new File(dataDir,"slave");
		writeTextFiles(masterFile, slaveFile,  512*1024, 1);
		String masterMd5=Utils.md5(masterFile);
		LocalChannel channel=new LocalChannel(false);
		Follower slave=new Follower(new RandomAccessFile(slaveFile, "r"),channel,
				slaveFile.getAbsolutePath(), 64);
		Leader master=new Leader(new RandomAccessFile(masterFile,"r"),channel,masterFile.getAbsolutePath());
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
		Assert.assertEquals(masterStats.matches, 8191);	
	}
	
	@Test
	public void testWithIdenticalFiles()throws Exception{
		File masterFile=new File(dataDir,"master");
		File slaveFile=new File(dataDir,"slave");
		String masterMd5 = writeTextFiles(masterFile, slaveFile, 4*1024*1024, 0);
		LocalChannel channel=new LocalChannel(false);
		Follower slave=new Follower(new RandomAccessFile(slaveFile, "r"),channel,slaveFile.getAbsolutePath(), 1024);
		Leader master=new Leader(new RandomAccessFile(masterFile,"r"),channel,masterFile.getAbsolutePath());
		Future<RsyncStats> f=Utils.getExecutor().submit(slave);
		RsyncStats masterStats=master.call();
		System.out.println(masterStats);
		RsyncStats slaveStats=f.get();
		System.out.println(slaveStats);
		String original=Utils.md5(masterFile);
		System.out.println("Original: "+original);
		String synched=Utils.md5(slaveFile);
		System.out.println("Synched : "+synched);
		Assert.assertEquals(masterMd5, synched);
		Assert.assertEquals(0, masterStats.misses);
	}

	@Test
	public void testWithAlmostIdenticalFiles()throws Exception{
		File masterFile=new File(dataDir,"master");
		File slaveFile=new File(dataDir,"slave");
		String masterMd5 = writeTextFiles(masterFile, slaveFile, 371099, 1);
		LocalChannel channel=new LocalChannel(false);
		Follower slave=new Follower(new RandomAccessFile(slaveFile, "r"),channel,slaveFile.getAbsolutePath(), 300);
		Leader master=new Leader(new RandomAccessFile(masterFile,"r"),channel,masterFile.getAbsolutePath());
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
		Assert.assertEquals(1, masterStats.misses);
	}
	
	
	@Test
	public void testLargeFile()throws Exception{
		File masterFile=new File(dataDir,"master");
		File slaveFile=new File(dataDir,"slave");
		//"large" files with a few single byte difference
		writeTestFiles(masterFile, slaveFile, 32*1024, 50, 1);
		String masterMd5=Utils.md5(masterFile);
		LocalChannel channel=new LocalChannel(false);
		int blockSize = 64;//Slave.reasonableBlockSize(slaveFile);
		Follower slave=new Follower(new RandomAccessFile(slaveFile, "r"),channel,slaveFile.getAbsolutePath(),blockSize);
		Leader master=new Leader(new RandomAccessFile(masterFile,"r"),channel,masterFile.getAbsolutePath());
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
		
		try(FileOutputStream os1=new FileOutputStream(masterFile);
				FileOutputStream os2=new FileOutputStream(slaveFile)){
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
		}
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
		
		try(FileOutputStream os1=new FileOutputStream(masterFile);
				FileOutputStream os2=new FileOutputStream(slaveFile)){
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
		}
		String original=Utils.md5(masterFile);
		System.out.println("Original: "+original);
		System.out.println("Modified: "+Utils.md5(slaveFile));
		System.out.println("File sizes: master "+masterFile.length()+" slave "+slaveFile.length());
		return original;
	}
	
	public static String writeTextFiles(File masterFile, File slaveFile,
			long length, long errors) throws Exception{
		try(FileOutputStream os1=new FileOutputStream(masterFile);
			FileOutputStream os2=new FileOutputStream(slaveFile)){
			long total = 0;
			int i = 1;
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			while(total<length) {
				byte[]data=(String.format("This is line %12d\n", i)).getBytes();
				i++;
				bos.write(data);
				total += data.length;
			}
			byte[] arr = bos.toByteArray();
			os2.write(arr);
			for(i=0; i<errors; i++) {
				int at = new Random().nextInt((int)total/2);
				arr[at]='X';
				System.out.println("Adding ERROR at "+at);
			}
			os1.write(arr);
		}
		String original=Utils.md5(masterFile);
		System.out.println("Original: "+original);
		System.out.println("Modified: "+Utils.md5(slaveFile));
		System.out.println("File sizes: master "+masterFile.length()+" slave "+slaveFile.length());
		return original;
	}
	
}
