package eu.unicore.uftp.standalone.util;

import java.io.Closeable;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.client.UFTPProgressListener2;
import eu.unicore.util.Log;
import jline.Terminal;
import jline.TerminalFactory;
import jline.console.ConsoleReader;

/**
 * Track progress when transferring multiple file
 * 
 * @author schuller
 */
public class MultiProgressBar implements UFTPProgressListener2, Closeable {

	private static final Logger logger = Log.getLogger(Log.CLIENT, MultiProgressBar.class);

	private Terminal terminal=null;	
	private ConsoleReader reader=null;
	private int width;

	private final UnitParser rateParser=UnitParser.getCapacitiesParser(1);

	private final String[] identifiers;
	private final long startedAt[];
	private final long size[];
	private final long have[];
	private final double rate[];
	private String[] sizeDisplay;

	private final int numThreads;
	private final long[] threadIds;


	public MultiProgressBar(int numThreads) {
		this.numThreads = numThreads;
		this.threadIds = new long[numThreads];
		this.startedAt = new long[numThreads];
		this.size = new long[numThreads];
		this.have = new long[numThreads];
		this.rate = new double[numThreads];
		this.identifiers = new String[numThreads];
		this.sizeDisplay = new String[numThreads];

		try{
			terminal = TerminalFactory.get();
			reader = new ConsoleReader();
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
	public synchronized void registerNew(String identifier, long length){
		int i = getThreadIndex();
		identifiers[i] = identifier;
		size[i] = length;
		rate[i] = 0;
		have[i] = 0;
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
		for(int i=0; i<numThreads; i++){
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

	protected void output(){
		StringBuilder sb=new StringBuilder();
		double totalRate = 0;

		for(int i=0; i<numThreads;i++){
			if(rate[i]>0){
				sb.append(String.format(" %sB/s", rateParser.getHumanReadable(rate[i])));
				totalRate+=rate[i];
			}
			else{
				sb.append(" -------");
			}
		}
		sb.append(String.format(" total %sB/s", rateParser.getHumanReadable(totalRate)));
		int max = width-sb.length()-5;
		if(max>0){
			sb.append(String.format("%-"+max+"s", " "));
		}
		try {
			reader.getCursorBuffer().clear();
			reader.getCursorBuffer().write(sb.toString());
			reader.setCursorPosition(width);
			reader.redrawLine();
			reader.flush();
		}
		catch (Exception e) {
			logger.error("Could not output to jline console",e);
			terminal = null;
		}
	}
	
	public String getStatus(int threadIndex){
		if(size[threadIndex]==0)return " -------";
		StringBuilder sb = new StringBuilder();
		long progress=have[threadIndex]*100/size[threadIndex];
		sb.append(String.format("%3d%%  %s",progress, sizeDisplay[threadIndex]));
		sb.append(String.format(" %sB/s", rateParser.getHumanReadable(rate[threadIndex])));
		return sb.toString();
	}

	public boolean isCancelled() {
		return false;
	}

	public synchronized void closeSingle(){
		int i = getThreadIndex();
		have[i]=size[i];
		updateRate(i);
		try {
			StringBuilder sb = new StringBuilder();
			sb.append(getStatus(i));
			int max = width-sb.length()-5;
			if(max>0){
				sb.insert(0, String.format("%-"+max+"s", identifiers[i]));
			}
			reader.getCursorBuffer().clear();
			reader.getCursorBuffer().write(sb.toString());
			reader.setCursorPosition(width);
			reader.redrawLine();
			reader.flush();
		}catch(Exception ex){}
		System.out.println();
		identifiers[i] = null;
		sizeDisplay[i] = null;
		size[i] = 0;
		rate[i] = 0;
		have[i] = 0;
		startedAt[i]=0;
	}

	public void close(){
		System.out.println();
		if(reader != null) reader.shutdown();
	}

}
