package eu.unicore.uftp.dpc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.server.JobStore;
import eu.unicore.uftp.server.PortManager;
import eu.unicore.uftp.server.UFTPCommands;
import eu.unicore.uftp.server.requests.UFTPBaseRequest;
import eu.unicore.uftp.server.requests.UFTPSessionRequest;

/**
 * Server allowing parallel data transfer with dynamic port configuration using
 * FTP
 *
 * @author Tim Pohlmann, 2010, Forschungszentrum Juelich - JSC
 * @author schuller
 */
public class DPCServer {

	private static final Logger logger = Utils.getLogger(Utils.LOG_SERVER, DPCServer.class);

	public static final String VER = 
			"UFTPD "+DPCServer.class.getPackage().getImplementationVersion()
			+ ", https://www.unicore.eu";

	private final ServerSocket serverSocket;

	// timeout on the established control connection
	private int timeout = 30000;

	private final int port;

	private final PortManager portManager;

	private final JobStore jobMap;

	private boolean checkClientIP;

	private boolean rfcMode= false;

	/**
	 * String-Array holding response messages used to establish the pseudo-FTP
	 * connection
	 */
	protected static final String[] responses = {
			"220 (" + VER + ")",
			"331 Please specify the password",
			"230 Login successful",
			"215 Unix Type: L8",};
	private InetAddress ip;

	private final String advertiseAddress;

	/**
	 * create a new server listening on the given interface
	 * 
	 * @param ip
	 *            server interface to bind to, if <code>null</code>, all
	 *            interfaces are used
	 * @param port
	 *            listen port
	 * @param backlog
	 *            maximum backlog of incoming connections
	 * @param advertiseAddress
	 *            Advertise this address as our own address in the control
	 *            connection
	 * @param pm - PortManager 
	 * @throws IOException
	 */
	public DPCServer(InetAddress ip, int port, int backlog, JobStore jobStore, String advertiseAddress, 
			PortManager pm, boolean checkClientIP)
					throws IOException {
		this.port = port;
		this.ip = ip;
		this.jobMap = jobStore;
		serverSocket = new ServerSocket(port, backlog, ip);
		serverSocket.setSoTimeout(5000);
		if (advertiseAddress != null) {
			this.advertiseAddress = InetAddress.getByName(advertiseAddress)
					.getHostAddress()
					.replace('.', ',');
		} else {
			this.advertiseAddress = null;
		}
		this.portManager = pm!=null ? pm : new PortManager();
		this.checkClientIP = checkClientIP;
	}

	/**
	 * Waits for incoming client
	 *
	 * @return control connection - which needs to be established before use
	 * @throws IOException on IO errors or if the connection is from a client address 
	 * for which no {@link UFTPSessionRequest} exists
	 */
	public Connection accept() throws IOException {
		serverSocket.setSoTimeout(timeout);
		Socket controlSocket = serverSocket.accept();
		if (timeout > 0) {
			controlSocket.setSoTimeout(timeout);
		}
		if (checkClientIP && !jobMap.haveJobs(controlSocket.getInetAddress())){
			String peer = controlSocket.getInetAddress().getHostAddress();
			Utils.closeQuietly(controlSocket);
			throw new IOException("No transfer request for client at "+peer);
		}
		return new Connection(controlSocket, getFeatures(), checkClientIP);
	}

	/**
	 * close the server socket, disallowing any new connections. Existing
	 * connections are kept
	 *
	 * @throws IOException as thrown from ServerSocket.close()
	 */
	public void close() throws IOException {
		serverSocket.close();
		logger.info("Server shut down.");
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public void setCheckClientIP(boolean checkIP){
		this.checkClientIP = checkIP;
	}

	public void setRFCRangeMode(boolean rfcMode){
		this.rfcMode = rfcMode;
	}

	public boolean getRFCRangeMode(){
		return rfcMode;
	}

	public int getPort() {
		return port;
	}

	public String getHost(){
		if (ip!=null) return ip.getHostAddress().toString();
		else {
			try {
				//listen on all
				return InetAddress.getLocalHost().getHostName().toString();
			} catch (UnknownHostException ex) {
				return null;
			}
		}
	}

	public boolean isCheckClientIP(){
		return checkClientIP;
	}

	void addJob(UFTPSessionRequest job){
		jobMap.addJob(job);
	}

	/**
	 * get the list of features supported by this server
	 *
	 * @return string list of features
	 */
	public static String[] getFeatures() {
		return new String[]{ UFTPCommands.PASV, 
				UFTPCommands.EPSV, 
				UFTPCommands.RANGSTREAM, 
				UFTPCommands.RESTSTREAM, 
				UFTPCommands.MFMT,
				UFTPCommands.MLSD,
				UFTPCommands.APPE,
				UFTPCommands.KEEP_ALIVE,
				UFTPCommands.ARCHIVE,
		};
	}

	public class Connection {

		private final Socket controlSocket;

		private final List<Socket> dataSockets = new ArrayList<>();

		/**
		 * writes to control channel
		 */
		private final BufferedWriter controlWriter;

		/**
		 * reads from control channel
		 */
		private final BufferedReader controlReader;

		private final Collection<String> features = new HashSet<>();

		private boolean checkClientIP = true;

		private Connection(Socket controlSocket, final String[] features, boolean checkClientIP) throws IOException {
			this.controlSocket = controlSocket;
			controlWriter = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream()));
			controlReader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
			this.features.addAll(Arrays.asList(features));
			this.checkClientIP = checkClientIP;
		}

