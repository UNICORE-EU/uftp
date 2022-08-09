package eu.unicore.uftp.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.dpc.AuthorizationFailureException;
import eu.unicore.uftp.dpc.Reply;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.rsync.Master;
import eu.unicore.uftp.rsync.RsyncStats;
import eu.unicore.uftp.rsync.Slave;
import eu.unicore.uftp.rsync.SocketMasterChannel;
import eu.unicore.uftp.rsync.SocketSlaveChannel;
import eu.unicore.uftp.server.UFTPCommands;

/**
 * Client class for managing a UFTP session. A UFTP session supports multiple
 * read/write operations, directory movement, listing directories etc.
 *
 * @author schuller
 */
public class UFTPSessionClient extends AbstractUFTPClient {

	private static final Logger logger = Utils.getLogger(Utils.LOG_CLIENT, UFTPSessionClient.class);
	 
	protected final byte[] buffer = new byte[BUFFSIZE];
	private String commandFile;
	private File baseDirectory;
	protected boolean keepAlive = false;
	
	protected boolean rfcCompliantRange = false;

	/**
	 * create a new UFTP session client
	 *
	 * @param servers - list of alternative IP addresses of the UFTP server
	 * @param port - the server port
	 */
	public UFTPSessionClient(InetAddress[] servers, int port) {
		super(servers, port);
	}

	public void setCommandFile(String commandFile) {
		if (commandFile == null) {
			throw new IllegalArgumentException("Commandfile is required");
		}
		this.commandFile = commandFile;
	}

	@Override
	public void connect() throws IOException, AuthorizationFailureException {
		super.connect();
		if(getServerFeatures().contains(UFTPCommands.KEEP_ALIVE)) {
			enableKeepAlive();
		}
		if(getServerFeatures().contains(UFTPCommands.FEATURE_RFC_RANG)) {
			rfcCompliantRange = true;
		}
	}

	@Override
	public void run() {
		if (commandFile == null) {
			throw new IllegalArgumentException("Commandfile is required");
		}

		try {
			logger.info("Connecting...");
			connect();
			BufferedReader localReader = new BufferedReader(new FileReader(commandFile));
			try {
				String line;
				do {
					line = localReader.readLine();
					if (line == null) {
						break;
					}
					if (line.trim().isEmpty()) {
						continue;
					}
					List<String> args = Utils.parseLine(line);
					Runnable cmd = SessionCommands.createCMD(args, this);
					if (cmd == null) {
						logger.error("No command for args " + args);
					} else {
						logger.info("Executing "+line);
						cmd.run();
					}
				} while (true);
			} finally {
				Utils.closeQuietly(localReader);
			}
			logger.info("Exiting UFTP session.");
		} catch (AuthorizationFailureException ex) {
			throw new RuntimeException("Authorization error running session", ex);
		} catch (IOException ex) {
			throw new RuntimeException("Error running session", ex);
		}
	}

	public void resetDataConnections() throws IOException {
		if(!keepAlive)return;
		super.closeData();
	}

	@Override
	public void close() throws IOException {
		client.sendControl("QUIT");
		super.close();
	}
	
	/**
	 * get the remote file and write it to the given local stream
	 *
	 * @param remoteFile - the remote path (relative to the current dir)
	 * @param localTarget - the local target stream to write to 
	 * @return number of bytes read
	 * @throws IOException
	 */
	public long get(String remoteFile, OutputStream localTarget)
			throws IOException {
		return get(remoteFile, -1, -1, localTarget);
	}

	/**
	 * partial read: get a chunk of a remote file
	 *
	 * @param remoteFile - the remote file
	 * @param offset - the first byte to read, starting at "0"
	 * @param length - the number of bytes to read
	 * @param localTarget - the local stream to write to
	 * @return number of bytes read
	 * @throws IOException
	 */
	public long get(String remoteFile, long offset, long length, OutputStream localTarget)
			throws IOException {
		checkConnected();
		openDataConnection();
		if(offset>=0 && length>0) sendRangeCommand(offset, length);
		Reply reply = runCommand("RETR " + remoteFile);
		//expect "xxx OK <size> ...", want the size
		long size = Long.parseLong(reply.getStatusLine().split(" ")[2]);
		if(progressListener!=null && progressListener instanceof UFTPProgressListener2){
			((UFTPProgressListener2)progressListener).setTransferSize(size);
		}
		prepareGet(localTarget);
		return moveData(size);
	}

