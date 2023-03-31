package eu.unicore.uftp.rsync;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import eu.unicore.uftp.rsync.Checksum.BlockReference;
import eu.unicore.uftp.rsync.Checksum.ChecksumHolder;

/**
 * the Leader has an up-to-date copy of a file which should be synchronized with
 * the Follower copy. <br/>
 *
 * Based on Andrew Tridgell's 'rsync' as described in
 * http://cs.anu.edu.au/techreports/1996/TR-CS-96-05.pdf
 *
 * @author schuller
 */
public class Leader implements Callable<RsyncStats> {

    private final RandomAccessFile file;

    private final LeaderChannel channel;

    private final RsyncStats stats;

    // we get this from the follower
    private int blockSize;

    private final Map<Long, List<BlockReference>> checksums = new HashMap<>();
    
    public Leader(File file, LeaderChannel channel, String fileName) throws IOException {
        this(new RandomAccessFile(file, "r"), channel, fileName);
    }

    /**
     * Construct a new Leader using the given file
     *
     * @param file
     * @param channel
     * @param fileName
     */
    public Leader(RandomAccessFile file, LeaderChannel channel, String fileName) {
        this.file = file;
        this.channel = channel;
        stats = new RsyncStats(fileName);
    }

    @Override
    public RsyncStats call() throws Exception {
        long start = System.currentTimeMillis();
        ChecksumHolder fromFollower = channel.receiveChecksums();
        blockSize = fromFollower.blocksize;
        stats.blocksize = blockSize;
        stats.blocks = fromFollower.weakChecksums.size();
        for(int index = 0; index<fromFollower.weakChecksums.size(); index++) {
			long weakCS = fromFollower.weakChecksums.get(index);
			List<BlockReference> ref = checksums.get(weakCS);
			if(ref==null) {
				ref = new ArrayList<>();
				checksums.put(weakCS, ref);
			}
			ref.add(new BlockReference(index, fromFollower.strongChecksums.get(index)));
		}
        findMatches();
        stats.duration = System.currentTimeMillis() - start;
        return stats;
    }

    protected void findMatches() throws IOException, NoSuchAlgorithmException {
    	RollingChecksum rollingChecksum = new RollingChecksum();
    	MessageDigest md = MessageDigest.getInstance("MD5");
    	stats.matches = 0;
        stats.weakMatches = 0;
        stats.misses = 0;
        long total = file.length();
        long endOfLastMatch = 0;
        long k = 0;
        long l = blockSize - 1;
        byte[] block = new byte[blockSize];
        boolean blockInvalid = false;
        boolean missCounted = false;
        
        int read = file.read(block);
        if (total < blockSize || read < blockSize) {
        	// send all the data and finish
        	file.seek(0);
            channel.sendData(total, file.getChannel(), -1);
            stats.transferred += total;
            return;
        }
        Long checkSum = rollingChecksum.init(block);
     	while (l < total) {
     		int index=-1;
            // two level check
            List<BlockReference> refs = checksums.get(checkSum);
            if(refs!=null) {
            	stats.weakMatches++;
                if(blockInvalid) {
            		file.seek(k);
                	file.read(block);
                	blockInvalid = false;
                }
                md.reset();
                byte[] strongChecksum = md.digest(block);
                for(BlockReference br: refs) {
                	if(Arrays.equals(strongChecksum, br.strongChecksum)){
                		index = br.index;
                		break;
                	}
                }
            }
            if (index>=0) {
            	stats.matches++;
            	missCounted = false;
            	long numBytes = k - endOfLastMatch;
                if(numBytes>0)file.seek(endOfLastMatch);
                // send literal data and index of matching block
                channel.sendData(numBytes, file.getChannel(), index);
                stats.transferred += numBytes;
                // reset search to the end of the match
                endOfLastMatch = l+1;
                k += blockSize;
                if(numBytes>0)file.seek(k);
                l = k-1+file.read(block);
                _avail = 0;
                checkSum = rollingChecksum.reset(block, k, l);
            } else if (l<(total-blockSize)){
            	if(!missCounted) {
            		missCounted = true;
            		stats.misses++;
            	}
            	// start byte-wise checksum update
            	blockInvalid = true;
            	k++;
                l++;
                checkSum = rollingChecksum.update(read0());
            }
            else {
            	break;
            }
        }
        // finally, send any remaining data
        long numBytes = total - endOfLastMatch;
        if(numBytes>0)file.seek(endOfLastMatch);
        channel.sendData(numBytes, file.getChannel(), -1);
        stats.transferred += numBytes;
    }

    // Buffer those single-byte reads... is there a smarter way?
    byte[] _buf = new byte[0];
    int _index = 0;
    int _avail = 0;
    int refills = 0;
    private byte read0() throws IOException {
    	byte res = -1;
    	if(_index>=_avail) {
    		_buf = new byte[Math.max(2*blockSize, 32768)];
    		_avail = file.read(_buf);
    		if(_avail<0) {
               throw new IOException("EOF");
    		}
        	_index = 0;
    		refills++;
    	}
    	res =_buf[_index];
    	_index++;
    	return res;
    }

}