		public Collection<String> getFeatures(){
			return features;
		}

		/**
		 * establish the connection using the pseudo FTP protocol
		 *
		 * @throws AuthorizationFailureException
		 * @throws ProtocolViolationException
		 * @throws IOException
		 */
		public UFTPBaseRequest establish() throws AuthorizationFailureException, IOException {
			logger.info("Establishing control connection with {}", controlSocket.getRemoteSocketAddress());
			ServerProtocol sp = new ServerProtocol();
			UFTPBaseRequest job = sp.establish(this);
			return job;
		}

		public boolean isCheckClientIP(){
			return checkClientIP;
		}

		public List<Socket> getDataSockets() {
			return dataSockets;
		}

		/**
		 * quietly close all our data connections
		 */
		public void closeData() {
			for (Socket dataSocket : dataSockets) {
				Utils.closeQuietly(dataSocket);
			}
			dataSockets.clear();
		}

		/**
		 * quietly close all sockets
		 */
		public void close() {
			closeData();
			Utils.closeQuietly(controlSocket);
			logger.info("Connection was closed.");
		}

		public InetAddress getAddress() {
			return controlSocket.getInetAddress();
		}

		/**
		 * send a message over the control channel
		 *
		 * @param message - the message to send
		 * @throws java.io.IOException on socket errors
		 */
		public void sendControl(String message) throws IOException {
			logger.debug("--> {}", message);
			controlWriter.write(message+UFTPCommands.NEWLINE);
			controlWriter.flush();
		}

		/**
		 * sends error message (with code 500) over the control connection
		 * 
		 * @param message message to send
		 * @throws IOException thrown upon socket error
		 */
		public void sendError(String message) throws IOException {
			sendControl(UFTPCommands.ERROR+" "+message);
		}

		/**
		 * sends error message (with code) over the control connection
		 * 
		 * @param code - error code
		 * @param message - message to send
		 * @throws IOException thrown upon socket error
		 */
		public void sendError(int code, String message) throws IOException {
			sendControl(code+" "+message);
		}

		/**
		 * read a line from the control connection
		 * 
		 * @return the line or null if end-of-stream
		 * @throws IOException
		 */
		public String readControl() throws IOException {
			String res = controlReader.readLine();
			logger.debug("<-- {}", res);
			return res;
		}

		/**
		 * set the timeout on the control connection
		 *
		 * @param timeout
		 * @throws SocketException
		 */
		public void setControlTimeout(int timeout) throws SocketException {
			controlSocket.setSoTimeout(timeout);
		}

		public void addNewDataConnection(String cmd) throws IOException {
			dataSockets.add(cmd.equals(UFTPCommands.EPSV) ? epsv() : pasv());
		}

		private Socket pasv() throws IOException {
			Socket r = null;
			ServerSocket tmp = null;
			try{
				InetAddress expectedClient = controlSocket.getInetAddress();
				tmp = portManager.getServerSocket();
				if(timeout>0)tmp.setSoTimeout(timeout);
				tmp.setReuseAddress(true);
				int localPort = tmp.getLocalPort();
				// use advertiseAddress if set
				final String ip = (advertiseAddress != null ? advertiseAddress
						: controlSocket.getLocalAddress().getHostAddress())
						.replace('.', ',');
				String outputLine = "227 Entering Passive Mode (" + ip + "," + (localPort / 256) + "," + (localPort % 256) + ")";
				sendControl(outputLine);
				//wait for client to connect
				int attempts = 0;
				while(attempts < 3){
					r = tmp.accept();
					InetAddress peer = ((InetSocketAddress) r.getRemoteSocketAddress()).getAddress();
					if (!expectedClient.equals(peer)) {
						logger.warn("Rejecting unexpected connection from " + peer);
						attempts++;
					}
					else{
						logger.info("Accepted data connection from " + r.getRemoteSocketAddress()+ " using local port "+r.getLocalPort());
						break;
					}
				}
			}finally{
				if(tmp!=null){
					portManager.free(tmp);
				}
			}
			return r;
		}

		private Socket epsv() throws IOException {
			Socket r = null;
			ServerSocket tmp = null;
			try{
				InetAddress expectedClient = controlSocket.getInetAddress();
				tmp = portManager.getServerSocket();
				if(timeout>0)tmp.setSoTimeout(timeout);
				tmp.setReuseAddress(true);
				int localPort = tmp.getLocalPort();

				String outputLine = "229 Entering Extended Passive Mode (|||" + localPort + "|)";
				sendControl(outputLine);

				//wait for client to connect
				int attempts = 0;
				while(attempts < 3){
					r = tmp.accept();
					InetAddress peer = ((InetSocketAddress) r.getRemoteSocketAddress()).getAddress();
					if (!expectedClient.equals(peer)) {
						logger.warn("Rejecting unexpected connection from " + peer);
						attempts++;
					}
					else{
						logger.info("Accepted data connection from " + r.getRemoteSocketAddress()+ " using local port "+r.getLocalPort());
						break;
					}
				}
			}finally{
				if(tmp!=null){
					portManager.free(tmp);
				}
			}
			return r;
		}

		public UFTPBaseRequest getJob(String secret){
			if(secret==null)return null;
			return jobMap.getJob(secret);
		}
	}

}