	/**
	 * write data to a remote file
	 *
	 * @param remoteFile - the remote file
	 * @param size - number of bytes to write
	 * @param offset - the offset to start writing the remote file at
	 * @param localSource - the local stream to read from
	 * @return number of bytes written
	 * @throws IOException
	 */
	public long put(String remoteFile, long size, Long offset, InputStream localSource) throws IOException {
		checkConnected();
		openDataConnection();
		sendStoreCommand(remoteFile, size, offset);
		preparePut(localSource);
		return moveData(size);
	}

	/**
	 * write data to a remote file
	 *
	 * @param remoteFile - the remote file
	 * @param size - number of bytes to write
	 * @param localSource - the local stream to read from
	 * @return number of bytes written
	 * @throws IOException
	 */
	public long put(String remoteFile, long size, InputStream localSource) throws IOException {
		return put(remoteFile,size,null,localSource);
	}

	/**
	 * write all the data from the input stream to a remote file
	 *
	 * @param remoteFile - the remote file
	 * @param localSource - the local stream to read from
	 * @return number of bytes written
	 * @throws IOException
	 */
	public long writeAll(String remoteFile, InputStream localSource, boolean closeDataWhenDone) throws IOException {
		checkConnected();
		openDataConnection();
		sendStoreCommand(remoteFile);
		preparePut(localSource);
		return moveData(-1, closeDataWhenDone);
	}
	
	/**
	 * append data to remote file
	 * 
	 * @param remoteFile - the remote file
	 * @param size - number of bytes to write
	 * @param localSource - the local stream to read from
	 * @return number of bytes written
	 * @throws IOException
	 */
	public long append(String remoteFile, long size, InputStream localSource) throws IOException {
		checkConnected();
		openDataConnection();
		if(size>-1) {
			runCommand("ALLO " + size);
		}
		runCommand("APPE " + size);
		preparePut(localSource);
		return moveData(size);
	}
	
	/**
	 * get the raw result of 'ls' for the given basedir (which is relative to
	 * the current dir)
	 *
	 * @param baseDir - the base dir
	 * @return a list of directory entries
	 * @throws IOException
	 */
	public List<String> getFileList(String baseDir) throws IOException {
		checkConnected();
		return sendListFilesCommand(baseDir, true);
	}

	/**
	 * get file information for the given basedir (which is relative to the
	 * current dir)
	 *
	 * @param baseDir - the base dir
	 * @return a list of directory entries
	 * @throws IOException
	 */
	public List<FileInfo> getFileInfoList(String baseDir) throws IOException {
		checkConnected();
		List<FileInfo> res = new ArrayList<FileInfo>();
		for (String ls : sendListFilesCommand(baseDir, true)) {
			res.add(new FileInfo(ls));
		}
		return res;
	}
	
	/**
	 * TODO should use MLST
	 * 
	 * get file information for the given file/dir (which is relative to the
	 * current dir)
	 *
	 * @param path - the path of the file to stat
	 * @return file info 
	 * @throws IOException
	 */
	public FileInfo stat(String path) throws IOException {
		checkConnected();
		String r = sendListFilesCommand(path, false).get(0);
		return new FileInfo(r);
	}

	public long getFileSize(String pathName) throws IOException {
		checkConnected();
		Reply reply = runCommand( "SIZE " + pathName);
		//expect "213 <size> ..."
		reply.assertStatus(213);
		return Long.parseLong(reply.getStatusLine().split(" ")[1]);
	}

	/**
	 * change to the given remote directory
	 *
	 * @param dir - the dir to change to. Can be absolute (starting with a "/") 
	 *              or relative to the current one
	 *
	 * @throws IOException
	 */
	public void cd(String dir) throws IOException {
		checkConnected();
		runCommand("CWD " + dir);
	}

	/**
	 * change to the parent directory
	 *
	 * @throws IOException
	 */
	public void cdUp() throws IOException {
		checkConnected();
		runCommand(UFTPCommands.CDUP);
	}

