package eu.unicore.uftp.client.async;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.dpc.Utils;

/**
 *
 * @author schuller
 */
public class AsyncDownloader implements Runnable {

	private static final Logger logger = Utils.getLogger(Utils.LOG_CLIENT, AsyncDownloader.class);

	private final Selector selector;

	private final List<SelectionKey> keys = new ArrayList<>();

	private final ByteBuffer buffer = ByteBuffer.allocate(16384);

	public AsyncDownloader() throws IOException {
		selector = Selector.open();
	}

	public synchronized void add(final SelectableChannel source, final Channel sink, long numBytes,
			UFTPSessionClient client)
	throws IOException {
		assert source instanceof ReadableByteChannel: "source must be readable";
		assert sink!=null: "sink cannot be null";
		assert sink instanceof WritableByteChannel : "sink must be writable";
		SelectionKey key  = source.register(selector,
				SelectionKey.OP_READ,
				new Holder((WritableByteChannel)sink, numBytes, client));
		keys.add(key);
	}

	private volatile boolean stopped = false;
	
	public void run() {
		try{
			logger.info("Starting.");
			while(!stopped) {
				selector.select(50);
				selector.selectedKeys().forEach(key -> dataAvailable(key));
			}
		}catch(Exception ex) {
			logger.error("Error running downloader: ",ex);
		}
	}
	
	public void stop() {
		stopped = true;
	}

	public int getRunningTasks() {
		return keys.size();
	}

	public void dataAvailable(SelectionKey key) {
		Holder attached = (Holder)key.attachment();
		WritableByteChannel sink = attached.sink;
		Long toRead = attached.toRead;
		UFTPSessionClient client = attached.client;
		ReadableByteChannel source = (ReadableByteChannel)key.channel();
		try{
			if(key.isReadable()) {
				buffer.clear();
				int n=0;
				if(toRead>0) {
					if(toRead<buffer.capacity()) {
						buffer.limit(toRead.intValue());
					}
					n = source.read(buffer);
					if(n>0) {
						toRead-=n;
						buffer.flip();
						int written = 0;
						while(written<n) {
							written += sink.write(buffer);
						}
						attached.toRead = toRead;
					}
				}
				if(n<0 || toRead<=0 || !source.isOpen()) {
					keys.remove(key);
					key.cancel();
					client.readControl();
				}
			}
		}catch(IOException ioe) {
			logger.error("Error handling selection event: "+ioe);
			ioe.printStackTrace();
		}
	}
	
	static class Holder{
		public WritableByteChannel sink;
		public Long toRead;
		public UFTPSessionClient client;
		public Holder(WritableByteChannel sink, Long toRead, UFTPSessionClient client) {
			this.sink = sink;
			this.toRead = toRead;
			this.client = client;
		}
	}
	
}
