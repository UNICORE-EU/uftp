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
import org.apache.logging.log4j.message.ParameterizedMessage;

import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.util.Log;
import eu.unicore.util.Pair;

public class ClientPool implements Closeable {

	private final AtomicInteger num = new AtomicInteger(0);
	
	final ExecutorService es;
	
	final int poolSize;
	
	final ClientFacade clientFacade;
	
	final String uri;
	
	final List<UFTPSessionClient> clients = new ArrayList<>();
	
	final List<Pair<TransferTask,Future<Boolean>>> tasks;

	final boolean verbose;

	private final MultiProgressBar pb;

	public ClientPool(List<Pair<TransferTask,Future<Boolean>>> tasks, int poolSize, ClientFacade clientFacade, String uri, boolean verbose, boolean showPerformance) {
		this.tasks = tasks;
		this.clientFacade = clientFacade;
		this.uri = uri;
		this.poolSize = poolSize;
		this.verbose = verbose;
		this.pb = showPerformance ?  new MultiProgressBar(poolSize, verbose) : null;
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
		es.shutdown();
		tasks.forEach(p -> {
			Future<Boolean> f = p.getM2();
			TransferTask t = p.getM1();
			try{
				f.get();
			}catch(Exception e){
				message(Log.createFaultMessage("ERROR in <"+t.getId()+">", e));
			}
		});
		clients.forEach(sc -> IOUtils.closeQuietly(sc));
		if(pb!=null)pb.close();
	}

	public void submit(TransferTask r) {
		if(pb!=null)r.setProgressListener(pb);
		tasks.add(new Pair<>(r, es.submit(r)));
	}

	public void verbose(String msg, Object ... params) {
		if(verbose)message(msg, params);
	}

	public void message(String msg, Object ... params) {
		System.out.println(new ParameterizedMessage(msg, params).getFormattedMessage());
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

		private TransferTracker transferTracker;

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
				pb.registerNew(getId(), getDataSize(), transferTracker);
				sc.setProgressListener(pb);
			}
			return sc;
		}

		public TransferTracker getTransferTracker() {
			return transferTracker;
		}

		public void setTransferTracker(TransferTracker transferTracker) {
			this.transferTracker = transferTracker;
		}

		protected abstract void doCall() throws Exception;
		
		@Override
		public Boolean call(){
			try {
				if(transferTracker!=null) {
					transferTracker.start.compareAndExchange(0, System.currentTimeMillis());
				}
				doCall();
				close();
				return Boolean.TRUE;
			}
			catch(Exception e) {
				if(pb!=null) {
					pb.closeWithError(Log.getDetailMessage(e));
				}
				throw new RuntimeException(e);
			}
		}

		@Override
		public void close() {
			if(pb!=null) {
				pb.closeSingle();
			}
		}
	}

	public static class TransferTracker {
		public final String file;
		public final long size;
		public final AtomicLong start;
		public final int numChunks;
		public final AtomicInteger chunkCounter;
		public TransferTracker(String file, long size, int numChunks, AtomicInteger chunkCounter, AtomicLong start) {
			this.file = file;
			this.size = size;
			this.chunkCounter = chunkCounter;
			this.start = start;
			this.numChunks = numChunks;
		}
	}
}