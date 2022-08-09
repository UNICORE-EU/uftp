package eu.unicore.uftp.rsync;

import java.nio.channels.ReadableByteChannel;

/**
 * Data item sent from master to slave when a match is found. 
 * Contains literal data and a block reference
 * 
 * @author schuller
 */
public class RsyncData {
	
	//number of bytes of literal data
	final long bytes;
	
	//literal data
	final ReadableByteChannel data;
	
	// block number
	final long blockNumber;
	
	/**
	 * 
	 * @param bytes
	 * @param data
	 * @param blockNumber - if negative, only the literal data is to be used
	 */
	public RsyncData(long bytes, ReadableByteChannel data, long blockNumber){
		this.bytes=bytes;
		this.data=data;
		this.blockNumber=blockNumber;
	}

	public boolean isShutDown(){
		return bytes<0 && blockNumber <0;
	}
}
