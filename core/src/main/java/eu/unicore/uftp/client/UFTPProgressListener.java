package eu.unicore.uftp.client;

public interface UFTPProgressListener {

	/**
	 * update the number of total bytes transferred
	 * @param totalBytesTransferred
	 */
	public void notifyTotalBytesTransferred(long totalBytesTransferred);
	
}
