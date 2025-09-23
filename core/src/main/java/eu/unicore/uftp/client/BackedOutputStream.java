package eu.unicore.uftp.client;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Output stream for writing to a backend that only supports chunks of data.
 * Data is buffered internally in a buffer of configurable size, and written
 * to the backend when flushed, closed, or the buffer is full 
 *
 * @author schuller
 */ 
public abstract class BackedOutputStream extends OutputStream {

	protected byte[] buffer = null;

	/**
	 * number of valid bytes in the buffer
	 */
	protected int pos = 0;

	/**
	 * is this the first write operation?
	 */
	protected boolean firstWrite = true;

	/**
	 * is the output stream being closed, i.e. is this the last write operation?
	 */
	protected boolean closing = false;

	/**
	 * @param size - buffer size to use
	 */
	public BackedOutputStream(int size){
		while(buffer==null){
			try{
				buffer = new byte[size];
			}catch(OutOfMemoryError mem){
				size = size/2;
				if(size<8192){
					throw new OutOfMemoryError();
				}
			}
		}
	}

	@Override
	public void write(int b) throws IOException {
		if(pos>=buffer.length) flush();
		buffer[pos]=(byte)b;
		pos++;
	}

	@Override
	public void close() throws IOException {
		closing = true;
		flush();
		for(Closeable c: closeHandlers) {
			c.close();
		}
	}

	@Override
	public void flush() throws IOException {
		writeBuffer();
		firstWrite = false;
		pos = 0;
	}

	/**
	 * write the buffer contents (i.e. the bytes buffer[0] to buffer[pos-1]) 
	 * to the backend. The current state must be checked using 
	 * the <code>firstWrite</code> and <code>closing</code> flags
	 *
	 * @throws IOException
	 */
	protected abstract void writeBuffer() throws IOException;

	private final List<Closeable> closeHandlers = new ArrayList<>();

	/**
	 * add a Closeable to be executed when this stream is closed
	 * @param handler
	 */
	public void addCleanupHandler(Closeable handler) {
		closeHandlers.add(handler);
	}

}
