package eu.unicore.uftp.server.workers;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.dpc.DPCServer.Connection;
import eu.unicore.uftp.dpc.Session;
import eu.unicore.uftp.dpc.UFTPConstants;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.jparss.PSocket;
import eu.unicore.uftp.rsync.Master;
import eu.unicore.uftp.rsync.MasterChannel;
import eu.unicore.uftp.rsync.RsyncStats;
import eu.unicore.uftp.rsync.Slave;
import eu.unicore.uftp.rsync.SlaveChannel;
import eu.unicore.uftp.rsync.SocketMasterChannel;
import eu.unicore.uftp.rsync.SocketSlaveChannel;
import eu.unicore.uftp.server.ServerThread;
import eu.unicore.uftp.server.requests.UFTPTransferRequest;

/**
 * A UFTPWorker processes a single Job
 *
 * @author Tim Pohlmann, 2010, Forschungszentrum Juelich - JSC
 * @author schuller
 */
public class UFTPWorker extends Thread implements UFTPConstants {

	private static final Logger logger = Utils.getLogger(Utils.LOG_SERVER, UFTPWorker.class);

	protected final ServerThread server;
	
	/**
	 * client connection
	 */
	protected final Connection connection;

	/**
	 * Job for this connection
	 */
	private final UFTPTransferRequest job;

	/**
	 * NETWORK send/receive buffer size, this must be the same on client/server
	 * if multiple streams are to be used
	 */
	public final static int BUFFSIZE = 16384;

	private final int maxStreams;

	private final byte[] buffer = new byte[BUFFSIZE];

	private Socket socket = null;

	/**
	 * FILE read/write buffer size
	 */
	private final int bufferSize;

	/**
	 *
	 * @param server
	 * @param connection
	 * @param job
	 * @param maxStreams
	 * @param bufferSize
	 */
	public UFTPWorker(ServerThread server, Connection connection, UFTPTransferRequest job, int maxStreams, int bufferSize) {
		this.server = server;
		this.connection = connection;
		this.job = job;
		this.maxStreams = maxStreams;
		this.bufferSize = bufferSize;        
	}


	boolean isSession = true;
	
	/**
	 * Open data connections, perform the data transfer, and close the
	 * connection. If appropriate, the target file permissions will be set to
	 * the user/group specified in the job
	 */
	@Override
	public void run() {
		Session session = new Session(connection, job, server.getFileAccess(), maxStreams);
		session.setRFCRangeMode(server.getRFCRangeMode());
		isSession = job.getFile().getName().endsWith(sessionModeTag);
		if (isSession) {
			boolean isPersistent = job.isPersistent();
			int count = job.newActiveSession();
			if(isPersistent)logger.info("New session for <"+job.getUser()+"> this is number <"+count+">");
			runSession(session);
			count = job.endActiveSession();
			if(isPersistent)logger.info("Ending session for <"+job.getUser()+"> remaining: "+count
					+( count==0 ?", request processing finished.":""));
			if(count==0 && isPersistent)server.invalidateJob(job);
		} else {
			runSingle(session, job.getFile());
		}
	}

	/**
	 * transfer multiple files. File names and byte ranges are read from the
	 * control channel, while data is sent over the data socket(s).
	 */
	protected void runSession(Session session) {
		int action = 0;
		logger.info("Processing "+String.valueOf(session));
		try {
			action = Session.ACTION_NONE;

			// want to disable control channel read timeouts in session mode
			connection.setControlTimeout(0);

			while (session.isAlive()) {
				action = session.getNextAction();

				switch (action) {

				case Session.ACTION_OPEN_SOCKET:
					socket = makeSocket(maxStreams, connection);
					break;
					
				case Session.ACTION_CLOSE_DATA:
					logger.info("Closing data connection for "+session.getClientDescription());
					connection.closeData();
					socket = null;
					break;

				case Session.ACTION_RETRIEVE:
					sendData(session);
					break;

				case Session.ACTION_STORE:
					readData(session);
					break;

				case Session.ACTION_SYNC_MASTER:
					syncMaster(session);
					break;

				case Session.ACTION_SYNC_SLAVE:
					syncSlave(session);
					break;

				case Session.ACTION_END:
					cleanup();
					break;
					
				case Session.ACTION_SEND_STREAM_DATA:
					sendStreamData(session);
					break;

				case Session.ACTION_SEND_HASH:
					sendHashData(session);
					break;

				case Session.ACTION_NONE:
				}
			}
		} catch (Exception ex) {
			String msg = "Error processing session-mode action <"+action+
					"> for connection from " + connection.getAddress();
			logger.error(msg, ex);
			cleanup();
		}
	}

