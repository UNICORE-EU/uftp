package eu.unicore.uftp.standalone.util;

/**
 * How to handle ranges
 * 
 * @author schuller
 */
public enum RangeMode {

	/**
	 * the range is used when reading data (from a local or remote source)
	 */
	READ, 
	
	/**
	 * the range is used for both reading and writing data
	 */
	READ_WRITE
}
