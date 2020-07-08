package eu.unicore.uftp.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import eu.unicore.uftp.dpc.ProtocolViolationException;
import eu.unicore.uftp.dpc.Utils;

/**
 * Client class for handling single-file UFTP transfers.
 *
 * @author Tim Pohlmann, 2010, Forschungszentrum Juelich - JSC
 * @author schuller
 */
public class UFTPClient extends AbstractUFTPClient {

    private static final Logger logger = Utils.getLogger(Utils.LOG_CLIENT, UFTPClient.class);

    private final InputStream fileReadStream;

    private final OutputStream fileWriteStream;

    long maxBytes = Long.MAX_VALUE;

    private final boolean send;

    //transfer rate in bytes per second
    private long finalTransferRate;

    /**
     * create a new UFTP client for file transfer from/to the given server
     *
     * @param servers - list of alternative IP addresses of the UFTP server
     * @param port - the server port
     * @param fileName - the local file name
     * @param send - <code>true</code> if data should be sent
     * @param append - <code>true</code> if data (in receive mode) should be
     * appended to the local file
     * @throws FileNotFoundException
     */
    public UFTPClient(InetAddress[] servers, int port, String fileName, boolean send, boolean append) throws FileNotFoundException {
        super(servers, port);
        this.send = send;
        File file = new File(fileName);
        if (send) {
            //special case for performance measurements: 
            //check if it is some /dev/ file
            String realFileName = file.getAbsolutePath();
            if (realFileName.startsWith("/dev/") && realFileName.contains("_")) {
                String[] s = realFileName.split("_");
                maxBytes = Long.parseLong(s[1]);
            } else {
                maxBytes = file.length();
            }
            logger.info("Sending " + realFileName + " length " + maxBytes);
            fileReadStream = new FileInputStream(realFileName);
            fileWriteStream = null;
        } else {
            logger.info("Writing to " + file.getAbsolutePath());
            fileWriteStream = new FileOutputStream(file, append);
            fileReadStream = null;
        }
    }

    /**
     * create a new UFTP client for writing data to the given server, which is
     * read from the specified source
     *
     * @param servers - list of alternative IP addresses of the UFTP server
     * @param port - UFTP port
     * @param source - input stream for reading data from
     * @throws IOException
     */
    public UFTPClient(InetAddress[] servers, int port, InputStream source) throws IOException {
        super(servers, port);
        this.send = true;
        fileReadStream = source;
        fileWriteStream = null;
    }

    /**
     * create a new UFTP client for reading data from the given server and
     * writing it to the specified target
     *
     * @param servers - list of alternative IP addresses of the UFTP server
     * @param port - UFTP port
     * @param target - output stream for writing data to
     * @throws IOException
     */
    public UFTPClient(InetAddress[] servers, int port, OutputStream target) throws IOException {
        super(servers, port);
        this.send = false;
        fileReadStream = null;
        fileWriteStream = target;
    }

