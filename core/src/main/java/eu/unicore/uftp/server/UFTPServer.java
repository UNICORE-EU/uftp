package eu.unicore.uftp.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.RejectedExecutionException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.dpc.DPCServer;
import eu.unicore.uftp.dpc.UFTPConstants;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.server.requests.UFTPBaseRequest;
import eu.unicore.uftp.server.requests.UFTPGetUserInfoRequest;
import eu.unicore.uftp.server.requests.UFTPPingRequest;
import eu.unicore.uftp.server.requests.UFTPRequestBuilderRegistry;
import eu.unicore.uftp.server.requests.UFTPTransferRequest;
import eu.unicore.uftp.server.unix.UnixUser;
import eu.unicore.util.Log;
import eu.unicore.util.configuration.ConfigurationException;

/**
 * Server for sending/receiving data using dynamic port opening in firewalls via
 * a pseudo-FTP connection.
 * 
 * A {@link ServerThread} is started, which waits for incoming client requests.
 * At the same time, the server listens on a command-socket, waiting for
 * {@link UFTPBaseRequest} requests. Client requests are linked to jobs,
 * i.e. only clients announced via a Job are accepted. <br/>
 * Authorization:<br/>
 * Each job has an associated "secret", which the client has to know.
 * 
 * @author Tim Pohlmann, 2010, Forschungszentrum Juelich - JSC
 * @author schuller
 */
public class UFTPServer implements Runnable {

	private static final Logger logger = Utils.getLogger(Utils.LOG_SERVER, UFTPServer.class);

	private static String VER = UFTPServer.class.getPackage().getImplementationVersion();
	/**
	 * Error codes
	 */
	public final static int SYNERR = 1;
	public final static int LISTENSOCKERR = 2;
	public final static int THREADERR = 3;
	public final static int CMDSOCKERR = 4;
	/**
	 * Backlog for the server sockets
	 */
	public final static int BACKLOG = 50;
	private final InetAddress cmdip;
	private final InetAddress srvip;
	private final int cmdport;
	private final int srvport;
	private final PortManager portManager;
	private final boolean checkClientIP;
	//timeout on the control connection
	int timeout = 30000;
	//timeout when reading jobs
	int jobReadTimeout = 15000;
	//limit on streams per client
	int maxStreams = 8;
	//limit on control connections per client IP
	int maxControlConnectionsPerClient = 16;
	//buffersize for reading/writing files
	int bufferSize = FileAccess.DEFAULT_BUFFERSIZE;
	private ServerThread svrThread;
	private final SSLHelper sslHelper;
	private volatile boolean stopped = false;
	private String advertiseAddress;
	private String[] keyFileList;
	
	public UFTPServer(InetAddress cmdip, int cmdport, InetAddress srvip,
			int srvport, String advertiseAddress, PortManager portManager, boolean checkClientIP) throws IOException {
		this.cmdip = cmdip;
		this.cmdport = cmdport;
		this.srvip = srvip;
		this.srvport = srvport;
		this.advertiseAddress = advertiseAddress;
		this.portManager = portManager;
		this.sslHelper = new SSLHelper();
		this.checkClientIP = checkClientIP;
	}

	/**
	 * @param host
	 * @param jobPort
	 * @param host2
	 * @param srvPort2
	 * @throws IOException
	 */
	public UFTPServer(InetAddress host, int jobPort, InetAddress host2,
			int srvPort2) throws IOException {
		this(host, jobPort, host2, srvPort2, null, null, true);
	}