	/**
	 * transfer a single file - this is a UFTP-only behaviour not in line with the FTP protocol!
	 */
	protected void runSingle(Session session, File localFile) {

		// run session until we have opened the data connections and are ready for sending!
		try {
			int action = Session.ACTION_NONE;

			// want to disable control channel read timeouts in session mode
			connection.setControlTimeout(0);

			while (session.isAlive() && Session.ACTION_OPEN_SOCKET!=action) {
				action = session.getNextAction();

				switch (action) {

				case Session.ACTION_OPEN_SOCKET:
					socket = makeSocket(maxStreams, connection);
					break;

				case Session.ACTION_NONE:
				}
			}
		}catch(Exception ex){}

		doRunSingle(session, localFile);
		cleanup();
	}

	protected void doRunSingle(Session session, File localFile) {
		try {

			long maxBytes = Long.MAX_VALUE;

			boolean send = job.isSend();


			String fileName = localFile.getAbsolutePath();
			boolean canAppend = true;
			if (fileName.startsWith("/dev/") && fileName.contains("_")) {
				// special case for performance measurements
				// file name contains amount of data to send/receive
				String[] s = fileName.split("_");
				fileName = s[0];
				maxBytes = Long.parseLong(s[1]);
				session.setRange(0, maxBytes);
				canAppend = false;
				localFile = new File(fileName);
			}
			else if(send){
				maxBytes = localFile.length() - job.getOffset();
				session.setRange(job.getOffset(), maxBytes);
			}
			session.setupLocalFile(localFile, !send);
			
			if (send) {
				sendData(session);
			} else {
				boolean append = job.isAppend() && canAppend;
				logger.info("Receiving " + (append?"(appending to) ":"") + localFile.getAbsolutePath());
				if(append){
					session.setRange(localFile.length(), maxBytes);
				}
				readData(session);
			}
			
		} catch (Exception e) {
			logger.error("Error", e);
			Utils.closeQuietly(socket);
		}

	}

	private void cleanup(){
		connection.close();
		server.notifyConnectionClosed(connection.getAddress());
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
		if (interval==0) interval = 1;
		long rate = 1000 * total / interval;
		long rateLimit = job.getRateLimit();
		if (rate < rateLimit) {
			//decrease sleep time
			sleepTime = sleepTime / 2;
			return;
		}
		//else increase sleep time and wait
		sleepTime += 5;
		Thread.sleep(sleepTime);
	}

	/**
	 * send data from a file
	 *
	 * @param session - the session containing the required information
	 * @throws IOException
	 * @throws java.lang.InterruptedException
	 */
	protected void sendData(Session session) throws IOException, InterruptedException {
		RandomAccessFile ra = session.getLocalRandomAccessFile();
		OutputStream target = preSend(session);
		
		ra.seek( session.getOffset());

		long startTime = System.currentTimeMillis();
		long bytesToSend = session.getNumberOfBytes();
		boolean controlRate = job.getRateLimit() > 0;
		final int bufSize = buffer.length;
		int n, len;
		long total = 0;
		while (total < bytesToSend) {
			len = (int) Math.min(bufSize, bytesToSend - total);
			n = ra.read(buffer, 0, len);
			if (n < 0) {
				break;
			}
			target.write(buffer, 0, n);
			total += n;
			if (controlRate) {
				controlRate(total, startTime);
			}
		}
		postSend(target, session, total, startTime, "Send", true);
	}

	/**
	 * compute hash from a file and send it via the control channel
	 *
	 * @param session - the session containing the required information
	 * @throws IOException
	 * @throws java.lang.InterruptedException
	 */
	protected void sendHashData(Session session) throws IOException, InterruptedException {
		RandomAccessFile ra = session.getLocalRandomAccessFile();
		ra.seek(session.getOffset());
		long startTime = System.currentTimeMillis();
		long intervalStart = System.currentTimeMillis();
		long bytesToSend = session.getNumberOfBytes();
		final int bufSize = buffer.length;
		int n, len;
		long total = 0;
		MessageDigest md = session.getMessageDigest();
		while (total < bytesToSend) {
			len = (int) Math.min(bufSize, bytesToSend - total);
			n = ra.read(buffer, 0, len);
			if (n < 0) {
				break;
			}
			md.update(buffer, 0, n);
			total += n;
			// keep the client entertained
			if(System.currentTimeMillis()-intervalStart > 30000) {
				intervalStart = System.currentTimeMillis();
				connection.sendControl("213-");
			}
		}
		String hash = Utils.hexString(md);
		String msg = "213 "+session.getHashAlgorithm()+" "
					+session.getOffset()+"-"+(bytesToSend-1)
					+ " "+hash+" "+session.getLocalFile().getPath();
		connection.sendControl(msg);
		postSend(null, session, total, startTime, session.getHashAlgorithm(), false);
	}

