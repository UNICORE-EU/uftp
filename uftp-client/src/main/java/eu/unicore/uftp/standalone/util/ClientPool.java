package eu.unicore.uftp.standalone.util;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.client.FileInfo;
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
	
	final MultiProgressBar progressBar;
	
	final boolean verbose;
	
	final long splitThreshold;
	
	boolean reuseClients = true;
	
	public ClientPool(int poolSize, ClientFacade clientFacade, String uri, boolean verbose, long splitThreshold) {
		this.clientFacade = clientFacade;
		this.uri = uri;
		this.poolSize = poolSize;
		this.splitThreshold = splitThreshold;
		this.progressBar = new MultiProgressBar(poolSize);
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
		logger.info("Have "+tasks.size()+" tasks");
		for(Future<?>f: tasks){
			try{
				f.get();
			}catch(Exception ee){
				logger.error(ee);
			}
		}
		for(UFTPSessionClient s: clients){
			closeQuietly(s);
		}
		closeQuietly(progressBar);
	}
	
	public void get(final String remotePath, final String localName, final FileInfo fi, final boolean preserveAttributes){
		get(remotePath, localName, fi, fi.getSize(), 0, preserveAttributes);
	}
	
	public void get(final String remotePath, final String localName, final FileInfo fi,  final long size, final long offset, final boolean preserveAttributes){
		Callable<Boolean> r = new Callable<Boolean>(){
			public Boolean call() throws Exception {
				File file = new File(localName);
				
				try(RandomAccessFile raf = new RandomAccessFile(file, "rw");
					OutputStream fos = Channels.newOutputStream(raf.getChannel());)
				{
					raf.seek(offset);
					UFTPClientThread t =(UFTPClientThread)Thread.currentThread();
					UFTPSessionClient sc = t.getClient();
					logger.info("Downloading <"+remotePath+">");
					if(verbose){
						progressBar.registerNew(localName, size);
						sc.setProgressListener(progressBar);
					}
					sc.get(remotePath, offset, size, fos);
					if(preserveAttributes && !isDevNull(localName)){
						Files.setLastModifiedTime(file.toPath(),FileTime.fromMillis(fi.getLastModified()));
					}
					return Boolean.TRUE;
				}catch(Exception ex){
					logger.error("Error getting <"+remotePath+">",ex);
					throw ex;
				}finally{
					if(verbose)progressBar.closeSingle();
				}
			}
		};
		tasks.add(es.submit(r));
	}
	
	public void getChunked(final String remotePath, final String local, final FileInfo remoteInfo, long start, long total){
		int numChunks = computeNumChunks(total);
		long chunkSize = total / numChunks;
		if(logger.isDebugEnabled()) {
			logger.debug("Downloading: '"+remotePath+"' --> '"+local
					+"', length="+total+" numChunks="+numChunks+" chunkSize="+chunkSize);
		}
		final ParallelProgressBar pb = verbose? new ParallelProgressBar(local, total, numChunks) : null;
		for(int i = 0; i<numChunks; i++){
			final int j = i;
			final long first = start;
			final long end = i<numChunks-1 ? first + chunkSize : total-1;
			Callable<Boolean> r = new Callable<Boolean>(){
				public Boolean call() throws Exception {
					UFTPClientThread t =(UFTPClientThread)Thread.currentThread();
					UFTPSessionClient sc = t.getClient();
					sc.setProgressListener(pb);
					try{
						if(logger.isDebugEnabled()) {
							logger.debug("\nChunk <"+j+"> for '"+local+"' startByte="+first+" endbyte="+end);
						}
						clientFacade.downloadFileChunk(remotePath, local, first, end, sc, remoteInfo);
						if(logger.isDebugEnabled()) {
							logger.debug("\nChunk <"+j+"> for '"+local+"' DONE");
						}
						return Boolean.TRUE;
					}catch(Exception ex){
						logger.error("Error getting chunk ["+first+":"+end+"] <"+remotePath+">",ex);
						throw new RuntimeException(ex);
					}
					finally {
						closeQuietly(pb);
					}
				}
			};
			tasks.add(es.submit(r));
			start = end + 1;
		}
	}
	
	public void getChunked(final String remotePath, final String local, final FileInfo remoteInfo){
		getChunked(remotePath, local, remoteInfo, 0, remoteInfo.getSize());
	}
	
	public void put(final File local, final String remotePath, final long size, final long offset, final boolean preserveAttributes){
		Callable<Boolean> r = new Callable<Boolean>(){
			public Boolean call() throws Exception {
				RandomAccessFile raf = null;
				InputStream fis = null;
				try{
					String localPath = local.getPath();
					raf = new RandomAccessFile(localPath, "r");
					fis = Channels.newInputStream(raf.getChannel());
					raf.seek(offset);
					UFTPClientThread t =(UFTPClientThread)Thread.currentThread();
					UFTPSessionClient sc = t.getClient();
					if(verbose){
						progressBar.registerNew(localPath, size);
						sc.setProgressListener(progressBar);
					}
					sc.put(remotePath, size, offset, fis);
					if(preserveAttributes){
						Calendar to = Calendar.getInstance();
						to.setTimeInMillis(local.lastModified());
						sc.setModificationTime(remotePath, to);
					}
					return Boolean.TRUE;
				}finally{
					closeQuietly(fis);
					closeQuietly(raf);
					if(verbose)progressBar.closeSingle();
				}
			}
		};
		tasks.add(es.submit(r));
	}


	public void putChunked(final File local, final String remotePath, final long start, final long total){
		int numChunks = computeNumChunks(total);
		long chunkSize = total / numChunks;
		long last = total-1;
		if(logger.isDebugEnabled()) {
			logger.debug("Uploading: '"+local.getPath()+"' --> '"+remotePath
					+"', length="+total+" numChunks="+numChunks+" chunkSize="+chunkSize);
		}
		final ParallelProgressBar pb = verbose? new ParallelProgressBar(local.getName(), total, numChunks) : null;

		for(int i = 0; i<numChunks; i++){
			final int j = i;
			final long end = last;
			final long first =  i<numChunks-1 ? end - chunkSize : 0;
			Callable<Boolean> r = new Callable<Boolean>(){
				public Boolean call() throws Exception {
					UFTPClientThread t =(UFTPClientThread)Thread.currentThread();
					UFTPSessionClient sc = t.getClient();
					sc.setProgressListener(pb);
					try{
						if(logger.isDebugEnabled()) {
							logger.debug("\nChunk <"+j+"> for '"+local.getPath()+"' startByte="+first+" endbyte="+end);
						}
						clientFacade.uploadFileChunk(remotePath, local.getPath(), first, end, sc);
						if(logger.isDebugEnabled()) {
							logger.debug("\nChunk <"+j+"> for '"+local.getPath()+"' DONE.");
						}
						return Boolean.TRUE;
					}catch(Exception ex){
						logger.error("Error getting chunk ["+first+":"+end+"] <"+remotePath+">",ex);
						throw new RuntimeException(ex);
					}
					finally{
						closeQuietly(pb);
					}
				}
			};
			tasks.add(es.submit(r));
			last = first - 1;
		}
	}
	
	public void putChunked(final File local, final String remotePath, final long size){
		putChunked(local, remotePath, 0, size);
	}
	
	// we don't want chunks smaller than half of the split threshold
	// otherwise create a few more chunks than we have threads to try 
	// and avoid idle threads
	private int computeNumChunks(long dataSize) {
		long numChunks = 2 * dataSize / splitThreshold;
		return (int)Math.min(numChunks, (long)(1.25*poolSize));
	}

	private boolean isDevNull(String dest) {
		return "/dev/null".equals(dest);
	}

	private void closeQuietly(Closeable c) {
		if(c!=null)try {
			c.close();
		}catch(Exception e) {}
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
}
