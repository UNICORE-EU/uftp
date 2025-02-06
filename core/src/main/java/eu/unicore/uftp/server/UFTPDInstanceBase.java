package eu.unicore.uftp.server;

import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.server.requests.UFTPBaseRequest;
import eu.unicore.uftp.server.requests.UFTPPingRequest;
import eu.unicore.util.Log;

/**
 * Holds properties and parameters for a single UFTPD server,
 * and is used for communicating with the UFTPD.
 *
 * @author schuller
 */
public abstract class UFTPDInstanceBase {

	protected static final Logger log = Log.getLogger(Log.SERVICES, UFTPDInstanceBase.class);
	
	private String host;
	
	private int port;
	
	private String commandHost;
	
	private int commandPort;

	private boolean ssl=true;

	private String description="n/a";

	protected String statusMessage = "N/A";

	protected boolean isUp = false;

	private long lastChecked;

	private final Map<String,String>serverInfo = new HashMap<>();
	
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
	
	protected void checkConnection(){
		if (lastChecked+60000>System.currentTimeMillis())
			return;

		boolean ok = true;
		UFTPPingRequest req = new UFTPPingRequest();
		try{
			String response = doSendRequest(req);
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

	public int getSessionLimit() {
		int limit = -1;
		try {
			limit = Integer.parseInt(serverInfo.getOrDefault("MaxSessionsPerClient", "-1"));
		}catch(Exception ex) {}
		return limit;
	}

	public String getVersion() {
		return serverInfo.getOrDefault("Version", "n/a");
	}

	protected abstract SSLSocketFactory getSSLSocketFactory();

	/**
	 * ping timeout in seconds
	 */
	protected int getPingTimeout(){
		return 20;
	}

	private String doSendRequest(final UFTPBaseRequest request)throws IOException{
		final int timeout = getPingTimeout();
		Callable<String>task = new Callable<>(){
			@Override
			public String call() throws Exception {
				try (Socket socket = ssl ? 
						getSSLSocketFactory().createSocket(commandHost, commandPort):
						new Socket(InetAddress.getByName(commandHost),commandPort))
				{
					socket.setSoTimeout(1000*timeout);
					log.debug("Sending {} request to {}:{}, SSL={}",
							request.getClass().getSimpleName(), commandHost, commandPort, ssl);
					return request.sendTo(socket);
				}
			}
		};
		try{
			return task.call();
		}catch(Exception ie){
			statusMessage = "CAN'T CONNECT TO UFTPD: "+Log.createFaultMessage("Error", ie);
			isUp = false;
			throw new IOException(ie);
		}
	}

}
