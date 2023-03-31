package eu.unicore.uftp.rsync;

import java.io.IOException;
import java.util.List;

/**
 * @author schuller
 */
public interface FollowerChannel {

	/**
	 * sends the Follower's checksums to the Leader
	 * 
	 * @param weakChecksums
	 * @param strongChecksums
	 * @param blocksize
	 * @throws IOException
	 */
	public void sendToLeader(List<Long>weakChecksums, List<byte[]>strongChecksums, int blocksize)
	throws IOException;
	
	/**
	 * receive a rsync data item (literal diff data plus block reference)
	 * 
	 * @return rysnc data
	 * @throws IOException
	 */
	public RsyncData receive() throws IOException;

}
