package eu.unicore.uftp.standalone;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.authserver.messages.AuthResponse;
import eu.unicore.uftp.client.FileInfo;
import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.dpc.AuthorizationFailureException;
import eu.unicore.uftp.rsync.RsyncStats;
import eu.unicore.uftp.standalone.authclient.AuthClient;
import eu.unicore.uftp.standalone.lists.Command;
import eu.unicore.uftp.standalone.lists.FileCrawler;
import eu.unicore.uftp.standalone.lists.FileCrawler.RecursivePolicy;
import eu.unicore.uftp.standalone.lists.LocalFileCrawler;
import eu.unicore.uftp.standalone.lists.RemoteFileCrawler;
import eu.unicore.uftp.standalone.util.ClientPool;
import eu.unicore.uftp.standalone.util.ProgressBar;
import eu.unicore.uftp.standalone.util.RangeMode;
import eu.unicore.util.Log;

/**
 * Client facade is a high-level abstraction over uftp and authentication client
 *
 * @author jj
 */
public class ClientFacade {

	private static final Logger logger = Log.getLogger(Log.CLIENT, ClientFacade.class);

	volatile boolean connected = false;
	
	private final ConnectionInfoManager connectionManager;

	private String group = null;

	private int numClients = 1;

	private UFTPSessionClient sc;

	private final UFTPClientFactory factory;

	private int streams = 1;

	private Long startByte = null;

	private Long endByte = null;

	private RangeMode rangeMode = RangeMode.READ_WRITE;

	private boolean compress = false;

	private byte[] encryptionKey = null;

	private boolean resume = false;

	private boolean preserveAttributes = false;

	private String clientIP;

	private boolean verbose = false;

	public static long DEFAULT_THRESHOLD = 1024*1024 * 512;
	
	private long largeFileThreshold = DEFAULT_THRESHOLD;
	
	// bandwith limit (bytes per second per FTP connection)
	private long bandwidthLimit = -1;

	private boolean archiveMode = false;
	
	public ClientFacade(ConnectionInfoManager connectionInfoManager, UFTPClientFactory clientFactory) {
		this.connectionManager = connectionInfoManager;
		this.factory = clientFactory;
	}

	public void setNumClients(int numClients){
		this.numClients = numClients;
	}
	
	public void setSplitThreshold(long threshold){
		this.largeFileThreshold = threshold;
	}

	public AuthResponse authenticate(String uri) throws Exception {
		connectionManager.init(uri);
		AuthResponse response = initSession(connectionManager.getAuthClient(this));
		if(!response.success){
			throw new AuthorizationFailureException("Failed to successfully authenticate: "+response.reason);
		}
		return response;
	}

	/**
	 * Connects to given server
	 *
	 * @param uri connection details uri
	 * @throws Exception
	 */
	public UFTPSessionClient doConnect(String uri) throws Exception {
		logger.debug("Connecting to {}", uri);
		AuthResponse response = authenticate(uri);
		UFTPSessionClient sc = factory.getUFTPClient(response);
		sc.setNumConnections(streams);
		sc.setCompress(compress);
		sc.setKey(encryptionKey);
		sc.setBandwidthLimit(bandwidthLimit);
		sc.connect();
		connected = true;
		return sc;
	}
	
	public void connect(String uri) throws Exception {
		sc = doConnect(uri);
	}

	/**
	 * Disconnects from server
	 *
	 * @throws IOException
	 */
	public void disconnect() throws IOException {
		if (!connected) {
			return;
		}
		sc.close();
		connected = false;
	}

