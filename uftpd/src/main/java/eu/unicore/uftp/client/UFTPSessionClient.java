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
import org.apache.log4j.Logger;

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
	 
	private final byte[] buffer = new byte[BUFFSIZE];
	private String commandFile;
	private File baseDirectory;
	private boolean keepAlive = false;

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
		client.sendControl("BYE");
		super.close();
	}
	
	/**
	 * get the remote file and write it to the given local stream
	 *
	 * @param remoteFile - the remote path (relative to the current dir)
	 * @param localTarget - the local target stream to write to
	 * @throws IOException
	 */
	public void get(String remoteFile, OutputStream localTarget)
			throws IOException {
		checkConnected();
		openDataConnection();
		long size = sendRetrieveCommand(remoteFile);
		if(progressListener!=null && progressListener instanceof UFTPProgressListener2){
			((UFTPProgressListener2)progressListener).setTransferSize(size);
		}
		prepareGet(localTarget);
		moveData(size);
	}

	/**
	 * partial read: get a chunk of a remote file
	 *
	 * @param remoteFile - the remote file
	 * @param offset - the first byte to read, starting at "0"
	 * @param length - the number of bytes to read
	 * @param localTarget - the local stream to write to
	 * @return actual number of bytes read
	 * @throws IOException
	 */
	public long get(String remoteFile, long offset, long length, OutputStream localTarget)
			throws IOException {
		checkConnected();
		openDataConnection();
		sendRangeCommand(offset, length);
		long size = sendRetrieveCommand(remoteFile);
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
	 * @return the actual number of bytes read
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
	 * @throws IOException
	 */
	public void put(String remoteFile, long size, InputStream localSource) throws IOException {
		put(remoteFile,size,null,localSource);
	}

	/**
	 * write all the data from the input stream to a remote file
	 *
	 * @param remoteFile - the remote file
	 * @param localSource - the local stream to read from
	 * @throws IOException
	 */
	public void writeAll(String remoteFile, InputStream localSource, boolean closeDataWhenDone) throws IOException {
		checkConnected();
		openDataConnection();
		sendStoreCommand(remoteFile);
		preparePut(localSource);
		moveData(-1, closeDataWhenDone);
	}
	
	/**
	 * append data to remote file
	 * 
	 * @param remoteFile - the remote file
	 * @param size - number of bytes to write
	 * @param localSource - the local stream to read from
	 * 
	 * @throws IOException
	 */
	public long append(String remoteFile, long size, InputStream localSource) throws IOException {
		checkConnected();
		openDataConnection();
		sendAppendCommand(remoteFile, size);
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
		return sendListFilesCommand("N", baseDir);
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
		List<FileInfo> res = new ArrayList<FileInfo>();
		for (String ls : getFileList(baseDir)) {
			res.add(new FileInfo(ls));
		}
		return res;
	}
	
	/**
	 * get file information for the given file/dir (which is relative to the
	 * current dir)
	 *
	 * @param path - the path of the file to stat
	 * @return file info 
	 * @throws IOException
	 */
	public FileInfo stat(String path) throws IOException {
		checkConnected();
		String r = sendListFilesCommand("F", path).get(0);
		FileInfo res = new FileInfo(r);
		return res;
	}

	public long getFileSize(String pathName) throws IOException {
		checkConnected();
		return sendSizeCommand(pathName);
	}

	/**
	 * change to the given remote directory
	 *
	 * @param dir - the dir to change to, relative to the current one
	 *
	 * @throws IOException
	 */
	public void cd(String dir) throws IOException {
		checkConnected();
		sendCD(dir);
	}

	/**
	 * change to the parent directory
	 *
	 * @throws IOException
	 */
	public void cdUp() throws IOException {
		checkConnected();
		sendCDUP();
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
		sendMKD(dir);
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
		sendRM(path);
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
		sendRename(from, to);
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
		sendChangeModificationTime(path, to);
	}
	
	/**
	 * get the current directory
	 */
	public String pwd() throws IOException {
		checkConnected();
		return sendPWD();
	}

	public static final String TYPE_NORMAL = "NORMAL";
	public static final String TYPE_ARCHIVE = "ARCHIVE";

	public void setType(String type) throws IOException{
		checkConnected();
		if(TYPE_ARCHIVE.equalsIgnoreCase(type)) {
			assertFeature(TYPE_ARCHIVE);
		}
		client.sendControl("TYPE "+type+"\r\n");
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
		sendSYNCMaster(remoteMaster);
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
		sendSYNCSlave(remoteSlave);
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

	private long moveData(long maxBytes) throws IOException {
		return moveData(maxBytes, maxBytes<0);
	}

	private long moveData(long maxBytes, boolean forceCloseData) throws IOException {
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
	private void controlRate(long total, long startTime) throws InterruptedException {
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

	//
	// send "RANG <start> <end>" 
	// http://tools.ietf.org/html/draft-bryan-ftp-range-05
	//
	private void sendRangeCommand(long offset, long length) throws IOException {
		String message = "RANG " + offset + " " + (offset+length) + "\r\n";
		client.sendControl(message);
		Reply reply = Reply.read(client);
		if (reply.getCode() != 350) {
			throw new IOException("Error: server reply " + reply);
		}
	}

	private long sendRetrieveCommand(String remoteFile) throws IOException {
		String message = "RETR " + remoteFile + "\r\n";
		client.sendControl(message);
		Reply reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error: server reply " + reply);
		}

		//expect "xxx OK <size> ...", want the size
		long size = Long.parseLong(reply.getStatusLine().split(" ")[2]);
		return size;
	}

	/**
	 * @param options - option string : 'N' for normal mode (list a directory)
	 * @param baseDir
	 */
	private List<String> sendListFilesCommand(String options, String baseDir) throws IOException {
		if (baseDir == null) {
			baseDir = ".";
		}
		String message = "STAT " + options + " " + baseDir + "\r\n";
		client.sendControl(message);
		Reply reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error reading file list: " + reply);
		}
		return reply.getResults();
	}

	private void sendCD(String dir) throws IOException {
		String message = "CWD " + dir + "\r\n";
		client.sendControl(message);
		Reply reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error: server reply " + reply);
		}
	}

	private void sendCDUP() throws IOException {		
		client.sendControl(UFTPCommands.CDUP);
		Reply reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error: server reply " + reply);
		}
	}

	private long sendSizeCommand(String remoteFile) throws IOException {
		String message = "SIZE " + remoteFile;
		client.sendControl(message);
		Reply reply = Reply.read(client);
		if (reply.getCode() != 213) {
			throw new IOException("Error: server reply " + reply);
		}

		//expect "213 <size> ...", want the size
		long size = Long.parseLong(reply.getStatusLine().split(" ")[1]);
		return size;
	}

	private void sendMKD(String pathname) throws IOException {
		String message = "MKD " + pathname + "\r\n";
		client.sendControl(message);
		Reply reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error: server reply " + reply);
		}
	}

	private void sendRM(String pathname) throws IOException {
		String message = "DELE " + pathname + "\r\n";
		client.sendControl(message);
		Reply reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error: server reply " + reply);
		}
	}

	private String sendPWD() throws IOException {
		String message = "PWD\r\n";
		client.sendControl(message);
		Reply reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error: server reply " + reply);
		}
		return reply.getStatusLine().split(" ", 2)[1];
	}

	private void sendStoreCommand(String remoteFile, long size, Long offset) throws IOException {
		Reply reply = null;
		if(offset!=null){
			client.sendControl("RANG "+offset+" "+(offset+size));
			reply = Reply.read(client);
			if (reply.isError()) {
				throw new IOException("Error setting range: server reply " + reply);
			}
		}
		String message = "ALLO " + size + "\r\n";
		client.sendControl(message);
		reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error: server reply " + reply);
		}

		message = "STOR " + remoteFile + "\r\n";
		client.sendControl(message);
		reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error: server reply " + reply);
		}
	}
	
	private void sendStoreCommand(String remoteFile) throws IOException {
		Reply reply = null;
		String message = "STOR " + remoteFile + "\r\n";
		client.sendControl(message);
		reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error: server reply " + reply);
		}
	}
	
	private void sendAppendCommand(String remoteFile, long size) throws IOException {
		Reply reply = null;
		String message;
		if(size>-1) {
			message = "ALLO " + size + "\r\n";
			client.sendControl(message);
			reply = Reply.read(client);
			if (reply.isError()) {
				throw new IOException("Error: server reply " + reply);
			}
		}
		message = "APPE " + remoteFile + "\r\n";
		client.sendControl(message);
		reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error: server reply " + reply);
		}
	}

	private void sendSYNCMaster(String pathname) throws IOException {
		// server is master
		String message = "SYNC-MASTER " + pathname + "\r\n";
		client.sendControl(message);
		Reply reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error: server reply " + reply);
		}
	}

	private void sendSYNCSlave(String pathname) throws IOException {
		// server is slave
		String message = "SYNC-SLAVE " + pathname + "\r\n";
		client.sendControl(message);
		Reply reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error: server reply " + reply);
		}
	}

	private void sendRename(String from, String to) throws IOException {
		Reply reply = null;
		String message = "RNFR " + from+ "\r\n";
		client.sendControl(message);
		reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error: server reply " + reply);
		}
		message = "RNTO " + to + "\r\n";
		client.sendControl(message);
		reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error: server reply " + reply);
		}
	}

	private void sendChangeModificationTime(String path, Calendar to) throws IOException {
		Reply reply = null;
		String time = FileInfo.toTimeVal(to.getTime().getTime());
		String message = UFTPCommands.MFMT+" "+ time+" "+ path+ "\r\n";
		client.sendControl(message);
		reply = Reply.read(client);
		if (reply.isError()) {
			throw new IOException("Error: server reply " + reply);
		}
	}
	
	private void enableKeepAlive() throws IOException {
		if(Boolean.parseBoolean(System.getenv().get("UFTP_DISABLE_KEEPALIVE"))) {
			logger.debug("KEEP-ALIVE disabled via environment");
			return;
		};
		String message = UFTPCommands.KEEP_ALIVE+" true\r\n";
		client.sendControl(message);
		Reply reply = Reply.read(client);
		keepAlive = reply.isOK();
	}

	private void checkConnected() throws IOException {
		if (!isConnected()) {
			throw new IOException("Not connected!");
		}
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