	protected void readData(Session session) throws IOException, InterruptedException {
		InputStream reader;
		if(!(socket instanceof PSocket)){
			if (job.getKey() != null) {
				//need to wrap here for encryption
				reader = Utils.getDecryptStream(socket.getInputStream(), job.getKey());
			} else {
				reader = socket.getInputStream();
			}
			if(job.isCompress()){
				reader = Utils.getDecompressStream(reader);
			}
		}
		else {
			reader = socket.getInputStream();
		}
		long startTime = System.currentTimeMillis();

		long total = 0;
		long numFiles = -1;

		if(session.isArchiveMode()) {
			try{
				ReceivedDataStats rcv = readArchiveData(session, reader);
				total = rcv.size;
				numFiles = rcv.numFiles;
			}catch(Exception ex) {
				throw new IOException(ex);
			}
		}
		else {
			total = readNormalData(session, reader);
		}

		if(!session.isKeepAlive())Utils.closeQuietly(reader);
		session.reset();
		if(logger.isDebugEnabled()){
			logger.debug("Time: " + (System.currentTimeMillis() - startTime) + " total bytes transferred: " + total);
		}
		long millis = System.currentTimeMillis() - startTime;
		logUsage("Receive", total, millis, connection.getAddress(), numFiles);
	}

	private long readNormalData(Session session, InputStream reader) throws IOException, InterruptedException {
		long offset = session.getOffset();
		long bytesToRead = session.getNumberOfBytes();
		RandomAccessFile ra = session.getLocalRandomAccessFile();
		ra.seek(offset);

		long total = copyData(reader, ra, bytesToRead);

		if(!session.haveRange()){
			// make sure we truncate to properly handle over-writing existing files
			ra.getChannel().truncate(offset+total);
		}
		return total;
	}


	private ReceivedDataStats readArchiveData(Session session, InputStream reader)
			throws IOException, ArchiveException, InterruptedException {
		long counter = 0;
		reader = new BufferedInputStream(reader, 2048);
		ArchiveInputStream input = new ArchiveStreamFactory().createArchiveInputStream(reader);
		ArchiveEntry entry = null;
		long total = 0;

	        while ((entry = input.getNextEntry()) != null) {
	            if (!input.canReadEntryData(entry)) {
	                logger.error("Cannot read archive entry <"+entry.getName()+"> for "+session.getClientDescription());
	                continue;
	        }

	        File f = new File(session.getLocalFile(), entry.getName());

	        if (entry.isDirectory()) {
	        	session.getFileAccess().mkdir(f.getAbsolutePath());
	        } else {
	            File parent = f.getParentFile();
	            session.getFileAccess().mkdir(parent.getAbsolutePath());
	            try(RandomAccessFile ra = session.getFileAccess().getRandomAccessFile(f, "rw")){
	            	total += copyData(input, ra, Long.MAX_VALUE);
	            	counter++;
	            }
	        }
	    }

		return new ReceivedDataStats(total, counter);
	}

	public static class ReceivedDataStats {
		public long size, numFiles;

		public ReceivedDataStats(long size, long numFiles) {
			this.size = size;
			this.numFiles = numFiles;
		}
	}
	
	// copy the given amount of bytes from the input to the target file
	// controls transfer rate
	private long copyData(InputStream in, RandomAccessFile ra, long bytesToRead) 
			throws IOException, InterruptedException {
		final int bufSize = buffer.length;
		int n, len;
		long total = 0;
		long startTime = System.currentTimeMillis();
		boolean controlRate = job.getRateLimit() > 0;
		while (total < bytesToRead) {
			len = (int) Math.min(bufSize, bytesToRead - total);
			n = in.read(buffer, 0, len);
			if (n < 0) {
				break;
			}
			ra.write(buffer, 0, n);
			total += n;
			if (controlRate) {
				controlRate(total, startTime);
			}
		}
		return total;
	}
	
	/**
	 * send data from a file
	 *
	 * @param session - the session containing the required information
	 * @throws IOException
	 * @throws java.lang.InterruptedException  */
	protected void sendStreamData(Session session) throws IOException, InterruptedException {
		InputStream source = session.getStream();
		OutputStream target = preSend(session);

		long bytesToSend = session.getNumberOfBytes();
		long startTime = System.currentTimeMillis();
		boolean controlRate = job.getRateLimit() > 0;
		final int bufSize = buffer.length;
		int n, len;
		long total = 0;
		while (total < bytesToSend) {
			len = (int) Math.min(bufSize, bytesToSend - total);
			n = source.read(buffer, 0, len);
			if (n < 0) {
				break;
			}
			target.write(buffer, 0, n);
			total += n;
			if (controlRate) {
				controlRate(total, startTime);
			}
		}
		postSend(target, session, total, startTime, "Send", true);
	}
	