	/**
	 * Copy function
	 *
	 * Source and destination can include wildcards e.g.:
	 * <p>
	 * Local:
	 * <ul>
	 * <li>/tmp/*</li>
	 * <li>/tmp/*.dat</li>
	 * </ul>
	 * </p>
	 * <p>
	 * Remote:
	 * <ul>
	 * <li>uftp://server:port/path/dir/*.dat</li>
	 * <li>uftp://server/path/*</li>
	 * </ul>
	 *
	 * If not connection is established to remote server, such connection will
	 * be initialized.
	 *
	 * @param source local or remote source
	 * @param destination local or remote destination
	 * @param policy policy to include subdirectories
	 * @throws Exception
	 */
	public void cp(String source, String destination, FileCrawler.RecursivePolicy policy)
			throws Exception {
		if (!connected) {
			if (ConnectionInfoManager.isRemote(source) && ConnectionInfoManager.isLocal(destination)) {
				connect(source);
				cp(source, destination, policy);
				return;
			}
			if (ConnectionInfoManager.isLocal(source) && ConnectionInfoManager.isRemote(destination)) {
				connect(destination);
				cp(source, destination, policy);
				return;
			}
		}

		if (connected) {
			if (!connectionManager.isSameServer(source) && !connectionManager.isSameServer(destination)) {
				disconnect();
				cp(source, destination, policy);
				return;
			}
		}

		logger.debug("cp {} -> {}", source, destination);
		if (ConnectionInfoManager.isLocal(source) && connectionManager.isSameServer(destination)) {
			startUpload(source, destination, policy);
			return;
		}
		if (ConnectionInfoManager.isLocal(destination) && connectionManager.isSameServer(source)) {
			startDownload(source, destination, policy);
			return;
		}
		String error = String.format("Unable to handle [%s, %s] combination. "
				+ "It is neither upload nor download", source, destination);
		throw new IllegalArgumentException(error);
	}

	/**
	 * List content of given directory
	 *
	 * @param directory path
	 * @return List of strings in form: mode, size, lastModified, FName
	 * @throws IOException
	 */
	public List<FileInfo> ls(String directory) throws Exception {

		if (!connected) {
			connect(directory);
		}
		String listDir = directory;
		if (ConnectionInfoManager.isRemote(directory)) {
			listDir = connectionManager.extractConnectionParameters(directory).get("path");
		}
		List<FileInfo> fileList = sc.getFileInfoList(listDir);
		return fileList;
	}

	/**
	 * Get info about single file or directory
	 *
	 * @param file
	 * 
	 * @throws IOException
	 */
	public FileInfo stat(String file) throws Exception {
		if (!connected) {
			connect(file);
		}
		if (ConnectionInfoManager.isRemote(file)) {
			file = connectionManager.extractConnectionParameters(file).get("path");
		}
		return sc.stat(file);
	}

	/**
	 * create remote directory
	 *
	 * @param file
	 * 
	 * @throws IOException
	 */
	public void mkdir(String file) throws Exception {
		if (!connected) {
			connect(file);
		}
		if (ConnectionInfoManager.isRemote(file)) {
			file = connectionManager.extractConnectionParameters(file).get("path");
		}
		sc.mkdir(file);
	}

	/**
	 * delete remote file/directory
	 *
	 * @param file
	 * 
	 * @throws IOException
	 */
	public void rm(String file) throws Exception {
		if (!connected) {
			connect(file);
		}
		if (!ConnectionInfoManager.isRemote(file)) {
			System.err.println("Not a remote file: '"+file+"'");
			return;
		}

		Command cmd = getRemoveCommand();
		Map<String, String> params = connectionManager.extractConnectionParameters(file);
		String path = params.get("path");
		FileCrawler fileList = new RemoteFileCrawler(path, null, sc);
		if (fileList.isSingleFile(path)) {
			sc.rm(path);
		}
		else{
			fileList.crawl(cmd, FileCrawler.RecursivePolicy.NONRECURSIVE);
		}
	}

	/**
	 * Change current remote directory
	 *
	 * @param cd
	 * @throws IOException
	 */
	public void cd(String cd) throws IOException {
		sc.cd(cd);
	}

	/**
	 * Print working directory
	 *
	 * @return current working directory
	 * @throws IOException
	 */
	public String pwd() throws IOException {
		return sc.pwd();
	}

	public RsyncStats sync(String master, String slave) throws Exception{
		logger.debug("sync {} (MASTER) -> {} (SLAVE)", master, slave);
		RsyncStats stats = null;
		if (!connected) {
			if (ConnectionInfoManager.isRemote(master) && ConnectionInfoManager.isLocal(slave)) {
				connect(master);
				Map<String, String> params = connectionManager.extractConnectionParameters(master);
				String path = params.get("path");
				stats = rsyncLocalFile(slave, path);
			}
			else if (ConnectionInfoManager.isLocal(master) && ConnectionInfoManager.isRemote(slave)) {
				connect(slave);
				Map<String, String> params = connectionManager.extractConnectionParameters(slave);
				String path = params.get("path");
				stats =  rsyncRemoteFile(master, path);
			}
			else {
				throw new IOException("Need one remote and one local file for sync.");
			}
		}
		logger.info("Statistics : {}", stats);
		return stats;
	}
	
