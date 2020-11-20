package eu.unicore.uftp.standalone.util;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.client.UFTPProgressListener2;
import eu.unicore.util.Log;
import jline.Terminal;
import jline.TerminalFactory;
import jline.console.ConsoleReader;

/**
 * Track progress from multiple threads when transferring a single file
 * 
 * @author schuller
 */
public class ParallelProgressBar implements UFTPProgressListener2, Closeable {

	private static final Logger logger = Log.getLogger(Log.CLIENT, ParallelProgressBar.class);

	private Terminal terminal=null;	
	private ConsoleReader reader=null;
	private int width;
	private long size;
	
	private long startedAt=0;
	private final UnitParser rateParser=UnitParser.getCapacitiesParser(1);
	private String sizeDisplay;

	private final long have[];
	private final double rate[];

	private final String identifier;

	private final int threadCount;
	private final long[] threadIds;
	
	private final AtomicInteger activeThreads;
	
	public ParallelProgressBar(String identifier,long size, int numThreads) {
		this.threadCount = numThreads;
		this.threadIds = new long[numThreads];
		this.have = new long[numThreads];
		this.rate = new double[numThreads];
		this.activeThreads = new AtomicInteger(numThreads);
		this.identifier=identifier;
		startedAt=System.currentTimeMillis();
		try{
			terminal = TerminalFactory.create();
			reader = new ConsoleReader();
			width =	terminal.getWidth();
			doSetTransferSize(size);
		}catch(Exception ex){
			logger.error("Cannot setup progress bar!",ex);
			terminal = null;
			width = 0;
		}
	}

	@Override
	public void setTransferSize(long size){
		// NOP
	}
	
	private void doSetTransferSize(long size){
		this.size=size;
		this.sizeDisplay=rateParser.getHumanReadable(size);
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
		for(int i=0; i<threadCount; i++){
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
	
	protected void updateRate(int threadIndex){
		rate[threadIndex]=1000*(double)have[threadIndex]/(System.currentTimeMillis()-startedAt);
	}

	protected synchronized void output(){
		long total = 0;
		for(int i=0; i<threadCount;i++)total+=have[i];
		output(total);
	}
	
	protected synchronized void output(long total){
		StringBuilder sb=new StringBuilder();
		long progress=total*100/size;
		sb.append(String.format("%3d%%  %s",progress, sizeDisplay));
		
		double totalRate = 0;
		
		//append rates
		sb.append(" [");
		for(int i=0; i<threadCount;i++){
			if(rate[i]>0){
				if(i>0)sb.append("|");
				sb.append(String.format(" %sB/s ", rateParser.getHumanReadable(rate[i])));
				totalRate+=rate[i];
			}
		}
		sb.append("] ");
		sb.append(String.format(" %sB/s", rateParser.getHumanReadable(totalRate)));
		//compute maximum width of identifier printout
		int max = width-sb.length()-5;
		if(max>0){
			sb.insert(0, String.format("%-"+max+"s ", identifier));
		}
		try {
			reader.getCursorBuffer().clear();
			reader.getCursorBuffer().write(sb.toString());
			reader.redrawLine();
			reader.flush();
		}
		catch (Exception e) {
			logger.error("Could not output to jline console",e);
			terminal = null;
		}
	}

	public boolean isCancelled() {
		return false;
	}

	public void close(){
		if(activeThreads.decrementAndGet()>0)return;
		
		if(size>0){
			output(size);
		}
		else{
			output(0);
		}
		System.out.println();
		if(reader != null) reader.shutdown();
	}

}