    @Override
    public void run() {

        try {
            logger.info("Connecting...");
            connect();

            if (send) {
                preparePut(fileReadStream);
            } else {
                prepareGet(fileWriteStream);
            }

            byte[] buffer = new byte[BUFFSIZE];

            long time = System.currentTimeMillis();

            int n;
            long total = 0;
            boolean notify = progressListener != null;
            long c = 0;
            while (total < maxBytes && !cancelled) {
                n = reader.read(buffer);
                if (n < 0) {
                    break;
                }
                total += n;
                writer.write(buffer, 0, n);
                if (notify) {
                    if (c % 100 == 0) {
                        progressListener.notifyTotalBytesTransferred(total);
                    }
                    c++;
                }
            }
            writer.flush();
            if (notify) {
                progressListener.notifyTotalBytesTransferred(total);
            }
            time = System.currentTimeMillis() - time;
            long rate = (long) ((double) total / (double) time);
            logger.info("Finished, data rate " + rate + " kB/sec. (" + total + " bytes in " + time + " ms.)");
            finalTransferRate = rate * 1000; // bytes per sec
            if(cancelled){
            	logger.info("Operation was cancelled.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error running transfer", e);
        }
        Utils.closeQuietly(this);
    }

    /**
     * after the transfer has finished, this will reflect the net transfer rate
     * in bytes per second
     *
     * @return transfer rate
     */
    public long getFinalTransferRate() {
        return finalTransferRate;
    }

    /**
     * runs a UFTP data transfer
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        try {
            AbstractUFTPClient client = ClientFactory.create(args);
            client.run();
            System.exit(0);
        } catch (Exception ex) {
            String msg = Utils.createFaultMessage("Error running UFTP client", ex);
            logger.error(msg,ex);
            System.err.println(msg);
            if (ex.getCause() instanceof ProtocolViolationException) {
                System.err.println();
                System.err.println("Please check hostname and port of the remote server!");
            }
            System.exit(1);
        }
    }

    static UFTPClient create(String[] args) throws UnknownHostException, FileNotFoundException {
        Options options = ClientFactory.createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine line = null;
        try {
            line = parser.parse(options, args);
        } catch (ParseException pe) {
            pe.printStackTrace();
            System.out.println();
            ClientFactory.printUsage(options);
            System.exit(SYNERR);
        }

        boolean send = ClientFactory.isSend(line);
        String file = line.getOptionValue('f');
        boolean append = Boolean.parseBoolean(line.getOptionValue('a'));

        // setup connection parameters
        int port = Integer.parseInt(line.getOptionValue("L"));
        InetAddress[] server = ClientFactory.getServers(line, logger);

        UFTPClient client = new UFTPClient(server, port, file, send, append);
        ClientFactory.configureClient(client, line, logger);
        logger.info("New UFTP client for server " + Arrays.asList(server) + " at port " + port);
        return client;
    }

    

    /**
     * create a correct commandline for passing the given parameters to a
     * UFTPClient
     *
     * @param host - server host (can be a comma separated list of addresses)
     * @param port - server port
     * @param localFile - local file name
     * @param sendData - <code>true</code> if client should send data
     * @param secret - the secret
     * @param numStreams - the number of streams
     * @param encryptionKey - encryption key (base64), if <code>null</code>, no
     * encryption will be used
     * @param append - whether to append to an existing file
     * @param bufferSize - file read/write buffer in kbytes (default 128)
     */
    public static String makeCommandline(String host, int port,
            String localFile,
            boolean sendData,
            String secret,
            int numStreams,
            String encryptionKey,
            boolean append,
            boolean compress,
            int bufferSize
    ) {
        String c1 = makeCommandline(host, port, localFile, sendData, secret, numStreams, encryptionKey, append, compress);
        return c1 + " -b " + bufferSize;
    }

    /**
     * create a correct commandline for passing the given parameters to a
     * UFTPClient
     *
     * @param host - server host (can be a comma separated list of addresses)
     * @param port - server port
     * @param localFile - local file name
     * @param sendData - <code>true</code> if client should send data
     * @param secret - the secret
     * @param numStreams - the number of streams
     * @param encryptionKey - encryption key (base64), if <code>null</code>, no
     * encryption will be used
     * @param append - whether to append to an existing file
     */
    public static String makeCommandline(String host, int port,
            String localFile,
            boolean sendData,
            String secret,
            int numStreams,
            String encryptionKey,
            boolean append,
            boolean compress
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("-l ").append(host);
        sb.append(" -L ").append(port);
        sb.append(" -f \"").append(localFile).append("\"");
        if (sendData) {
            sb.append(" -s");
        } else {
            sb.append(" -r");
        }
        sb.append(" -x ").append(secret);
        sb.append(" -n ").append(numStreams);
        if (encryptionKey != null) {
            sb.append(" -E ");
            sb.append(encryptionKey);
        }
        if (append) {
            sb.append(" -a");
        }
        if (compress) {
            sb.append(" -z");
        }

        return sb.toString();
    }

}