	/**
	 * Copy function
	 *
	 * Source and destination can include wildcards e.g.:
	 * <p>
	 * Local:
	 * <ul>
	 * <li>/tmp/*</li>
	 * <li>/tmp/*.dat</li>
	 * </ul>
	 * </p>
	 * <p>
	 * Remote:
	 * <ul>
	 * <li>uftp://server:port/path/dir/*.dat</li>
	 * <li>uftp://server/path/*</li>
	 * </ul>
	 *
	 * If not connection is established to remote server, such connection will
	 * be initialized.
	 *
	 * @param source local or remote source
	 * @param destination local or remote destination
	 * @param policy policy to include subdirectories
	 * @throws Exception
	 */
	public void checksum(String fileSpec, String algorithm, FileCrawler.RecursivePolicy policy)
			throws Exception {
		if (!connected) {
			connect(fileSpec);
			checksum(fileSpec, algorithm, policy);
			return;
		}
		
		if (connected) {
			if (!connectionManager.isSameServer(fileSpec)) {
				disconnect();
				checksum(fileSpec, algorithm, policy);
				return;
			}
		}

		logger.debug("checksum {}", fileSpec);
		Map<String, String> params = connectionManager.extractConnectionParameters(fileSpec);
		String path = params.get("path");
		FileCrawler fileList = new RemoteFileCrawler(path, "./dummy/", sc);
		if (fileList.isSingleFile(path)) {
				doChecksum(path, algorithm);
		}
		else{
			Command cmd = new Command() {
				public void execute(String path, String dummy) throws IOException{
					doChecksum(path, algorithm);
				}
			};
			fileList.crawl(cmd, policy);
		}
	}

	private boolean haveSetAlgorithm = false;
	
	private void doChecksum(String path, String algorithm) throws IOException{
		if(algorithm!=null && !haveSetAlgorithm) {
			String reply = sc.setHashAlgorithm(algorithm);
			haveSetAlgorithm = true;
			if(verbose)System.out.println("Set hash algorithm: "+reply);
		}
		UFTPSessionClient.HashInfo hi = sc.getHash(path);
		System.out.println(hi.hash+"  "+hi.path);
	}

	private void startUpload(String localSource, String destinationURL, RecursivePolicy policy) 
			throws Exception {
		Map<String, String> params = connectionManager.extractConnectionParameters(destinationURL);
		String remotePath = params.get("path");
		try(ClientPool pool = new ClientPool(numClients, this, destinationURL, verbose, largeFileThreshold)){
			LocalFileCrawler fileList = new LocalFileCrawler(localSource, remotePath, sc);
			fileList.crawl(getUploadCommand(preserveAttributes, pool), policy);
		}
	}
	

	private void uploadFile(String local, String remotePath, ClientPool pool) throws FileNotFoundException, URISyntaxException, IOException {
		logger.debug("Uploading file {} -> {}", local, remotePath);
		ProgressBar pb = null;
		String dest = getFullRemoteDestination(local, remotePath);
		if(archiveMode) {
			sc.setType(UFTPSessionClient.TYPE_ARCHIVE);
		}
		try {
			if("-".equals(local)){
				if(verbose){
					pb = new ProgressBar("stdin", -1);
					sc.setProgressListener(pb);
				}
				sc.writeAll(dest, System.in, true);
			}
			else{
				File file = new File(local);
				long size = file.length();
				
				if(startByte!=null)
				{
					size = endByte-startByte+1;
					if(size<largeFileThreshold || numClients<2){
						if(verbose){
							pb = new ProgressBar(file.getName(), size);
							sc.setProgressListener(pb);
						}
						uploadFileChunk(dest, local, startByte, endByte, sc);
					}
					else{
						pool.putChunked(file, dest, startByte, size);
					}
				}
				else{
					long offset = 0;
					if(resume){
						try{
							offset = sc.getFileSize(dest);
						}catch(IOException ioe) {
							// does not exist
						}
						size = file.length() - offset;
						if(size>0){
							logger.debug("Resuming transfer, already have <{}> bytes", offset);
							pool.put(file, dest, size, offset, preserveAttributes);
						}
						else{
							logger.debug("Nothing to do for <{]>", dest);
						}
					}
					else{
						if(size<largeFileThreshold || numClients<2){
							pool.put(file, dest, size, 0, preserveAttributes);
						}
						else{
							pool.putChunked(file, dest, size);
						}
					}
				}
			}
		}
		finally{
			closeQuietly(pb);
			sc.setProgressListener(null);
		}
	}
	
