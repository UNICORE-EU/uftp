package eu.unicore.uftp.rsync;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import eu.unicore.uftp.server.DefaultFileAccess;
import eu.unicore.uftp.server.UserFileAccess;

/**
 * the slave has an out-of-date copy of a file which should be
 * synchronized with the master copy.
 * 
 * @author schuller
 */
public class Slave implements Callable<RsyncStats>{

	private final RandomAccessFile file;

	private final String fileName;

	private final SlaveChannel channel;

	private final int blocksize;

	private final List<Long>weakChecksums=new ArrayList<Long>();

	private final List<byte[]>strongChecksums=new ArrayList<byte[]>();

	public static final int DEFAULT_BLOCKSIZE=512;

	private UserFileAccess fileAccess = new UserFileAccess(new DefaultFileAccess(), "", "");
	
	public Slave(File file, SlaveChannel channel, String name)throws FileNotFoundException{
		this(new RandomAccessFile(file, "r"), channel, name, reasonableBlockSize(file));
	}

	/**
	 * using the default blocksize
	 * @param file - local file that should be synchronized with the master
	 * @param channel - communication to the master
	 * @param name - file name
	 */
	public Slave(RandomAccessFile file, SlaveChannel channel, String name){
		this(file,channel,name,DEFAULT_BLOCKSIZE);
	}

	/**
	 * @param file - local file that should be synchronized with the master
	 * @param channel - communication to the master
	 * @param blocksize
	 */
	public Slave(RandomAccessFile file, SlaveChannel channel, String name, int blocksize){
		this.file=file;
		this.channel=channel;
		this.blocksize=blocksize;
		this.fileName=name;
	}

	public RsyncStats call()throws Exception{
		RsyncStats stats=new RsyncStats(fileName);
		stats.blocksize=blocksize;
		long start=System.currentTimeMillis();
		computeChecksums();
		channel.sendToMaster(weakChecksums, strongChecksums, blocksize);
		stats.transferred=weakChecksums.size()*20+4;
		if(!dryRun)reconstructFile();
		stats.duration=System.currentTimeMillis()-start;
		return stats;
	}

	private int bufsize=4096;

	public void reconstructFile()throws Exception{
		if(bufsize<blocksize){
			bufsize=blocksize*2;
		}
		final File myVersion=new File(fileName);
		final String name=myVersion.getName();
		final File tmpfile=getTmpFile(myVersion);
		
		RandomAccessFile tmp = fileAccess.getRandomAccessFile(tmpfile, "rw");
		FileChannel reconstruct = tmp.getChannel();
		
		RsyncData masterData=null;
		ByteBuffer buf=ByteBuffer.allocate(bufsize);
		byte[] buf2=new byte[blocksize];
		do{
			masterData=channel.receive();
			if(masterData.isShutDown())break;

			// first write literal data			
			long remaining=masterData.bytes;
			int len;
			if(remaining>0){
				long expect=remaining;
				while(remaining>0){
					buf.clear();
					if(remaining<bufsize)buf.limit((int)remaining);
					len=masterData.data.read(buf);
					if(len<0)throw new IOException("Unexpected end of data : expected "+expect+" missing "+remaining);
					remaining-=len;
					buf.flip();
					reconstruct.write(buf);
				}
			}
			// then write referenced block
			long index=masterData.blockNumber;
			if(index>=0){
				file.seek(index*blocksize);
				len=file.read(buf2);
				buf.clear();
				buf.put(buf2,0,len);
				buf.flip();
				reconstruct.write(buf);
			}
		}while(true);

		reconstruct.close();

		Runnable r = new Runnable(){
			public void run(){
				final File backup=new File(myVersion.getParentFile(),"__"+name+"__rsync__orig");
				myVersion.renameTo(backup);
				tmpfile.renameTo(new File(myVersion.getParentFile(),name));
				backup.delete();
			}
		};
		fileAccess.asUser(r);
		
	}

	protected void computeChecksums()throws IOException{
		file.seek(0);
		long size=file.length();
		long offset=0;
		byte[]buf=new byte[blocksize];
		long remaining=size;
		int c=0;
		int len=blocksize;
		while(true){
			if(remaining<blocksize)len=(int)remaining;
			c=file.read(buf,0,len);
			if(c<0)break;
			weakChecksums.add(Checksum.checksum(buf, offset, len-1));
			strongChecksums.add(Checksum.md5(buf));
		}
		file.seek(0);
	}

	public SlaveChannel getChannel() {
		return channel;
	}

	public int getBlocksize() {
		return blocksize;
	}

	public List<Long> getWeakChecksums() {
		return weakChecksums;
	}

	public List<byte[]> getStrongChecksums() {
		return strongChecksums;
	}

	boolean dryRun=false;

	void setDryRun(){
		this.dryRun=true;
	}

	public void setFileAccess(UserFileAccess f){
		this.fileAccess = f;
	}
		
	/// generate the temporary file for writing the reconstructed version
	public File getTmpFile(File file){
		return new File(file.getParentFile(),"__"+file.getName()+"__rsync__tmp");
	}
	
	public static int reasonableBlockSize(File file){
		return Math.max((int)file.length()/1000, DEFAULT_BLOCKSIZE);
	}
}