	private OutputStream preSend(Session session)throws IOException {
		OutputStream target = null;
		if(!(socket instanceof PSocket)){
			if (job.getKey() != null) {
				//need to wrap here for encryption
				target = Utils.getEncryptStream(socket.getOutputStream(), job.getKey());
			} else {
				target = socket.getOutputStream();
			}
			if(job.isCompress()){
				target = Utils.getCompressStream(target);
			}
		}
		else {
			target = socket.getOutputStream();
		}
		return target;
	}
	
	private void postSend(OutputStream target, Session session, long total, long startTime, String operation, boolean send226) throws IOException {
		if(target!=null) {
			target.flush();
			if(isSession)Utils.finishWriting(target);
			if(!session.isKeepAlive())Utils.closeQuietly(target);
		}
		session.reset(send226);
		long millis = System.currentTimeMillis() - startTime;
		logUsage(operation, total, millis, connection.getAddress(), -1);
	}
	
	protected void syncMaster(Session session) throws Exception {
		MasterChannel channel = new SocketMasterChannel(socket);
		String name = session.getLocalFile().getAbsolutePath();
		Master master = new Master(session.getLocalRandomAccessFile(), channel, name);
		RsyncStats stats = master.call();
		logger.info(stats);
	}

	protected void syncSlave(Session session) throws Exception {
		SlaveChannel channel = new SocketSlaveChannel(socket);
		String name = session.getLocalFile().getAbsolutePath();
		int blockSize = Slave.reasonableBlockSize(session.getLocalFile());
		Slave slave = new Slave(session.getLocalRandomAccessFile(), channel, name, blockSize);
		slave.setFileAccess(session.getFileAccess());
		RsyncStats stats = slave.call();
		logger.info(stats);
	}

	/**
	 * create a new Socket with the specified number of parallel TCP streams
	 *
	 * @param max - the number of parallel streams
	 * @param connection - the client connection
	 * @return a {@link Socket}
	 * @throws Exception
	 */
	protected Socket makeSocket(int max, Connection connection) throws Exception {
		Socket localSocket;
		int n = Math.min(job.getStreams(), max);

		List<Socket>dataCons = connection.getDataSockets();
		if (n > 1) {
			logger.info("Creating parallel socket with " + n + " streams.");
			PSocket parallelSocket = new PSocket(job.getKey(), job.isCompress());
			parallelSocket.init(1, dataCons.size());
			for (Socket dataCon : dataCons) {
				parallelSocket.addSocketStream(dataCon);
			}
			localSocket = parallelSocket;
		} else {
			logger.info("Creating normal socket.");	
			localSocket = dataCons.get(0);
		}
		return localSocket;
	}

	/**
	 * @return returns the {@link UFTPTransferRequest} for this connection 
	 */
	public UFTPTransferRequest getJob() {
		return job;
	}

	public Connection getConnection() {
		return connection;
	}

	public int getBufferSize() {
		return bufferSize;
	}

	/**
	 * computes transfer rates, bytes transferred and
	 * logs it to the USAGE logger at INFO level<br/>
	 * (If needed, Log4j can be configured to send these log messages to a 
	 * specific file instead of the standard logfile)
	 * <p>
	 * The format is:
	 *    [Sent|Received] [clientIP] [user:group] [bytes] [kb/sec]   
	 * </p>
	 */
	protected void logUsage(String operation, long dataSize, long consumedMillis, InetAddress clientIP, long numFiles){
		if(!logger.isInfoEnabled())return;
		String group=job.getGroup();
		float r=(float)dataSize/(float)consumedMillis;

		StringBuilder sb = new StringBuilder();
		sb.append("USAGE [").append(operation).append("] ");
		sb.append("[").append(clientIP).append("] ");
		sb.append("[").append(job.getUser()).append(":");
		sb.append(group!=null?group:"n/a").append("] ");
		if(numFiles>0)sb.append("[").append(numFiles).append(" files] ");
		sb.append("[").append(dataSize).append(" bytes] ");
		if(dataSize>10000) {
			String unit = " kB/sec";
			if(r>1000) {
				r = r / 1000f;
				unit = " MB/sec";
			}
			sb.append("[").append((long)r).append(unit);
		}
		sb.append("]");
		String msg = sb.toString();

		logger.info(msg);
	}

}