	public void uploadFileChunk(String remote, String local, long start, long end, UFTPSessionClient sc)
	throws IOException {
		File file = new File(local);
		try(RandomAccessFile raf = new RandomAccessFile(file, "r");
			InputStream fis = Channels.newInputStream(raf.getChannel()))
		{
			raf.seek(start);
			sc.put(remote, end-start+1, start, fis);
			if(preserveAttributes && !remote.startsWith("/dev/")){
				Calendar to = Calendar.getInstance();
				to.setTimeInMillis(file.lastModified());
				sc.setModificationTime(remote, to);
			}
		}
	}

	private void startDownload(String remote, String destination, RecursivePolicy policy) 
			throws Exception {
		Map<String, String> params = connectionManager.extractConnectionParameters(remote);
		String path = params.get("path");
		try(ClientPool pool = new ClientPool(numClients, this, remote, verbose, largeFileThreshold)){
			FileCrawler fileList = new RemoteFileCrawler(path, destination, sc);
			if (fileList.isSingleFile(path)) {
				downloadFile(path, destination, pool);
			}
			else{
				Command cmd = getDownloadCommand(preserveAttributes, pool);
				fileList.crawl(cmd, policy);
			}
		}
	}

	private void downloadFile(String remotePath, String local, ClientPool pool) 
			throws FileNotFoundException, URISyntaxException, IOException {
		String dest = getFullLocalDestination(remotePath, local);
		logger.debug("Downloading file {} -> {}", remotePath, dest);

		File file = new File(dest);
		OutputStream fos = null;
		RandomAccessFile raf = null;
		ProgressBar pb = null;
		
		try{
			if("-".equals(local)){
				fos = System.out;
				sc.get(remotePath,fos);
				sc.resetDataConnections();
			}else{
				FileInfo fi = sc.stat(remotePath);
				if(startByte!=null){
					long size = endByte-startByte+1;
					if(size<largeFileThreshold || numClients<2){
						if(verbose){
							pb = new ProgressBar(local,endByte-startByte);
							sc.setProgressListener(pb);
						}	
						downloadFileChunk(remotePath, dest, startByte, endByte, sc, fi);
					}
					else{
						pool.getChunked(remotePath, dest, fi, startByte, size);
					}
				}
				else{
					if(resume && file.exists()){
						// check if we have local data already
						long start = file.length();
						long length = fi.getSize() - start;
						raf = new RandomAccessFile(file, "rw");
						fos = Channels.newOutputStream(raf.getChannel());
						
						if(length>0){
							logger.debug("Resuming transfer, already have <{}> bytes", start);
							if(verbose){
								pb = new ProgressBar(local,length);
								sc.setProgressListener(pb);
							}
							pool.get(remotePath, dest, fi, start, length, preserveAttributes);
						}
						else{
							logger.debug("Nothing to do for {}",remotePath);
						}
					}
					else{
						long size = fi.getSize();
						if(file.exists()) {
							// truncate it now to make sure we don't overwrite 
							// only part of the file 
							raf = new RandomAccessFile(file, "rw");
							try {
								raf.setLength(0);
							}catch(Exception e) {}
						}
						if(size<largeFileThreshold || numClients<2){
							pool.get(remotePath, dest, fi, preserveAttributes);
						}
						else{
							pool.getChunked(remotePath, dest, fi);
						}
					}
				}

			}
		}
		finally{
			closeQuietly(fos);
			closeQuietly(raf);
			closeQuietly(pb);
			sc.setProgressListener(null);
		}
	}

	public void downloadFileChunk(String remotePath, String dest, long start, long end, UFTPSessionClient sc, FileInfo fi)
			throws FileNotFoundException, URISyntaxException, IOException{
		logger.debug("Downloading [{}:{}] from {} to {}", start, end, remotePath, dest);
		File file = new File(dest);
		OutputStream fos = null;
		RandomAccessFile raf = null;
		try{
			raf = new RandomAccessFile(file, "rw");
			if(rangeMode==RangeMode.READ_WRITE){
				raf.seek(start);
			}
			fos = Channels.newOutputStream(raf.getChannel());
			
			sc.get(remotePath, start, end-start+1, fos);
			if(preserveAttributes && !dest.startsWith("/dev/")){
				try{
					Files.setLastModifiedTime(file.toPath(),FileTime.fromMillis(fi.getLastModified()));
				}catch(Exception ex){
					Log.logException("Error updating modification time", ex, logger);
				}
			}
		}finally{
			closeQuietly(fos);
			closeQuietly(raf);
			sc.setProgressListener(null);
		}
	}