	/**
	 * create a remote directory
	 *
	 * @param dir - the dir to create, either absolute or relative to the
	 * current one
	 *
	 * @throws IOException
	 */
	public void mkdir(String dir) throws IOException {
		checkConnected();
		runCommand("MKD " + dir);
	}

	/**
	 * delete a remote file/directory
	 *
	 * @param path - the file/dir to delete, either absolute or relative to the
	 * current one
	 *
	 * @throws IOException
	 */
	public void rm(String path) throws IOException {
		checkConnected();
		runCommand("DELE " + path);
	}

	/**
	 * rename / move a remote file/directory
	 *
	 * @param from - the file/dir to rename, either absolute or relative to the current dir
	 * @param to - the target file name, either absolute or relative to the current dir
	 *
	 * @throws IOException
	 */
	public void rename(String from, String to) throws IOException {
		checkConnected();
		runCommand("RNFR " + from);
		runCommand("RNTO " + to);
	}


	/**
	 * set the modification time of a remote file/directory
	 *
	 * @param path - the file/dir to modify
	 * @param to - the desired modification time
	 *
	 * @throws IOException
	 */
	public void setModificationTime(String path, Calendar to) throws IOException {
		checkConnected();
		String time = FileInfo.toTimeVal(to.getTime().getTime());
		runCommand(UFTPCommands.MFMT + " " + time + " " + path);
	}
	
	/**
	 * get the current directory
	 */
	public String pwd() throws IOException {
		checkConnected();
		Reply reply = runCommand("PWD");
		return reply.getStatusLine().split(" ", 2)[1];
	}

	public static final String TYPE_NORMAL = "NORMAL";
	public static final String TYPE_ARCHIVE = "ARCHIVE";

