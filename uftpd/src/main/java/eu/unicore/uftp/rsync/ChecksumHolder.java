package eu.unicore.uftp.rsync;

import java.util.List;

public class ChecksumHolder {

	int blocksize;
	
	List<Long> weakChecksums;
	
	List<byte[]> strongChecksums;
	
}
