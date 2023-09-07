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

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.client.UFTPProgressListener;
import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.util.Log;

public class ClientPool implements Closeable {

	private static final Logger logger = Log.getLogger(Log.CLIENT, ClientPool.class);

	private final AtomicInteger num = new AtomicInteger(1);
	
	final ExecutorService es;
	
	final int poolSize;
	
	final ClientFacade clientFacade;
	
	final String uri;
	
	final List<UFTPSessionClient> clients = new ArrayList<>();
	
	final List<Future<?>> tasks = new ArrayList<>();

	final boolean verbose;

	boolean reuseClients = true;
	
	public ClientPool(int poolSize, ClientFacade clientFacade, String uri, boolean verbose) {
		this.clientFacade = clientFacade;
		this.uri = uri;
		this.poolSize = poolSize;
		this.verbose = verbose;
		es = Executors.newFixedThreadPool(poolSize, new ThreadFactory() {

			boolean createNewThreads = true;

			@Override
			public Thread newThread(Runnable r) {
				if(!createNewThreads) return null;
				try{
					UFTPSessionClient client = null;
					if(reuseClients) {
						client = ClientPool.this.clientFacade.doConnect(ClientPool.this.uri);
						clients.add(client);
					}
					UFTPClientThread t = new UFTPClientThread(r, client, clientFacade, uri);
					t.setName("UFTPClient-"+num.getAndIncrement());
					return t;
				}catch(Exception ex){
					logger.info("Creating new client thread failed.", ex);
					createNewThreads = false;
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
		for(Future<?>f: tasks){
			try{
				f.get();
			}catch(Exception ee){
				logger.error(ee);
			}
		}
		for(UFTPSessionClient s: clients){
			IOUtils.closeQuietly(s);
		}
	}

	public void submit(Callable<?> r) {
		tasks.add(es.submit(r));
	}


	public static class UFTPClientThread extends Thread {

		UFTPSessionClient client;
		ClientFacade cf;
		String uri;

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

		private UFTPProgressListener pb = null;

		public TransferTask(UFTPSessionClient sc) {
			this.sc = sc;
		}

		public void setProgressListener(UFTPProgressListener pb) {
			this.pb = pb;
		}

		public UFTPSessionClient getSessionClient() {
			if(sc!=null)return sc;
			UFTPClientThread t = (UFTPClientThread)Thread.currentThread();
			UFTPSessionClient sc = t.getClient();
			if(pb!=null)sc.setProgressListener(pb);
			return sc;
		}

		@Override
		public void close() {
			if(pb!=null && pb instanceof Closeable) {
				IOUtils.closeQuietly((Closeable)pb);
			}
		}
	}

}