	@Override
	public void run() {
		try {
			svrThread = new ServerThread(srvip, srvport, BACKLOG, maxStreams,
					advertiseAddress, portManager, checkClientIP);
		} catch (IOException ex) {
			logger.error("Error starting 'ftp' listen socket. Please check the parameters for host and port.",ex);
			System.exit(LISTENSOCKERR);
		}
		svrThread.setTimeout(timeout);
		svrThread.setMaxControlConnectionsPerClient(maxControlConnectionsPerClient);
		svrThread.setBufferSize(bufferSize);

		svrThread.start();
		logger.info("UFTPD Listener server socket started on " + srvip.getHostName() + ":" + srvport);

		ServerSocket cmdSocket = null;
		try {
			cmdSocket = sslHelper.createCommandSocket(cmdport,BACKLOG,cmdip);
			//TODO set timeout values
			logger.info("UFTPD Command server socket started on " + cmdip.getHostName() + ":" + cmdport);
		} catch (IOException e) {
			logger.error("Error starting command socket. Please check the parameters for command host and port.", e);
			System.exit(CMDSOCKERR);
		}
		logger.info("Maximum connections per client: " + maxControlConnectionsPerClient);
		logger.info("Maximum streams per connection: " + maxStreams);
		logger.info("File buffer size per client: " + bufferSize + " kB");
		logger.info("Client IP check is " + (checkClientIP ? "ENABLED" : "DISABLED"));

		// setup key file list for getUserinfo()
		String fileListS = Utils.getProperty(UFTPConstants.ENV_UFTP_KEYFILES, null);
		if(fileListS==null || fileListS.isEmpty()) {
			fileListS = ".ssh/authorized_keys";
		}
		try {
			keyFileList = fileListS.split(":");
		}catch(Exception ex) {
			throw new ConfigurationException("Cannot parse file list from UFTP_KEYFILES", ex);
		}
		for(String s: keyFileList) {
			logger.info("Reading user's keys from $HOME/"+s);
		}
		while (!stopped) {
			Socket jobSocket = null;
			try {
				jobSocket = cmdSocket.accept();
				try {
					logger.debug("New control connection from " + jobSocket.getInetAddress());
					jobSocket.setSoTimeout(jobReadTimeout);
					jobSocket.setKeepAlive(true);
					sslHelper.checkAccess(jobSocket);

					JobConnectionWorker jcw = new JobConnectionWorker(jobSocket, svrThread);
					Utils.getExecutor().execute(jcw);

				} catch (RejectedExecutionException ree) {
					handleErrorAndClose(jobSocket, "", ree);
				} catch (IOException e) {
					handleErrorAndClose(jobSocket, "Error receiving job", e);
				}
			}catch(java.net.SocketTimeoutException ste) {
				// "Accept timed out" will happen regularly  - ignore it
			}
			catch(IOException ioe) {
				Log.logException("Error accepting new control connection", ioe, logger);
			}
		}
		logger.info("Exiting UFTP server.");
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}
	
	public void setCheckClientIP(boolean checkIP){
		if(svrThread!=null)svrThread.setCheckClientIP(checkIP);
	}

	public void setRFCRangeMode(boolean rfcMode){
		if(svrThread!=null)svrThread.setRFCRangeMode(rfcMode);
	}
	
	/**
	 * stops the server. Both command and listener sockets are closed.
	 */
	public void stop() {
		if (!stopped) {
			if (svrThread != null) {
				try {
					svrThread.close();
				} catch (Exception ex) {
					logger.error("Error stopping", ex);
				}
				svrThread.interrupt();
			}
			stopped = true;

		}
	}

	/**
	 * starts a server reading params from commandline args
	 */
	public static void main(String[] args) {
		Options options = createOptions();
		CommandLineParser parser = new GnuParser();
		CommandLine line = null;
		try {
			line = parser.parse(options, args);
		} catch (ParseException pe) {
			System.out.println("Unable to parse options: " + pe.getLocalizedMessage());
			printUsage(options);
			System.exit(SYNERR);
		}

		printHeader();

		InetAddress cmdip, srvip;
		int cmdport, srvport;
		String advertiseAddress;
		boolean checkClientIP;
		UFTPServer server = null;

		try {
			cmdip = InetAddress.getByName(line.getOptionValue("c"));
			cmdport = Integer.parseInt(line.getOptionValue("p"));
			srvip = InetAddress.getByName(line.getOptionValue("l"));
			srvport = Integer.parseInt(line.getOptionValue("L"));
			advertiseAddress = line.getOptionValue("a");
			String portRange = line.getOptionValue("P");
			PortManager pm = createPortManager(portRange);
			checkClientIP = !line.hasOption("I");
			server = new UFTPServer(cmdip, cmdport, srvip, srvport, advertiseAddress, pm, checkClientIP);
			server.maxControlConnectionsPerClient = Integer.parseInt(line.getOptionValue("m", "16"));
			server.maxStreams = Integer.parseInt(line.getOptionValue("s", "8"));
			server.bufferSize = Integer.parseInt(line.getOptionValue("b", "128")) * 1024;

			//TODO timeouts

		} catch (Exception e) {
			System.out.println("Invalid option detected: " + e.getLocalizedMessage());
			printUsage(options);
			System.exit(SYNERR);
		}
		if (server != null) {
			server.run();
		}
	}