	/**
	 * @param local - master
	 * @param remote - slave
	 * @throws Exception
	 */
	private RsyncStats rsyncRemoteFile(String local, String remote) throws Exception {
		File localMaster = new File(local);
		if (!localMaster.isFile()) {
			throw new IllegalArgumentException(local + " is not a file");
		}
		return sc.syncRemoteFile(localMaster, remote);
	}


	/**
	 * @param local - slave
	 * @param remote - master
	 * @throws Exception
	 */
	private RsyncStats rsyncLocalFile(String local, String remote) throws Exception {
		File localSlave = new File(local);
		if (!localSlave.isFile()) {
			throw new IllegalArgumentException(local + " is not a file");
		}
		return sc.syncLocalFile(remote, localSlave);
	}

	private Command getDownloadCommand(final boolean preserveAttributes, final ClientPool pool) {
		Command downloadCommand = new Command() {
			@Override
			public void execute(String source, String destination) throws IOException {
				try {
					downloadFile(source, destination, pool);
				} catch (URISyntaxException|IOException ex) {
					throw new IOException("Error downloading file "+source+" to "+destination, ex);
				}
			}		
		};
		return downloadCommand;
	}

	private Command getUploadCommand(final boolean preserveAttributes, final ClientPool pool) {
		Command downloadCommand = new Command() {
			@Override
			public void execute(String source, String destination) throws IOException {
				try {
					uploadFile(source, destination, pool);
				} catch (URISyntaxException|IOException ex) {
					throw new IOException("Error uploading file "+source+" to "+destination, ex);
				}
			}
		};
		return downloadCommand;
	}

	private Command getRemoveCommand() {
		Command removeCommand = new Command() {
			@Override
			public void execute(String source, String destination) throws IOException {
				sc.rm(source);
			}		
		};
		return removeCommand;
	}

	/**
	 * if user specifies a remote directory, append the source file name
	 */
	String getFullRemoteDestination(String source, String destination) {
		if(destination.endsWith("/") && !"-".equals(source)){
			return FilenameUtils.concat(destination, FilenameUtils.getName(source));
		}
		else return destination;
	}

	/**
	 * if the local destination is a directory, append the source file name
	 */
	public String getFullLocalDestination(String source, String destination) {
		String destName = FilenameUtils.getName(destination);
		if (destName == null || destName.isEmpty() || new File(destination).isDirectory()) {
			destName = FilenameUtils.getName(source);
			//verify not null?
		}
		return FilenameUtils.concat(FilenameUtils.getFullPath(destination), destName);
	}

	private AuthResponse initSession(AuthClient authClient) throws Exception {
		return authClient.createSession();
	}

	public void setRange(long startByte, long endByte, RangeMode mode){
		this.startByte = startByte;
		this.endByte = endByte;
		this.rangeMode = mode;
	}

	public void setRange(long startByte, long endByte){
		setRange(startByte, endByte, RangeMode.READ_WRITE);
	}

	public void resetRange(){
		this.startByte = null;
		this.endByte = null;
	}

	public int getStreams() {
		return streams;
	}

	public void setStreams(int streams) {
		this.streams = streams;
	}

	public boolean isCompress() {
		return compress;
	}

	public void setCompress(boolean compress) {
		this.compress = compress;
	}

	public byte[] getEncryptionKey() {
		return encryptionKey;
	}

	public void setEncryptionKey(byte[] encryptionKey) {
		this.encryptionKey = encryptionKey;
	}

	public long getBandwithLimit() {
		return bandwidthLimit;
	}

	public void setBandwithLimit(long limit) {
		this.bandwidthLimit = limit;
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public boolean isResume() {
		return resume;
	}

	public void setResume(boolean resume) {
		this.resume = resume;
	}

	public boolean isPreserveAttributes() {
		return preserveAttributes;
	}

	public void setPreserveAttributes(boolean preserve) {
		this.preserveAttributes = preserve;
	}

	public String getClientIP() {
		return clientIP;
	}

	public void setClientIP(String clientIP) {
		this.clientIP = clientIP;
	}

	public ConnectionInfoManager getConnectionManager(){
		return connectionManager;
	}

	public void setArchiveMode(boolean archiveMode){
		this.archiveMode = archiveMode;
	}
	
	public void setVerbose(boolean verbose){
		this.verbose = verbose;
	}

	private void closeQuietly(Closeable c) {
		if(c!=null)try {
			c.close();
		}catch(Exception e) {}
	}
}
