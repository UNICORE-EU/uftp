package eu.unicore.uftp.rsync;

import java.io.IOException;
import java.util.List;

/**
 * Slave-to-Master communication
 * 
 * @author schuller
 */
public interface SlaveChannel {

	/**
	 * sends the Slave's checksums to the Master
	 * 
	 * @param weakChecksums
	 * @param strongChecksums
	 * @param blocksize
	 * @throws IOException
	 */
	public void sendToMaster(List<Long>weakChecksums, List<byte[]>strongChecksums, int blocksize)
	throws IOException;
	
	/**
	 * receive a rsync data item (literal diff data plus block reference)
	 * 
	 * @return rysnc data
	 * @throws IOException
	 */
	public RsyncData receive() throws IOException;

}
