package eu.unicore.uftp.server;

import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.server.requests.UFTPBaseRequest;
import eu.unicore.uftp.server.requests.UFTPPingRequest;
import eu.unicore.util.Log;

/**
 * Base class for communicating with the command channel of
 * a single UFTPD server. It holds properties and parameters
 * that server, including the FTP host/port, and checks the
 * status of the server.
 *
 * @author schuller
 */
public abstract class UFTPDInstanceBase {

	protected static final Logger log = Log.getLogger(Log.SERVICES, UFTPDInstanceBase.class);

	private String host;

	private int port;

	private String commandHost;

	private int commandPort;

	private boolean ssl = true;

	private String description = "n/a";

	protected volatile String statusMessage = "N/A";

	protected volatile boolean isUp = false;

	private volatile long lastChecked;

	// server info received from a ping request
	protected final Map<String,String> serverInfo = new HashMap<>();

	/**
	 * the address of the FTP socket
	 */
	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	/**
	 * the port of the FTP socket
	 */
	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getCommandHost() {
		return commandHost;
	}

	public void setCommandHost(String commandHost) {
		this.commandHost = commandHost;
	}

	public int getCommandPort() {
		return commandPort;
	}

	public void setCommandPort(int commandPort) {
		this.commandPort = commandPort;
	}

	public boolean isSsl() {
		return ssl;
	}

	public void setSsl(boolean ssl) {
		this.ssl = ssl;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getConnectionStatusMessage(){
		checkConnection();
		return statusMessage;
	}

	public String toString(){
		return "[UFTPD server: cmd"+(ssl?"(ssl)":"")+"="+commandHost+":"+commandPort+" ftp="+host+":"+port+"]";
	}

	public boolean isUFTPAvailable(){
		checkConnection();
		return isUp;
	}

	private final AtomicBoolean pingInProgress = new AtomicBoolean(false);

	protected void checkConnection(){
		if (pingInProgress.get() || (lastChecked+60000>System.currentTimeMillis()))
			return;
		pingInProgress.set(true);
		try {
			boolean ok = true;
			try{
				String response = doSendRequest(new UFTPPingRequest());
				for(String line: IOUtils.readLines(new StringReader(response))){
					if(!line.contains(":"))continue;
					try {
						String[]tok = line.split(":", 2);
						serverInfo.put(tok[0].trim(), tok[1].trim());
					}catch(Exception e) {}
				}
			}
			catch(IOException e){
				ok = false;
				statusMessage="CAN'T CONNECT TO UFTPD: "+Log.createFaultMessage("Error", e);
			}
			if(ok){
				isUp = true;
				statusMessage="UFTPD connection OK";
			}
			else {
				isUp = false;
			}
		}
		finally {
			lastChecked = System.currentTimeMillis();
			pingInProgress.set(false);
		}
	}

	/**
	 * send request via UFTPD control channel
	 * 
	 * @return reply from uftpd
	 * @throws IOException in case of IO errors or timeout
	 */
	public String sendRequest(final UFTPBaseRequest request)throws IOException {
		if(!isUFTPAvailable()){
			throw new IOException(statusMessage);
		}
		return doSendRequest(request);
	}

	public int getSessionLimit() {
		return Integer.parseInt(serverInfo.getOrDefault("MaxSessionsPerClient", "-1"));
	}

	public String getVersion() {
		return serverInfo.getOrDefault("Version", "n/a");
	}

	protected abstract SSLSocketFactory getSSLSocketFactory();

	/**
	 * socket read timeout in seconds
	 */
	protected int getReadTimeout(){
		return 60;
	}

	/**
	 * connection timeout in seconds
	 */
	protected int getConnectTimeout(){
		return 10;
	}

	private String doSendRequest(final UFTPBaseRequest request)throws IOException{
		try{
			try(Socket socket = createSocket())
			{
				socket.setSoTimeout(1000*getReadTimeout());
				log.debug("Sending {} request to {}:{}, SSL={}",
						request.getClass().getSimpleName(), commandHost, commandPort, ssl);
				return request.sendTo(socket);
			}
		}catch(Exception ie){
			statusMessage = "CAN'T CONNECT TO UFTPD: "+Log.createFaultMessage("Error", ie);
			isUp = false;
			throw new IOException(ie);
		}
	}

	/**
	 * create connected socket to the control port
	 * will timeout if the connection cannot be made in the
	 * time configured via getConnectTimeout()
	 */
	protected Socket createSocket() throws IOException{
		Socket s = ssl? getSSLSocketFactory().createSocket() : new Socket();
		s.connect(new InetSocketAddress(commandHost, commandPort), 1000*getConnectTimeout());
		return s;
	}

}