	public void setType(String type) throws IOException{
		checkConnected();
		if(TYPE_ARCHIVE.equalsIgnoreCase(type)) {
			assertFeature(TYPE_ARCHIVE);
		}
		client.sendControl("TYPE "+type);
		Reply reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error: server reply " + reply);
		}
	}

	/**
	 * sync a local file with its up-to-date remote version
	 *
	 * @param remoteMaster - the remote file (up of date)
	 * @param localSlave - the local file (out of date)
	 */
	public RsyncStats syncLocalFile(String remoteMaster, File localSlave) throws Exception {
		runCommand("SYNC-MASTER " + remoteMaster);
		Slave slave = new Slave(localSlave, new SocketSlaveChannel(socket), localSlave.getAbsolutePath());
		return slave.call();
	}

	/**
	 * sync a remote file with its up-to-date local version
	 *
	 * @param localMaster - the local file (up of date)
	 * @param remoteSlave - the remote file (out of date)
	 * @return RsyncStats info about the rsync process
	 */
	public RsyncStats syncRemoteFile(File localMaster, String remoteSlave) throws Exception {
		runCommand("SYNC-SLAVE " + remoteSlave);
		Master master = new Master(localMaster, new SocketMasterChannel(socket), localMaster.getAbsolutePath());
		return master.call();
	}

	/**
	 * set the local base directory
	 *
	 * @param base - the base directory
	 */
	public void setBaseDirectory(File base) {
		if (!base.isDirectory()) {
			throw new IllegalArgumentException("Not a directory: " + base.getPath());
		}
		this.baseDirectory = base;
	}

	public File getBaseDirectory() {
		return baseDirectory;
	}

	public boolean supportsHashes() {
		for(String feat: getServerFeatures()) {
			if(feat.startsWith("HASH"))return true;
		}
		return false;
	}

	public String getHashAlgorithm() throws IOException {
		checkConnected();
		client.sendControl("OPTS HASH");
		Reply reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error: server reply " + reply);
		}
		else {
			return reply.toString().split("200 ")[1];
		}
	}

	public String setHashAlgorithm(String algo) throws IOException {
		checkConnected();
		Reply reply = runCommand("OPTS HASH " + algo);
		return reply.getStatusLine().split("200 ")[1];
	}

	public HashInfo getHash(String file) throws IOException {
		return getHash(file, 0, -1);
	}

	public HashInfo getHash(String remoteFile, long offset, long length) throws IOException {
		checkConnected();
		if(offset>=0 && length>0) sendRangeCommand(offset, length);
		Reply reply = runCommand("HASH " + remoteFile);
		String[] tokens = reply.getStatusLine().split(" ", 5);
		String algo = tokens[1];
		String[] range = tokens[2].split("-");
		long first = Long.parseLong(range[0]);
		long last = Long.parseLong(range[1]);
		String hash = tokens[3];
		String path = tokens[4];
		return new HashInfo(path, hash, algo, first, last);
	}

	public static class HashInfo {
		public String algorithm;
		public long firstByte; // start byte
		public long lastByte;  // including this byte
		public String hash;
		public String path;

		public HashInfo(String path, String  hash, String algorithm, long firstByte, long lastByte) {
			this.path = path;
			this.hash = hash;
			this.algorithm = algorithm;
			this.firstByte = firstByte;
			this.lastByte = lastByte;
		}

		public String fullInfo() {
			return algorithm+" "+firstByte+"-"+lastByte+" "+hash+" "+path;
		}

		public String toString() {
			return hash;
		}
	}

	protected long moveData(long maxBytes) throws IOException {
		return moveData(maxBytes, maxBytes<0);
	}

	protected long moveData(long maxBytes, boolean forceCloseData) throws IOException {
		long time = System.currentTimeMillis();
		boolean controlRate = bandwidthLimit>0;
		int n;
		boolean notify = progressListener != null;
		boolean streamingMode = maxBytes<0;
		long c = 0;
		long want = 0;
		long remaining = maxBytes;
		long total = 0;
		while (streamingMode || remaining > 0 && !cancelled) {
			want = streamingMode? buffer.length : (buffer.length > remaining ? remaining : buffer.length);  
			n = reader.read(buffer,0,(int)want);
			if (n < 0) {
				break;
			}
			remaining -= n;
			total += n;
			writer.write(buffer, 0, n);
			if (notify) {
				if (c % 200 == 0) {
					progressListener.notifyTotalBytesTransferred(total);
				}
				c++;
			}
			if (controlRate) {
				try{
					controlRate(total, time);
				}catch(Exception ex) {}
			}
		}
		writer.flush();
		
		Utils.finishWriting(writer);
	    
		if (notify) {
			progressListener.notifyTotalBytesTransferred(total);
		}
		time = System.currentTimeMillis() - time;
		double rate = (double) total / (double) time;
		logger.info("Finished, data rate " + (int) rate + " kB/sec. (" + total + " bytes in " + time + " ms.)");
		if(cancelled){
			logger.info("Operation was cancelled.");
			cancelled = false;
		}
		boolean closed = false;
		if(remaining>0 || streamingMode || forceCloseData || !keepAlive) {
			logger.debug("Closing data connection.");
			closeData();
			closed = true;
		}
		if(closed || !streamingMode)client.readControl();
		return total;
	}
	

	//sleep time to bring down rate
	private long sleepTime = 0;

	/**
	 * calculate current transfer rate and if necessary slow down a bit by
	 * waiting
	 *
	 * @param total - total bytes transferred
	 * @param startTime - start time of the transfer
	 */
	protected void controlRate(long total, long startTime) throws InterruptedException {
		//all rates are bytes/second

		// this is not intended to be high-precision
		long interval = System.currentTimeMillis() - startTime;
		if (interval ==0) interval = 1;
		long rate = 1000 * total / interval;
		if (rate < bandwidthLimit) {
			//decrease sleep time
			sleepTime = sleepTime / 2;
			return;
		}
		//else increase sleep time and wait
		sleepTime += 5;
		Thread.sleep(sleepTime);
	}

	private Reply runCommand(String command) throws IOException {
		client.sendControl(command);
		Reply reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error: server reply " + reply);
		}
		return reply;
	}

	//
	// send "RANG <start> <end>" 
	// http://tools.ietf.org/html/draft-bryan-ftp-range-05
	//
	private void sendRangeCommand(long offset, long length) throws IOException {
		long endByte = rfcCompliantRange ? offset+length-1 : offset+length;
		String message = "RANG " + offset + " " + endByte;
		Reply reply = runCommand(message);
		reply.assertStatus(350);
	}

	/**
	 * @param options - option string : 'N' for normal mode (list a directory)
	 * @param baseDir
	 */
	private List<String> sendListFilesCommand(String baseDir, boolean dir) throws IOException {
		if (baseDir == null) {
			baseDir = ".";
		}
		Reply reply = runCommand("STAT " + (dir? "N ": "F ") + baseDir);
		return reply.getResults();
	}

	private void sendStoreCommand(String remoteFile, long size, Long offset) throws IOException {
		if(offset!=null){
			sendRangeCommand(offset, size);
		}
		if(size>-1) {
			runCommand("ALLO " + size);
		}
		runCommand("STOR " + remoteFile);
	}
	
	private void sendStoreCommand(String remoteFile) throws IOException {
		sendStoreCommand(remoteFile, -1, null);
	}

	private void enableKeepAlive() throws IOException {
		if(Boolean.parseBoolean(Utils.getProperty("UFTP_DISABLE_KEEPALIVE", "false"))) {
			logger.debug("KEEP-ALIVE disabled via environment");
			return;
		};
		if(numcons>1 || key!=null) {
			logger.debug("KEEP-ALIVE disabled (numstreams/encryption)");
			return;
		}
		Reply reply = runCommand(UFTPCommands.KEEP_ALIVE + " true");
		keepAlive = reply.isOK();
	}

	private void checkConnected() throws IOException {
		if (!isConnected()) {
			throw new IOException("Not connected!");
		}
	}

	public void setRFCRangeMode(boolean rfcMode){
		this.rfcCompliantRange = rfcMode;
	}

	/**
	 * create a correct commandline for passing the given parameters to a
	 * UFTPSessionClient
	 *
	 * @param host - server host (can be a comma separated list of addresses)
	 * @param port - server port
	 * @param baseDirectory - local base directory
	 * @param secret - the secret
	 * @param numStreams - the number of streams
	 * @param encryptionKey - encryption key (base64), if <code>null</code>, no
	 * encryption will be used
	 * @param bufferSize - size of the buffer
	 * @param compress - whether to enable gzip compression
	 * @param commandFile - file to read commands from
	 */
	public static String makeCommandline(String host, int port,
			String baseDirectory,
			String secret,
			int numStreams,
			String encryptionKey,
			int bufferSize,
			boolean compress,
			String commandFile) {
		StringBuilder sb = new StringBuilder();
		sb.append("-l ").append(host);
		sb.append(" -L ").append(port);
		sb.append(" -f \"").append(baseDirectory).append("\"");
		sb.append(" -x ").append(secret);
		sb.append(" -n ").append(numStreams);
		if (encryptionKey != null) {
			sb.append(" -E ");
			sb.append(encryptionKey);
		}
		if (commandFile != null) {
			sb.append(" -c \"").append(commandFile).append("\"");
		}
		if (bufferSize > 0) {
			sb.append(" -b ").append(bufferSize);
		}
		if(compress){
			sb.append(" -z");
		}
		return sb.toString();
	}

	/**
	 * create a session client instance from the supplied commandline parameters
	 */
	public static UFTPSessionClient create(String[] args) throws UnknownHostException, FileNotFoundException {
		Options options = ClientFactory.createOptions();
		CommandLineParser parser = new GnuParser();
		CommandLine line = null;
		try {
			line = parser.parse(options, args);
		} catch (ParseException pe) {
			System.out.println("Unable to parse options: " + pe.getLocalizedMessage());
			ClientFactory.printUsage(options);
			System.exit(SYNERR);
		}

		// setup connection parameters
		int port = Integer.parseInt(line.getOptionValue('L'));
		InetAddress[] server = ClientFactory.getServers(line, logger);


		UFTPSessionClient client = new UFTPSessionClient(server, port);
		ClientFactory.configureClient(client, line, logger);
		client.setCommandFile(line.getOptionValue('c'));
		String base = line.getOptionValue('f');
		if (base == null) {
			base = ".";
		}
		client.setBaseDirectory(new File(base));
		logger.info("New UFTP session client for server " + Arrays.asList(server) + " at port " + port);
		return client;
	}

}
