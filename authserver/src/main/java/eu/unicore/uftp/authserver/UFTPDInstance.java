package eu.unicore.uftp.authserver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.concurrent.Callable;

import javax.net.ssl.SSLSocketFactory;

import org.apache.logging.log4j.Logger;

import eu.emi.security.authn.x509.impl.SocketFactoryCreator2;
import eu.unicore.services.ExternalSystemConnector;
import eu.unicore.services.Kernel;
import eu.unicore.uftp.server.requests.UFTPBaseRequest;
import eu.unicore.uftp.server.requests.UFTPPingRequest;
import eu.unicore.util.Log;
import eu.unicore.util.httpclient.HostnameMismatchCallbackImpl;
import eu.unicore.util.httpclient.IClientConfiguration;
import eu.unicore.util.httpclient.ServerHostnameCheckingMode;

/**
 * Holds properties and parameters for a single UFTPD server,
 * and is used for communicating with the UFTPD.
 *
 * @author schuller
 */
public class UFTPDInstance implements ExternalSystemConnector {

	public static final Logger log = Log.getLogger(Log.SERVICES, UFTPDInstance.class);
	
	private String host;
	
	private int port;
	
	private String commandHost;
	
	private int commandPort;

	private boolean ssl=true;

	private String description="n/a";

	private final String serverName;
	
	private String logicalName;
	
	private String statusMessage = "N/A";

	private Status status = Status.UNKNOWN;

	private long lastChecked;

	private final Kernel kernel;
	
	public UFTPDInstance(String serverName, Kernel kernel){
		this.serverName = serverName;
		this.logicalName = serverName;
		this.kernel = kernel;
	}

	public void setLogicalName(String name) {
		this.logicalName = name;
	}
	
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

	public String getName(){
		return logicalName;
	}
	
	public String getServerName(){
		return serverName;
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
	
	public Status getConnectionStatus() {
		checkConnection();
		return status;
	}
	
	public String getExternalSystemName() {
		return  "UFTPD server '"+serverName+"'";
	}
	
	public String toString(){
		return "[UFTPD server '"+serverName+"': cmd"+(ssl?"(ssl)":"")+"="+commandHost+":"+commandPort+" listen="+host+":"+port+"]";
	}
	
	public boolean isUFTPAvailable(){
		checkConnection();
		return Status.OK.equals(status);
	}
	
	private void checkConnection(){
		if (!Status.OK.equals(status) && (lastChecked+60000>System.currentTimeMillis()))
			return;

		boolean ok = true;
		UFTPPingRequest req = new UFTPPingRequest();
		try{
			doSendRequest(req);
		}
		catch(IOException e){
			ok = false;
			String err = Log.createFaultMessage("Error", e);
			statusMessage="CAN'T CONNECT TO UFTPD "+commandHost+":"+commandPort+" ["+err+"]";
		}
		if(ok){
			status = Status.OK;
			statusMessage="OK [connected to UFTPD "+commandHost+":"+commandPort+"]";
		}
		else {
			status = Status.DOWN;
		}
		lastChecked=System.currentTimeMillis();
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

	private static SSLSocketFactory socketfactory = null;
	
	private synchronized SSLSocketFactory getSSSSocketFactory() {
		if(socketfactory==null) {
			IClientConfiguration cfg = kernel.getClientConfiguration();
			socketfactory = new SocketFactoryCreator2(cfg.getCredential(), cfg.getValidator(), 
					new HostnameMismatchCallbackImpl(ServerHostnameCheckingMode.NONE),
					getRandom(), "TLS").getSocketFactory();
		}
		return socketfactory;
	}
	
	
	private String doSendRequest(final UFTPBaseRequest request)throws IOException{

		final int timeout = 20 * 1000;

		Callable<String>task=new Callable<String>(){
			@Override
			public String call() throws Exception {
				Socket socket=null;
				if(!ssl){
					socket=new Socket(InetAddress.getByName(commandHost),commandPort);
					socket.setSoTimeout(timeout);
				}
				else{
					socket = getSSSSocketFactory().createSocket(commandHost, commandPort);
					socket.setSoTimeout(timeout);
				}
				if(log.isDebugEnabled()){
					log.debug("Sending "+request.getClass().getSimpleName()+" request to "
							+commandHost+":"+commandPort+", SSL="+ssl);
				}
				try {
					return request.sendTo(socket);
				} finally {
					try{
						socket.close();
					}catch(IOException ex) {}
				}
			}
		};
		try{
			return task.call();
		}catch(Exception ie){
			String err = Log.createFaultMessage("Error", ie);
			statusMessage = "CAN'T CONNECT TO UFTPD "+commandHost+":"+commandPort+" ["+err+"]";
			status = Status.DOWN;
			throw new IOException(ie);
		}
	}

	private static SecureRandom random=null;

	private synchronized SecureRandom getRandom(){
		if(random==null){
			random=new SecureRandom();
		}
		return random;
	}
}
