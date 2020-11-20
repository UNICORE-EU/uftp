package eu.unicore.uftp.standalone.util;

import jline.Terminal;
import jline.TerminalFactory;
import jline.console.ConsoleReader;

import java.io.Closeable;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.client.UFTPProgressListener2;
import eu.unicore.util.Log;

/**
 * console progress bar using jline
 * 
 * @author schuller
 */
public class ProgressBar implements UFTPProgressListener2, Closeable {

	private static final Logger logger = Log.getLogger(Log.CLIENT, ProgressBar.class);

	private Terminal terminal=null;	
	private ConsoleReader reader=null;
	private long size=-1;
	private long have=0;
	private long startedAt=0;
	private final UnitParser rateParser=UnitParser.getCapacitiesParser(1);
	private String sizeDisplay;

	//bytes per second
	private double rate=0;

	private final String identifier;

	//for displaying spinning thingy if size is unknown
	private final char[] x=new char[]{'|','/','-','\\'};
	private int index=0;

	/**
	 * 
	 * @param identifier - fixed ID, e.g. file name, to display
	 * @param size - if this is non-positive, a "spinning" progress indicator will be displyoed
	 */
	public ProgressBar(String identifier,long size) {
		this.identifier=identifier;
		startedAt=System.currentTimeMillis();
		try{
			terminal = TerminalFactory.get();
			reader = new ConsoleReader();
			setTransferSize(size);
		}catch(Exception ex){
			logger.error("Cannot setup progress bar!",ex);
			terminal = null;
		}
	}

	public void setTransferSize(long size){
		this.size=size;
		this.sizeDisplay=rateParser.getHumanReadable(size);
	}

	public void notifyTotalBytesTransferred(long total){
		if(terminal==null || total<0)return;
		updateRate();
		have=total;
		output();
	}

	protected void updateRate(){
		rate=1000*(double)have/(System.currentTimeMillis()-startedAt);
	}

	protected void output(){
		StringBuilder sb=new StringBuilder();
		if(size>0){
			long progress=have*100/size;
			sb.append(String.format("%3d%%  %s ",progress, sizeDisplay));
		}
		else{
			//for unknown size, just display a 'rotating' thingy
			sb.append(x[index]);
			index++;
			if(index==x.length)index=0;
		}

		//append rate
		if(rate>0){
			sb.append(String.format("%sB/s", rateParser.getHumanReadable(rate)));
		}

		//compute maximum with of identifier printout
		int w=getTerminalWidth();
		int max=w-sb.length()-5;
		if(max>0){
			sb.insert(0, String.format("%-"+max+"s ", identifier));
		}

		try {
			reader.getCursorBuffer().clear();
			reader.getCursorBuffer().write(sb.toString());
			reader.setCursorPosition(w);
			reader.redrawLine();
			reader.flush();

		}
		catch (Exception e) {
			logger.error("Could not output to jline console",e);
			terminal = null;
		}
	}

	private int width=0;

	private int getTerminalWidth(){
		if(width==0){
			width=terminal.getWidth();
		}
		return width;
	}

	public boolean isCancelled() {
		return false;
	}

	public void close(){
		if(size>0){
			have=size;
		}
		else{
			setTransferSize(have);
		}
		output();
		System.out.println();
		if(reader != null) reader.shutdown();
	}


}
