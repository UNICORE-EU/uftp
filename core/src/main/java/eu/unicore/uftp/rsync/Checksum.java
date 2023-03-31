package eu.unicore.uftp.rsync;

import java.util.List;

/**
 * Helper class for dealing with the checksums used in rsync <br/>
 * 
 * Based on Andrew Tridgell's 'rsync' as described in  
 * https://www.andrew.cmu.edu/course/15-749/READINGS/required/cas/tridgell96.pdf
 *
 * @author schuller
 */
public class Checksum {

	/**
	 * the function "a" from the rsync rolling checksum algorithm
	 * @param block
	 */
	public static long a(byte[]block){
		long sum=0;
		for(byte b: block){
			sum+=(b & 0xFF);
		}
		return sum & 0xFFFF ;
	}
	
	/**
	 * the function "b" from the rsync algorithm
	 * @param block
	 * @param k - start
	 * @param l - finish
	 */
	public static long b(byte[]block, long k, long l){
		long sum=0;
		long i=k;
		for(int b: block){
			sum+=(l-i+1)*(b & 0xFF);
			i++;
		}
		return sum & 0xFFFF;
	}
	
	public static long sum(long a, long b){
		return a + (b << 16) ;
	}

	/**
	 * computes the (weak) checksum for the given data block
	 * 
	 * @param block - data
	 * @param start - start index of the block in the complete data
	 * @param finish - end index of the block in the complete data
	 * @return checksum
	 */
	public static long checksum(byte[]block, long start, long finish){
		return sum( a(block), b(block, start, finish) ); 
	}

	public static class ChecksumHolder {

		int blocksize;
		
		List<Long> weakChecksums;
		
		List<byte[]> strongChecksums;
		
	}
	
	public static class BlockReference {
		
		final int index;
	
		final byte[] strongChecksum;
		
		public BlockReference(int index, byte[]strongChecksum) {
			this.index = index;
			this.strongChecksum = strongChecksum;
		}
	}

}
