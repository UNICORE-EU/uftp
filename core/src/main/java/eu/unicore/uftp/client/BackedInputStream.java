package eu.unicore.uftp.client;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * an input stream that reads data on demand from a back-end
 *
 * @author schuller
 */
public abstract class BackedInputStream extends InputStream {

	// read buffer
	protected byte[] buffer;
	// bytes available to the application before buffer has to be re-filled
	protected int avail = 0;
	// position in the read buffer
	protected int pos = 0;
	// position in the underlying source
	protected long bytesRead = 0;
	// total length of data to read
	protected long length = -1;

	public BackedInputStream(int bufferSize, long length){
		this.length = length;
		while(buffer==null){
			try{
				buffer=new byte[bufferSize];
			}catch(OutOfMemoryError mem){
				bufferSize=bufferSize/2;
				if(bufferSize<8192){
					throw new OutOfMemoryError();
				}
			}
		}
	}		

	public BackedInputStream(int bufferSize){
		this(bufferSize,-1);
	}		

	@Override
	public int read() throws IOException {
		if(length==-1)length = getTotalDataSize();
		try{
			if(avail==0 && bytesRead<length){
				fillBuffer();
			}
		}catch(Exception e){
			throw new IOException("Error reading from TSI",e);
		}
		if(avail>0){
			avail--;
			bytesRead++;
			return buffer[pos++] & 0xff;
		}
		else return -1;
	}

	protected abstract void fillBuffer()throws IOException;

	/**
	 * called on first read: get the total size of data to download
	 */
	protected abstract long getTotalDataSize()throws IOException;

	@Override
	public void close() throws IOException {
		super.close();
		for(Closeable c: closeHandlers) {
			c.close();
		}
	}

	/**
	 * skip over some bytes by increasing the position
	 * and discarding the read buffer
	 */
	@Override
	public long skip(long n)throws IOException{
		if(n<=0)return 0;
		if(length==-1)length = getTotalDataSize();
		avail=0;
		if((bytesRead+n)<length){
			bytesRead+=n;
			return n;
		}
		else{// skip to end
			long skipped=length-bytesRead;
			bytesRead=length;
			return skipped;
		}
	}

	private final List<Closeable> closeHandlers = new ArrayList<>();

	/**
	 * add a Closeable to be executed when this stream is closed
	 * @param handler
	 */
	public void addCleanupHandler(Closeable handler) {
		closeHandlers.add(handler);
	}

}
