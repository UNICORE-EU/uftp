package eu.unicore.uftp.rsync;

import java.io.IOException;
import java.nio.channels.ByteChannel;


/**
 * Master-to-Slave communication
 * 
 * @author schuller
 */
public interface MasterChannel {

	/**
	 * receives checksums from the Slave
	 * 
	 * @return ChecksumHolder - weak and strong checksums
	 * @throws IOException
	 */
	public ChecksumHolder receiveChecksums() throws IOException;
	
	/**
	 * Send data: the given number of data bytes from the input 
	 * stream should be sent to the slave, followed by the index of
	 * the matching block
	 * 
	 * @param bytes - number of bytes to send
	 * @param source - the channel to read from
	 * @param index - the index of a matching block
	 * @throws IOException
	 */
	public void sendData(long bytes, ByteChannel source, int index)throws IOException;
	
	/**
	 * shutdown the channel
	 * @throws IOException
	 */
	public void shutdown()throws IOException;
	
}
