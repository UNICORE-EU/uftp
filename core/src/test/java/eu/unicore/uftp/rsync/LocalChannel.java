package eu.unicore.uftp.rsync;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.List;
import java.util.concurrent.SynchronousQueue;

/**
 * for testing: direct channel between Slave and Master
 */
public class LocalChannel implements SlaveChannel, MasterChannel {

	private final boolean dryRun;
	
	ChecksumHolder holder;
	
	public LocalChannel(boolean dryRun){
		this.dryRun=dryRun;
	}
	
	// slave channel
	
	@Override
	public void sendToMaster(List<Long> weakChecksums, List<byte[]> strongChecksums, int blocksize)throws IOException{
		holder=new ChecksumHolder();
		holder.blocksize=blocksize;
		holder.weakChecksums=weakChecksums;
		holder.strongChecksums=strongChecksums;
		try{
			if(!dryRun)handoff2.put(holder);
		}catch(Exception ex){
			throw new IOException(ex);
		}
	}

	 
	private final SynchronousQueue<RsyncData>handoff=new SynchronousQueue<RsyncData>();
	
	@Override
	public RsyncData receive() throws IOException {
		try{
			return dryRun? null : handoff.take();
		}catch(Exception te){
			throw new IOException(te);
		}
	}

	// master channel

	private final SynchronousQueue<ChecksumHolder>handoff2=new SynchronousQueue<ChecksumHolder>();
	
	@Override
	public ChecksumHolder receiveChecksums() {
		try{
			return dryRun? holder : handoff2.take();
		}catch(Exception e){
			throw new RuntimeException(e);
		}
	}

	@Override
	public void sendData(long bytes, ByteChannel source, int blockIndex) throws IOException {
		ReadableByteChannel dataSource=null;
		if(bytes>=0){
			ByteBuffer buf=ByteBuffer.allocate((int)bytes);
			int have=0;
			while(have < bytes){
				have+=source.read(buf);
			}
			buf.flip();
			dataSource=new ByteArrayChannel(buf.array());
		}
		RsyncData rd=new RsyncData(bytes, dataSource, blockIndex);
		try{
			if(!dryRun){
				handoff.put(rd);
			}
		}catch(Exception te){
			throw new IOException(te);
		}
	}

	protected boolean closed=false;
	
	@Override
	public void shutdown() throws IOException {
		sendData(-1, null, -1);
		closed=true;
	}

	public static class ByteArrayChannel implements ReadableByteChannel{

		private final byte[] buf;
		
		private int pos=0;
		
		public ByteArrayChannel(byte[] buf){
			this.buf=buf;
		}
		
		@Override
		public boolean isOpen() {
			return true;
		}

		@Override
		public void close() throws IOException {
		
		}

		@Override
		public int read(ByteBuffer dst) throws IOException {
			int read=Math.min(dst.remaining(),buf.length-pos);
			dst.put(buf,pos,read);
			pos+=read;
			return read;
		}
		
	}
	
}
