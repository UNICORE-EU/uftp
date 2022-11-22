package eu.unicore.uftp.client.async;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.dpc.Utils;

/**
 *
 * @author schuller
 */
public class AsyncProducer implements Runnable {

	private static final Logger logger = Utils.getLogger(Utils.LOG_CLIENT, AsyncProducer.class);

	private final List<Holder> operations = new ArrayList<>();

	public AsyncProducer() throws IOException {
	}

	public synchronized void add(final FileChannel source, final Deque<ByteBuffer> sink, long numBytes)
			throws IOException {
		assert source!=null: "source cannot be null";
		assert sink!=null: "sink cannot be null";
		operations.add(new Holder(source, sink, numBytes));
	}

	private volatile boolean stopped = false;

	public void run() {
		try{
			logger.info("Starting.");
			while(!stopped) {
				Iterator<Holder> iter = operations.iterator();
				while(iter.hasNext()) {
					Holder h = iter.next();
					boolean finished = dataAvailable(h);
					if(finished)iter.remove();
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
		return operations.size();
	}

	public boolean dataAvailable(Holder holder) {
		FileChannel source = holder.source;
		Deque<ByteBuffer> sink = holder.sink;
		//TODO
		if(sink.size()>4)return false;
		Long toRead = holder.toRead;
		try{
			// TODO pool these?
			final ByteBuffer buffer = ByteBuffer.allocate(8192);
			int n=0;
			if(toRead>0) {
				if(toRead<buffer.capacity()) {
					buffer.limit(toRead.intValue());
				}
				n = source.read(buffer);
				if(n>0) {
					toRead -= buffer.position();
					buffer.flip();
					sink.offer(buffer);
					holder.toRead = toRead;
				}
			}
			return (n<0 || toRead<=0 || !source.isOpen());
		}catch(IOException ioe) {
			logger.error("Error handling selection event: "+ioe);
			ioe.printStackTrace();
			return false;
		}
	}

	static class Holder{
		public FileChannel source;
		public Deque<ByteBuffer> sink;
		public Long toRead;
		public Holder(FileChannel source, Deque<ByteBuffer> sink, Long toRead) {
			this.source = source;
			this.sink = sink;
			this.toRead = toRead;
		}
	}

}
