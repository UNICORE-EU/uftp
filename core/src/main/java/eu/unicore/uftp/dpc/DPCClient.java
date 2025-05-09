package eu.unicore.uftp.dpc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.server.UFTPCommands;
import eu.unicore.util.Log;

/**
 * Internal client class handling the low-level FTP operations,
 * like establishing the control connection and opening data connections.
 * This class is usually not used directly by end-user code.
 *
 * @author Tim Pohlmann, 2010, Forschungszentrum Juelich - JSC
 * @author schuller
 */
public final class DPCClient implements Closeable {

	private static final Logger logger = Utils.getLogger(Utils.LOG_CLIENT, DPCClient.class);

	private int timeout;

	private Socket controlSocket;

	private final List<Socket> dataSockets = new ArrayList<>();

	private volatile boolean connected = false;

	private BufferedWriter controlWriter;

	private BufferedReader controlReader;

	private final ClientProtocol protocol;

	private final List<String> features = new ArrayList<>();

	// data connection buffer size - taken from environment UFTP_SO_BUFSIZE
	private int so_buffer_size;

	public DPCClient(){
		this.protocol = new ClientProtocol(this);
		so_buffer_size = Integer.parseInt(Utils.getProperty("UFTP_SO_BUFSIZE", "-1"));
	}

	/**
	 * establishes an authorised pseudo FTP connection. Only a single connection
	 * to a server can be open at the same time. A connection can be closed
	 * using close().
	 *
	 * @param server - server addresses
	 * @param port - server port
	 * @param secret - the authentication secret
	 * @throws IOException
	 * @throws ProtocolViolationException
	 * @throws AuthorizationFailureException
	 */
	public void connect(InetAddress[] server, int port, String secret) throws IOException,
	AuthorizationFailureException {
		if (connected) {
			throw new IllegalStateException("Already connected");
		}
		ProxyType proxyType = ProxyType.NONE;
		if(usingProxy()) {
			InetAddress proxy = InetAddress.getByName(ftpProxyHost);
			openControlSocket(new InetAddress[] {proxy}, ftpProxyPort);
			String reply = protocol.initialHandshake();
			logger.debug(reply);
			proxyType = determineProxyType(reply);
			if (ProxyType.DELEGATE==proxyType) {
				protocol.login("USER anonymous", ftpProxyPass);
			}
			protocol.login("user anonymous@"+server[0].getHostAddress()+":"+port, secret);
			features.addAll(getFroxFeatures());
		}
		else {
			openControlSocket(server, port);
			protocol.initialHandshake();
			protocol.login("USER anonymous", secret);
		}
		if(proxyType!=ProxyType.FROX) {
			features.addAll(protocol.checkFeatures());
		}
		connected = true;
		logger.info("Connection established.");
	}
	
	String ftpProxyHost;
	int ftpProxyPort;
	String ftpProxyUser = "anonymous";
	String ftpProxyPass = "";

	private boolean usingProxy() {
		ftpProxyHost = Utils.getProperty(UFTPConstants.ENV_UFTP_PROXY, null);
		if(ftpProxyHost==null)return false;
		ftpProxyPort = Integer.parseInt(Utils.getProperty(UFTPConstants.ENV_UFTP_PROXY_PORT, "21"));
		ftpProxyUser = Utils.getProperty(UFTPConstants.ENV_UFTP_PROXY_USER, "anonymous");
		ftpProxyPass = Utils.getProperty(UFTPConstants.ENV_UFTP_PROXY_PASS, "");
		return true;
	}

	static enum ProxyType {
		NONE, DELEGATE, FROX	
	}

	private ProxyType determineProxyType(String serverInfo) {
		if(serverInfo.contains("DeleGate"))return ProxyType.DELEGATE;
		if(serverInfo.contains("Frox"))return ProxyType.FROX;
		return ProxyType.FROX;
	}

