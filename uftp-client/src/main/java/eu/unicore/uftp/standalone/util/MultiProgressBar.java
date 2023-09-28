package eu.unicore.uftp.standalone.util;

import java.io.Closeable;

import org.apache.logging.log4j.Logger;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp.Capability;

import eu.unicore.uftp.client.UFTPProgressListener2;
import eu.unicore.uftp.standalone.util.ClientPool.TransferTracker;
import eu.unicore.util.Log;


/**
 * Show multi-threaded performance / progress when transferring multiple files or chunks
 * 
 * @author schuller
 */
public class MultiProgressBar implements UFTPProgressListener2, Closeable {

	private static final Logger logger = Log.getLogger(Log.CLIENT, MultiProgressBar.class);

	private Terminal terminal=null;	
	private int width;

	private final UnitParser rateParser=UnitParser.getCapacitiesParser(1);

	private final String[] identifiers;
	private final long startedAt[];
	private final long size[];
	private final long have[];
	private final double rate[];
	private final TransferTracker[] trackers;
	
	private String[] sizeDisplay;

	private final int maxThreads;
	private final long[] threadIds;
	private final boolean verbose;

	public MultiProgressBar(int maxThreads, boolean verbose) {
		this.maxThreads = maxThreads;
		this.verbose = verbose;
		this.threadIds = new long[maxThreads];
		this.startedAt = new long[maxThreads];
		this.size = new long[maxThreads];
		this.have = new long[maxThreads];
		this.rate = new double[maxThreads];
		this.identifiers = new String[maxThreads];
		this.sizeDisplay = new String[maxThreads];
		this.trackers = new TransferTracker[maxThreads];
		try{
			terminal = TerminalBuilder.terminal();
			width = terminal.getWidth();
		}catch(Exception ex){
			logger.error("Cannot setup progress bar!",ex);
			terminal = null;
		}
	}

	/**
	 * register a new file transfer 
	 * NOTE: this must be called from a worker thread!
	 */
	public synchronized void registerNew(String identifier, long length, TransferTracker tracker){
		int i = getThreadIndex();
		identifiers[i] = identifier;
		size[i] = length;
		rate[i] = 0;
		have[i] = 0;
		trackers[i] = tracker;
		startedAt[i] = System.currentTimeMillis();
		doSetTransferSize(length);
	}

	@Override
	public void setTransferSize(long size){
		// NOP
	}

	private void doSetTransferSize(long size){
		int i = getThreadIndex();
		this.size[i] = size;
		this.sizeDisplay[i] = rateParser.getHumanReadable(size);
	}

	public synchronized void notifyTotalBytesTransferred(long total){
		if(terminal==null || total<0)return;
		int i = getThreadIndex();
		updateRate(i);
		have[i]=total;
		output();
	}

	protected int getThreadIndex(){
		long currentId = Thread.currentThread().getId();
		for(int i=0; i<maxThreads; i++){
			long threadId = threadIds[i];
			if(threadId==0){
				threadIds[i] = currentId;
				return i;
			}
			else if(threadId==currentId){
				return i;
			}
		}
		throw new IllegalStateException();
	}
	
	protected void updateRate(int i){
		rate[i]=1000*(double)have[i]/(System.currentTimeMillis()-startedAt[i]);
	}

	private int lastOutputLength=0;

	protected void output(){
		StringBuilder sb=new StringBuilder();
		double totalRate = 0;

		for(int i=0; i<maxThreads;i++){
			if(rate[i]>0){
				sb.append(String.format(" %sB/s", rateParser.getHumanReadable(rate[i])));
				totalRate+=rate[i];
			}
			else{
				sb.append(" -------");
			}
		}
		sb.append(String.format(" total %sB/s", rateParser.getHumanReadable(totalRate)));

		int fill = lastOutputLength - sb.length();
		if(fill>0) {
			for(int i=0; i<fill; i++)sb.append(" ");
		}
		lastOutputLength = sb.length();
		synchronized(this){
			try {
				terminal.puts(Capability.carriage_return);
				terminal.writer().write(sb.toString());
				terminal.flush();
			}
			catch (Exception e) {
				logger.error("Could not output to jline console",e);
				terminal = null;
			}
		}
	}
	
	public String getStatus(int threadIndex){
		if(size[threadIndex]==0)return " -------";
		StringBuilder sb = new StringBuilder();
		sb.append(" ").append(sizeDisplay[threadIndex]);
		sb.append(String.format(" %sB/s", rateParser.getHumanReadable(rate[threadIndex])));
		return sb.toString();
	}

	public boolean isCancelled() {
		return false;
	}

	public synchronized void closeWithError(String errorMsg){
		int i = getThreadIndex();
		String id = identifiers[i];
		cleanup(i);
		if(verbose){
			try {
				StringBuilder sb = new StringBuilder();
				sb.append(errorMsg);
				int max = width-sb.length()-5;
				if(max<0)max=8;
				sb.insert(0, String.format("%-"+max+"s", id));
				terminal.puts(Capability.carriage_return);
				terminal.writer().write(sb.toString());
				terminal.flush();
			}catch(Exception ex){}
			System.out.println();
		}
	}

	private void cleanup(int i) {
		identifiers[i] = null;
		sizeDisplay[i] = null;
		size[i] = 0;
		rate[i] = 0;
		have[i] = 0;
		trackers[i] = null;
		startedAt[i]=0;
	}
	
	public synchronized void closeSingle(){
		int i = getThreadIndex();
		TransferTracker t = trackers[i];
		if(verbose){
			have[i]=size[i];
			updateRate(i);
			try {
				StringBuilder sb = new StringBuilder();
				sb.append(getStatus(i));
				int max = width-sb.length()-5;
				if(max<0)max=8;
				sb.insert(0, String.format("%-"+max+"s", identifiers[i]));
				terminal.puts(Capability.carriage_return);
				terminal.writer().write(sb.toString());
				terminal.flush();
			}catch(Exception ex){}
			System.out.println();
		}
		cleanup(i);
		if(t!=null)finalize(t);
	}

	private void finalize(TransferTracker t) {
		try {
			int active = t.chunkCounter.decrementAndGet();
			if(active==0 && verbose && t.numChunks>1) {
				double rate = 1000*(double)t.size / (System.currentTimeMillis()-t.start.get());
				StringBuilder sb = new StringBuilder();
				sb.append("100% ");
				sb.append(rateParser.getHumanReadable(t.size));
				sb.append(String.format(" %sB/s", rateParser.getHumanReadable(rate)));
				int max = width-sb.length()-5;
				if(max<0)max=8;
				sb.insert(0, String.format("%-"+max+"s", t.file));
				synchronized (this) {
					terminal.puts(Capability.newline);
					terminal.puts(Capability.carriage_return);
					terminal.writer().write(sb.toString());
					terminal.puts(Capability.newline);
					terminal.flush();
				}
			}
		}catch(Exception ex) {}
	}

	public void close(){
		System.out.println();
		try{
			if(terminal!=null)terminal.close();
		}catch(Exception ex) {}
	}

}