	private static PortManager createPortManager(String portRange){
		PortManager pm;
		if(portRange != null){
			String[]tokens = portRange.split(":");
			int lower = Integer.parseInt(tokens[0]);
			int upper = Integer.parseInt(tokens[1]);
			pm = new PortManager(lower, upper);
			logger.info("Using local ports "+portRange+" for data connections.");
		} else {
			pm = new PortManager();
		}
		return pm;
	}

	@SuppressWarnings("static-access")
	public static Options createOptions() {
		Options options = new Options();

		options.addOption(
				OptionBuilder.withLongOpt("listen-host")
				.withDescription("Hostname of the listen socket")
				.withArgName("Listen host")
				.hasArg()
				.isRequired(true)
				.create("l"));
		options.addOption(OptionBuilder
				.withLongOpt("advertise-host")
				.withDescription(
						"Host to advertise in place of listen-host. Useful for servers behind NAT.")
				.withArgName("Advertise host").hasArg().isRequired(false)
				.create("a"));
		options.addOption(OptionBuilder.withLongOpt("listen-port")
				.withDescription("Port of the listen socket")
				.withArgName("Listen port")
				.hasArg()
				.isRequired(true)
				.create("L"));
		options.addOption(
				OptionBuilder.withLongOpt("command-host")
				.withDescription("Hostname of the command socket")
				.withArgName("Command host")
				.hasArg()
				.isRequired(true)
				.create("c"));
		options.addOption(
				OptionBuilder.withLongOpt("command-port")
				.withDescription("Port of the command socket")
				.withArgName("Command port")
				.hasArg()
				.isRequired(true)
				.create("p"));
		options.addOption(
				OptionBuilder.withLongOpt("max-connections")
				.withDescription("Limit on the number of connections per client IP (default: 32)")
				.withArgName("Control connections")
				.hasArg()
				.isRequired(false)
				.create("m"));
		options.addOption(
				OptionBuilder.withLongOpt("max-streams")
				.withDescription("Limit on the number of parallel data streams per client (default: 8)")
				.withArgName("Stream limit")
				.hasArg()
				.isRequired(false)
				.create("s"));
		options.addOption(
				OptionBuilder.withLongOpt("buffersize")
				.withDescription("Buffer size in kbytes for reading/writing files (default: 128)")
				.withArgName("bufferSize")
				.hasArg()
				.isRequired(false)
				.create("b"));
		options.addOption(
				OptionBuilder.withLongOpt("portrange")
				.withDescription("Port range for data connections in the form 'lower:upper' (default: any port can be used)")
				.withArgName("portRange")
				.hasArg()
				.isRequired(false)
				.create("P"));
		options.addOption(
				OptionBuilder.withLongOpt("no-check-client-ip")
				.withDescription("Disable check if client's IP matches the requested one")
				.isRequired(false)
				.create("I"));
		return options;
	}

	public static String getVersion() {
		if (VER == null) {
			VER = "DEVELOPMENT";
		}
		return VER;
	}

	public static void printHeader() {
		String message = "**** UFTPD Version "+getVersion()+ " starting";
		logger.info(message);
	}

