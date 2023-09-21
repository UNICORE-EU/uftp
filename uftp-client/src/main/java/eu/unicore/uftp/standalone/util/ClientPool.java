package eu.unicore.uftp.standalone.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.util.Log;

public class ClientPool implements Closeable {

	private static final Logger logger = Log.getLogger(Log.CLIENT, ClientPool.class);

	private final AtomicInteger num = new AtomicInteger(0);
	
	final ExecutorService es;
	
	final int poolSize;
	
	final ClientFacade clientFacade;
	
	final String uri;
	
	final List<UFTPSessionClient> clients = new ArrayList<>();
	
	final List<Future<?>> tasks = new ArrayList<>();

	final boolean verbose;

	private final MultiProgressBar pb;

	public ClientPool(int poolSize, ClientFacade clientFacade, String uri, boolean verbose) {
		this.clientFacade = clientFacade;
		this.uri = uri;
		this.poolSize = poolSize;
		this.verbose = verbose;
		this.pb = new MultiProgressBar(poolSize);
		es = Executors.newFixedThreadPool(poolSize, new ThreadFactory() {

			boolean createNewThreads = true;

			@Override
			public Thread newThread(Runnable r) {
				if(!createNewThreads) return null;
				try{
					UFTPSessionClient client = clientFacade.doConnect(ClientPool.this.uri);
					clients.add(client);
					UFTPClientThread t = new UFTPClientThread(r, client, clientFacade, uri);
					t.setName("UFTPClient-"+num.incrementAndGet());
					return t;
				}catch(Exception ex){
					clientFacade.message("Creating new client thread failed: {}", ex);
					createNewThreads = false;
					if(num.get()==0) {
						throw new RuntimeException(ex);
					}
					return null;
				}
			}
		});
	}

	@Override
	public void close() throws IOException{
		logger.info("Shutting down client pool");
		es.shutdown();
		logger.debug("Have {} tasks", tasks.size());
		tasks.forEach(f -> {
			try{
				f.get();
			}catch(Exception e){
				verbose(Log.createFaultMessage("ERROR: ", e));
			}
		});
		clients.forEach(sc -> IOUtils.closeQuietly(sc));
	}

	public void submit(TransferTask r) {
		if(verbose)r.setProgressListener(pb);
		tasks.add(es.submit(r));
	}

	public void verbose(String msg, Object ... params) {
		logger.debug(msg, params);
		if(!verbose)return;
		String f = logger.getMessageFactory().newMessage(msg, params).getFormattedMessage();
		System.out.println(f);
	}

	public static class UFTPClientThread extends Thread {

		final UFTPSessionClient client;
		final ClientFacade cf;
		final String uri;

		public UFTPClientThread(Runnable target, UFTPSessionClient client, ClientFacade cf, String uri) {
			super(target);
			this.client = client;
			this.cf = cf;
			this.uri = uri;
		}

		public UFTPSessionClient getClient(){
			if(client!=null)return client;
			else {
				try{
					return cf.doConnect(uri);
				}catch(Exception e) {
					throw new RuntimeException("Cannot connect to <"+uri+">", e);
				}
			}
		}
	}


	public static abstract class TransferTask implements Callable<Boolean>, Closeable {

		private UFTPSessionClient sc = null;

		private MultiProgressBar pb = null;

		private String id;

		private long dataSize = -1;

		private TransferTracking transferInfo;

		public TransferTask(UFTPSessionClient sc) {
			this.sc = sc;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public long getDataSize() {
			return dataSize;
		}

		public void setDataSize(long dataSize) {
			this.dataSize = dataSize;
		}

		public void setProgressListener(MultiProgressBar pb) {
			this.pb = pb;
		}

		public UFTPSessionClient getSessionClient() {
			if(sc!=null)return sc;
			UFTPClientThread t = (UFTPClientThread)Thread.currentThread();
			UFTPSessionClient sc = t.getClient();
			if(pb!=null) { 
				pb.registerNew(getId(), getDataSize(), transferInfo);
				sc.setProgressListener(pb);
			}
			return sc;
		}

		public TransferTracking getTransferInfo() {
			return transferInfo;
		}

		public void setTransferInfo(TransferTracking transferInfo) {
			this.transferInfo = transferInfo;
		}

		public abstract void doCall() throws Exception;
		
		@Override
		public Boolean call(){
			try {
				if(transferInfo!=null) {
					transferInfo.start.compareAndExchange(0, System.currentTimeMillis());
				}
				doCall();
				return Boolean.TRUE;
			}
			catch(Exception e) {
				throw new RuntimeException(e);
			}
			finally {
				close();
			}
		}

		@Override
		public void close() {
			if(pb!=null) {
				pb.closeSingle();
			}
		}
	}

	public static class TransferTracking {
		public final String file;
		public final long size;
		public final AtomicLong start;
		public final int numChunks;
		public final AtomicInteger chunkCounter;
		public TransferTracking(String file, long size, int numChunks, AtomicInteger chunkCounter, AtomicLong start) {
			this.file = file;
			this.size = size;
			this.chunkCounter = chunkCounter;
			this.start = start;
			this.numChunks = numChunks;
		}
	}
}