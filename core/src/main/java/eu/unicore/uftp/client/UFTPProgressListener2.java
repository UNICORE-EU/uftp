package eu.unicore.uftp.client;

public interface UFTPProgressListener2 extends UFTPProgressListener{

	/**
	 * set the number of total bytes to transfer
	 * @param transferSize
	 */
	public void setTransferSize(long transferSize);

}
