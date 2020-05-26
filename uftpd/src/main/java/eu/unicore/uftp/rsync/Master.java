package eu.unicore.uftp.rsync;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * the Master has an up-to-date copy of a file which should be synchronized with
 * the slave copy. <br/>
 *
 * Based on Andrew Tridgell's 'rsync' as described in
 * http://cs.anu.edu.au/techreports/1996/TR-CS-96-05.pdf
 *
 * @author schuller
 */
public class Master implements Callable<RsyncStats> {

    private final RandomAccessFile file;

    private final MasterChannel channel;

    private final RsyncStats stats;

    // we get this from the slave
    private int blockSize;

    private List<Long> weakChecksums;

    private List<byte[]> strongChecksums;

    public Master(File file, MasterChannel channel, String fileName) throws IOException {
        this(new RandomAccessFile(file, "r"), channel, fileName);
    }

    /**
     * Construct a new Master using the given file
     *
     * @param file
     * @param channel
     * @param fileName
     */
    public Master(RandomAccessFile file, MasterChannel channel, String fileName) {
        this.file = file;
        this.channel = channel;
        stats = new RsyncStats(fileName);
    }

    @Override
    public RsyncStats call() throws Exception {
        long start = System.currentTimeMillis();
        ChecksumHolder checksums = channel.receiveChecksums();
        blockSize = checksums.blocksize;
        stats.blocksize = blockSize;
        weakChecksums = checksums.weakChecksums;
        strongChecksums = checksums.strongChecksums;
        findMatches();
        stats.duration = System.currentTimeMillis() - start;
        return stats;
    }

    protected void findMatches() throws IOException {
        RollingChecksum rollingChecksum = new RollingChecksum();
        long indexOfLastMatch = 0;
        long total = file.length();
        long k = 0;
        long l = blockSize - 1;
        stats.matches = 0;
        stats.weakMatches = 0;

        byte[] block = new byte[blockSize];
       
        int read = file.read(block);
        if (total < blockSize || read < blockSize) {
        	// send all the data and finish
        	file.seek(0);
            channel.sendData(total, file.getChannel(), -1);
            stats.transferred += total;
            channel.shutdown();
            return;
        }
        
        long checkSum;
        checkSum = rollingChecksum.init(block);

        while (l <= total) {
            boolean foundMatch = false;
            // two level check
            int index = weakCheck(checkSum);
            if (index >= 0) {
                stats.weakMatches++;
                byte[] strong = Checksum.md5(block);
                if (Arrays.equals(strong, strongChecksums.get(index))) {
                    foundMatch = true;
                }
            }

            if (foundMatch) {
                stats.matches++;
                long numBytes = k - indexOfLastMatch;
                file.seek(indexOfLastMatch);
                // send literal data and index of matching block
                channel.sendData(numBytes, file.getChannel(), index);
                stats.transferred += numBytes;
                // reset search to the end of the match
                k += blockSize;
                l += blockSize;
                indexOfLastMatch = k;
                file.seek(k);
                if (l <= total) {
                    file.read(block);
                    checkSum = rollingChecksum.reset(block, k, l);
                }
            } else {
                // set read position to next block start
                k++;
                file.seek(k);
                l++;
                // and compute the next checksum
                file.read(block);
                checkSum = rollingChecksum.update(k, l, block[0], block[blockSize - 1]);
            }
        }
        // finally need to send any remaining data
        if (indexOfLastMatch < total) {
            long numBytes = total - indexOfLastMatch;
            file.seek(indexOfLastMatch);
            channel.sendData(numBytes, file.getChannel(), -1);
            stats.transferred += numBytes;
        }
        channel.shutdown();
    }

    /**
     * checks whether one of the weak checksums match
     *
     * @param checkSum
     * @return block index or -1 if no match was found
     */
    private int weakCheck(long checkSum) {
        int index = 0;
        for (Long c : weakChecksums) {
            if (checkSum == c) {
                return index;
            }
            index++;
        }
        return -1;
    }
}
