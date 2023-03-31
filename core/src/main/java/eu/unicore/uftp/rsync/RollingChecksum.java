package eu.unicore.uftp.rsync;

import java.util.Arrays;

/**
 * Compute the rolling checksum.<br/>
 *  
 * Based on Andrew Tridgell's 'rsync' as described in  
 * https://www.andrew.cmu.edu/course/15-749/READINGS/required/cas/tridgell96.pdf
 *
 * @author schuller
 */
public class RollingChecksum {

	//last data value at position 'k'
	private int Xk;
	
	//last function values
	private long A_kl;
	private long B_kl;
	
	//last index values
	private long k;
	private long l;
	
	private byte[] data;
	private int position=0;

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
	 * @param k . start index of the block in the full data
	 * @param l - end index of the block in the full data
	 * @return the checksum of the given block
	 */
	public long reset(byte[] data, long k, long l){
		this.k = k;
		this.l = l;
		A_kl = Checksum.a(data);
		B_kl = Checksum.b(data, k, l);
		this.data = Arrays.copyOf(data, data.length);
		this.Xk = data[0] & 0xFF;
		this.position = 0;
		return Checksum.sum(A_kl, B_kl);
	}

	/**
	 * update the rolling checksum with the next byte of data
	 * 
	 * @param Xk     - the data value at position 'k'
	 * @param nextX - the data value at position 'l+1'
	 * @return checksum value for the block from 'k+1' to 'l+1'
	 */
	public long update(byte nextX){
		// recurrence relations
		long A = ( A_kl - Xk + (nextX & 0xff)) & 0xFFFF ;
		long B = ( B_kl - (l-k+1)*Xk + A ) & 0xFFFF ;
		// store values for the next update()
		k++;
		l++;
		A_kl=A;
		B_kl=B;
		data[position] = nextX;
		position++;
		if(position==data.length)position=0;
		Xk =  data[position] & 0xFF;
		return Checksum.sum(A, B);
	}

	public long getK() {
		return k;
	}
	public long getL() {
		return l;
	}
}