	private void openControlSocket(InetAddress[] server, int port) throws IOException,
	AuthorizationFailureException {
		InetAddress selectedServer = null;
		StringBuilder errors = new StringBuilder();
		// try to connect to one of the given server IPs, avoiding overly long timeouts
		int tries = 0;
		List<InetAddress> servers = new ArrayList<>();
		servers.addAll(Arrays.asList(server));

		outer:
			while (servers.size() > 0 && tries < 3) {
				tries++;
				int currentTimeoutValue = tries * timeout;
				Iterator<InetAddress> iter = servers.iterator();
				while (iter.hasNext()) {
					InetAddress s = iter.next();
					try {
						controlSocket = new Socket();
						logger.debug("Attempt to connect to {}", s.getHostAddress());
						controlSocket.connect(new InetSocketAddress(s, port), currentTimeoutValue);
						controlSocket.setKeepAlive(true);
						selectedServer = s;
						break outer;
					} catch (SocketTimeoutException ste) {
						// probably some firewall thing, will retry with a longer timeout to be sure
					} catch (Exception ex) {
						// this should take care of "cleaner" network level errors
						iter.remove();
						Utils.closeQuietly(controlSocket);
						errors.append("[").append(s).append(": ").append(Log.createFaultMessage(s.toString(), ex)).append("]");
					}
				}
			}
		if (selectedServer == null) {
			throw new IOException("Can't connect to server(s) " + Arrays.asList(server) + " at port " + port + ". Error message " + errors);
		}
		controlWriter = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream()));
		controlReader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
		logger.info("FTP control connection established to {}:{}", selectedServer.getHostAddress(), port);
	}

	/**
	 * opens data connections
	 *
	 * @param numParCons number of parallel connections
	 * @return socket list
	 * @throws IOException
	 * @throws ProtocolViolationException
	 */
	public List<Socket>openDataConnections(int numParCons) throws IOException {
		checkConnected();
		logger.info("Opening <{}> data connection(s).", numParCons);
		if (dataSockets.size() > 0) {
			throw new IllegalStateException("There are already open data connections.");
		}
		//send numParCons using "NOOP <n>"
		String noopMsg = "NOOP " + numParCons ;
		sendControl(noopMsg);
		String noopResponse = readControl();
		if (noopResponse.startsWith("223")) {
			// adjust our number of connections since server may have imposed a limit
			numParCons = Integer.parseInt(noopResponse.split(" ")[2]);
			logger.info("Server limit: <{}> data connection(s).", numParCons);
		}
		//open data connections
		for (int i = 0; i < numParCons; i++) {
			dataSockets.add(getNewConnection());
		}
		return dataSockets;
	}

	/**
	 * open new data connection
	 *
	 * @return newly opened socket
	 * @throws IOException
	 */
	public Socket getNewConnection() throws IOException {
		return features.contains(UFTPCommands.EPSV) ? epsv() : pasv();
	}

	private static final Pattern epsv_response = Pattern.compile("(\\d+)\\D+(\\d+).*"); 

	private Socket epsv() throws IOException {
		sendControl(UFTPCommands.EPSV);
		String inputLine = readControl();
		Matcher m = epsv_response.matcher(inputLine);
		if(m.matches()){
			try{
				int code = Integer.parseInt(m.group(1));
				if(229==code){
					int port = Integer.parseInt(m.group(2));
					SocketChannel sChannel = SocketChannel.open();
					sChannel.connect(new InetSocketAddress(controlSocket.getInetAddress(), port));
					Socket s = sChannel.socket();
					if(so_buffer_size>0) {
						try {
							s.setSendBufferSize(so_buffer_size);
							s.setReceiveBufferSize(so_buffer_size);
						}catch(SocketException e) {}
					}
					return s;
				}
			}
			catch(NumberFormatException ex){
				throw new IOException("Could not parse response from server: <"+inputLine+">");
			}
		}
		throw new IOException("Could not parse response from server: <"+inputLine+">");	
	}

	private Socket pasv() throws IOException {
		sendControl(UFTPCommands.PASV);
		String inputLine = readControl();
		String[] inputString = inputLine.split(" ")[4].substring(1).split(",");	//"i1,i2,i3,i4,p1,p2)".split(",")
		InetAddress dataAddress = InetAddress.getByName(inputString[0] + "." + inputString[1] + "." + inputString[2]
				+ "." + inputString[3]);
		int dataPort = Integer.parseInt(inputString[4]) * 256 + //get control port (p1 * 256 + p2) 
				Integer.parseInt(inputString[5].substring(0, inputString[5].length() - 1));
		SocketChannel sChannel = SocketChannel.open();
		sChannel.connect(new InetSocketAddress(dataAddress, dataPort));
		return sChannel.socket();
	}

	/**
	 * close all data connections
	 *
	 * @throws IOException
	 */
	public void closeData() {
		for (Socket dataSocket : dataSockets) {
			Utils.closeQuietly(dataSocket);
		}
		dataSockets.clear();
	}

	/**
	 * close all
	 *
	 * @throws IOException
	 */
	public void close() throws IOException {
		closeData();
		Utils.closeQuietly(controlSocket);
		connected = false;
		logger.info("Connection closed.");
	}

	public boolean isConnected() {
		return connected;
	}

	public List<String>getServerFeatures(){
		checkConnected();
		return features;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	/**
	 * send a message over the control channel
	 *
	 * @param message - the message to send
	 * @throws java.io.IOException when sending failed
	 */
	public void sendControl(String message) throws IOException {
		logger.debug("--> {}", message);
		controlWriter.write(message + "\r\n");
		controlWriter.flush();
	}

	/**
	 * read a single line from the control channel
	 */
	public String readControl() throws IOException {
		String res = controlReader.readLine();
		logger.debug("<-- {}", res);
		return res;
	}

	private void checkConnected() {
		if (!connected) {
			throw new IllegalStateException("Not connected");
		}
	}

	private List<String> getFroxFeatures(){
		return Arrays.asList(new String[]{ UFTPCommands.PASV,
				UFTPCommands.RANGSTREAM, 
				UFTPCommands.MFMT,
				UFTPCommands.MLSD,
				UFTPCommands.APPE,
				UFTPCommands.ARCHIVE,
		});
	}

}
