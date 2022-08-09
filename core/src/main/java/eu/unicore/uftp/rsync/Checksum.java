package eu.unicore.uftp.rsync;

import java.security.MessageDigest;

/**
 * Helper class for dealing with the checksums used in rsync <br/>
 * 
 * Based on Andrew Tridgell's 'rsync' as described in  
 * http://cs.anu.edu.au/techreports/1996/TR-CS-96-05.pdf
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
		for(byte b: block){
			sum+=(l-i+1)*(b & 0xFF);
			i++;
		}
		return sum & 0xFFFF;
	}
	
	public static long sum(long a, long b){
		return a + b << 16 ;
	}

	/**
	 * compute the checksum for the given data block
	 * 
	 * @param block - data
	 * @param start - start index of the block in the complete data
	 * @param finish - end index of the block in the complete data
	 * @return checksum
	 */
	public static long checksum(byte[]block, long start, long finish){
		return sum( a(block), b(block, start, finish) ); 
	}
	
	private static MessageDigest md;
	static{
		try{
			md=MessageDigest.getInstance("MD5");
		}
		catch(Exception ex){
			throw new RuntimeException(ex);
		}
	}
	
	public static byte[] md5(byte[]block){
		md.reset();
		md.update(block);
		return md.digest();
	}

}