	public static void printUsage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		String syntax = "UFTPD [OPTIONS]" + System.getProperty("line.separator");
		formatter.printHelp(syntax, options);
	}


	public String getServerInfo() {
		StringBuilder sb = new StringBuilder();
		sb.append("Version: ").append(getVersion());
		sb.append("\nMaxStreamsPerClient: ").append(maxStreams);
		if(advertiseAddress!=null){
			sb.append("\nAdvertiseAddress: ").append(advertiseAddress);
		}
		sb.append("\nListenPort: ").append(srvport);
		sb.append("\nListenAddress: ").append(srvip.getHostAddress());
		sb.append("\nFeatures: ").append(Arrays.asList(DPCServer.getFeatures()));
		return sb.toString();
	}

	
	
	public String getUserInfo(String username) {

		StringBuilder sb = new StringBuilder();
		sb.append("Version: ").append(getVersion());
		sb.append("\nUser: ").append(username);
		if(svrThread.getFileAccess() instanceof SetUIDFileAccess){
			try{
				UnixUser uu = new UnixUser(username);
				sb.append("\nStatus: OK");
				String home = uu.getHome();
				sb.append("\nHome: "+home);
				// get the accepted ssh keys
				int i = 0;
				for(String keyFile: keyFileList) {
					try{
						String path = home+"/"+keyFile;
						InputStream in = svrThread.getFileAccess().readFile(path, 
								uu.getLoginName(), null, FileAccess.DEFAULT_BUFFERSIZE); 
						List<String> auth_keys = null;
						try{
							auth_keys = IOUtils.readLines(in, "UTF-8");
						}catch(Exception ex) {
							auth_keys = IOUtils.readLines(in, "US-ASCII");
						}
						for(String key: auth_keys){
							if(key.startsWith("#"))continue;
							sb.append("\nAccepted key "+i+": "+key);
							i++;
						}
					}
					catch(Exception ex){}
				}
			}catch(IllegalArgumentException ex){
				sb.append("\nStatus: Error - no such user!");
			}
		}
		else{
			sb.append("\nStatus: n/a");
		}
		return sb.toString();
	}

	private void handleErrorAndClose(Socket jobSocket, String error, Throwable e) {
		logger.error(error, e);
		Utils.closeQuietly(jobSocket);
	}

	public class JobConnectionWorker implements Runnable {

		private static final String PROP_REQUEST_TYPE = "request-type";
		final Socket jobSocket;
		final ServerThread serverThread;

		public JobConnectionWorker(Socket jobSocket, ServerThread serverThread) {
			this.jobSocket = jobSocket;
			this.serverThread = serverThread;
		}

		@Override
		public void run() {
			try {
				String jobString;
				BufferedReader br = new BufferedReader(new InputStreamReader(jobSocket.getInputStream()));
				StringBuilder sb = new StringBuilder();
				while (true) {
					String line = br.readLine();
					if (line == null || line.length() == 0 || line.equals(UFTPCommands.END)) {
						break;
					} else {
						sb.append(line).append("\n");
					}
				}
				jobString = sb.toString();
				if(logger.isDebugEnabled())logger.debug("CMD ==> " + jobString);
				String response = "OK::"+String.valueOf(serverThread.getPort());
				try {
					Properties props = UFTPBaseRequest.loadProperties(jobString);
					String requestType = props.getProperty(PROP_REQUEST_TYPE, UFTPTransferRequest.REQUEST_TYPE);
					UFTPRequestBuilder builder = UFTPRequestBuilderRegistry.INSTANCE.getBuilder(requestType);
					UFTPBaseRequest request = builder.createInstance(props);
					if(UFTPPingRequest.REQUEST_TYPE.equals(requestType)){
						response = getServerInfo();
					}
					else if(UFTPGetUserInfoRequest.REQUEST_TYPE.equals(requestType)){
						response = getUserInfo(props.getProperty("user"));
					}
					else{
						svrThread.addJob(request);
					}
				} catch (Exception e) {
					response = UFTPCommands.ERROR+"::"+Utils.createFaultMessage("Request rejected. Reason: ", e);
					logger.error("Error processing job: " + jobString, e);
				}
				writeResponse(response, jobSocket.getOutputStream());
			} catch (IOException ioe) {
				logger.info("Error processing job connection.", ioe);
			} finally {
				Utils.closeQuietly(jobSocket);
			}
		}

		void writeResponse(String msg, OutputStream os) throws IOException {
			OutputStreamWriter ow = new OutputStreamWriter(os, "UTF-8");
			ow.write(msg + "\n");
			ow.flush();
			if(logger.isDebugEnabled())logger.debug("CMD <== "+msg);
		}
	}
}
