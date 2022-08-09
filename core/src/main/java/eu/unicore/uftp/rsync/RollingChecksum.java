package eu.unicore.uftp.rsync;

/**
 * Compute the rolling checksum.<br/>
 *  
 * Based on Andrew Tridgell's 'rsync' as described in  
 * http://cs.anu.edu.au/techreports/1996/TR-CS-96-05.pdf
 * 
 * @author schuller
 */
public class RollingChecksum {

	//last data value
	private long lastX;
	
	//last function values
	private long lastA;
	private long lastB;
	
	//last index values for consistency checking
	private long lastK;
	private long lastL;
	
	/**
	 * initialise the checksum
	 * 
	 * @param data - initial data block
	 */
	public long init(byte[] data){
		return reset(data, 0, data.length-1);
	}
	
	/**
	 * reset the checksum using the given block data
	 * 
	 * @param data - data block
	 * @param k - start position
	 * @param l - end position
	 * @return the checksum of the given block
	 */
	public long reset(byte[] data, long k, long l){
		lastX=data[0] & 0xFF ;
		lastK=k;
		lastL=l;
		lastA=Checksum.a(data);
		lastB=Checksum.b(data, k, l);
		
		return Checksum.sum(lastA,lastB);
	}

	/**
	 * compute the rolling checksum for the given parameters
	 * 
	 * @param k - start index
	 * @param l - end index
	 * @param X_k - the data value at position 'k'
	 * @param X_l - the data value at position 'l'
	 * @return checksum value for the block from 'k' to 'l'
	 */
	public long update(long k, long l, byte X_k, byte X_l){
		if(k==0 || k!=lastK+1 || l!=lastL+1){
			throw new IllegalStateException();
		}
		//recurrence relations
		lastA=( lastA - lastX + (X_l & 0xFF) ) & 0xFFFF ;
		lastB=( lastB - (l-k+1)*lastX + lastA ) & 0xFFFF ;
		lastK++;
		lastL++;
		lastX =  X_k & 0xFF ;
		
		return Checksum.sum(lastA,lastB);
	}

}
