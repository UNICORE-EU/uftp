package eu.unicore.uftp.server;

import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.server.requests.UFTPBaseRequest;
import eu.unicore.uftp.server.requests.UFTPPingRequest;
import eu.unicore.util.Log;
import eu.unicore.util.Pair;

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

	protected String host;

	protected int port;

	protected String commandHost;

	protected int commandPort;

	protected boolean ssl = true;

	protected String description = "n/a";

	// server info received from a ping request
	protected final Map<String,String> serverInfo = new HashMap<>();

	/**
	 * set the address of the FTP socket
	 */
	public void setHost(String host) {
		this.host = host;
	}

	/**
	 * set the port of the FTP socket
	 */
	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * set the address of the FTP socket
	 */
	public void setCommandHost(String commandHost) {
		this.commandHost = commandHost;
	}

	/**
	 * set the port of the FTP socket
	 */
	public void setCommandPort(int commandPort) {
		this.commandPort = commandPort;
	}

	/**
	 * select SSL mode on the command socket
	 */
	public void setSsl(boolean ssl) {
		this.ssl = ssl;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public String toString(){
		return "[UFTPD server: cmd"+(ssl?"(ssl)":"")+"="+commandHost+":"+commandPort+" ftp="+host+":"+port+"]";
	}

	public boolean isOK() { return true; }

	protected void notOK(String errorMsg) {}

	protected void setOK() {}

	/**
	 * @return true/false for the status and a message/error
	 */
	protected Pair<Boolean, String> checkConnection(){
		boolean ok = true;
		String msg = "OK";
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
			msg = Log.getDetailMessage(e);
		}
		return new Pair<>(ok, msg);
	}

	/**
	 * send request via UFTPD control channel
	 * 
	 * @return reply from uftpd
	 * @throws IOException in case of IO errors or timeout
	 */
	public String sendRequest(final UFTPBaseRequest request)throws IOException {
		if(!isOK()){
			throw new IOException("UFTPD server is not available");
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
		try(Socket socket = createSocket())
		{
			socket.setSoTimeout(1000*getReadTimeout());
			log.debug("Sending {} request to {}:{}, SSL={}",
					request.getClass().getSimpleName(), commandHost, commandPort, ssl);
			String reply = request.sendTo(socket);
			setOK();
			return reply;
		}catch(IOException e) {
			notOK(Log.getDetailMessage(e));
			throw e;
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
