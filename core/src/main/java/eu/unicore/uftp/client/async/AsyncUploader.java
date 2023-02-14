package eu.unicore.uftp.client.async;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.dpc.Utils;

/**
 *
 * @author schuller
 */
public class AsyncUploader implements Runnable {

	private static final Logger logger = Utils.getLogger(Utils.LOG_CLIENT, AsyncUploader.class);

	private final Selector selector;

	private final List<SelectionKey> keys = new ArrayList<>();

	public AsyncUploader() throws IOException {
		selector = Selector.open();
	}

	public synchronized void add(final SelectableChannel sink, final Deque<ByteBuffer> source, long numBytes,
			UFTPSessionClient client) throws IOException {
		assert sink!=null: "sink cannot be null";
		assert sink instanceof WritableByteChannel: "source must be readable";
		SelectionKey key  = sink.register(selector,
				SelectionKey.OP_WRITE,
				new Holder(source, numBytes, client));
		keys.add(key);
	}

	private volatile boolean stopped = false;
	
	public void run() {
		try{
			logger.info("Starting.");
			while(!stopped) {
				selector.select(50);
				Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
				while(iter.hasNext()) {
					SelectionKey key = iter.next();
					if(key.isValid())tryWrite(key);
					iter.remove();
				}
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

	public void tryWrite(SelectionKey key) {
		Holder attached = (Holder)key.attachment();
		Deque<ByteBuffer> source = attached.source;
		ByteBuffer buffer = source.pollFirst();
		if(buffer==null)return;
		Long toWrite = attached.toWrite;
		WritableByteChannel sink = (WritableByteChannel)key.channel();
		try{
			if(key.isWritable()) {
				if(toWrite>0) {
					if(toWrite<buffer.limit()) {
						buffer.limit(toWrite.intValue());
					}
					int n = buffer.limit();
					int written = 0;
					while(written<n) {
						int wrote = sink.write(buffer);
						written += wrote;
						toWrite -= wrote;
					}
					attached.toWrite = toWrite;
				}
				if(toWrite<=0 || !sink.isOpen()) {
					keys.remove(key);
					key.cancel();
					attached.client.readControl();
				}
			}
		}catch(IOException ioe) {
			logger.error("Error handling selection event: "+ioe);
			ioe.printStackTrace();
		}
	}
	
	static class Holder{
		public Deque<ByteBuffer> source;
		public Long toWrite;
		public UFTPSessionClient client;
		public Holder(Deque<ByteBuffer> source, Long toWrite, UFTPSessionClient client) {
			this.source = source;
			this.toWrite = toWrite;
			this.client = client;
		}
	}
	
}
