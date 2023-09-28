package eu.unicore.uftp.standalone;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.logging.log4j.message.ParameterizedMessage;

import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.dpc.AuthorizationFailureException;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.standalone.authclient.AuthClient;
import eu.unicore.uftp.standalone.authclient.AuthResponse;

/**
 * Helper for creating UFTP Sessions
 *
 * @author jj
 */
public class ClientFacade {

	private final ConnectionInfoManager connectionManager;

	private String group = null;

	private int streams = 1;

	private boolean compress = false;

	private byte[] encryptionKey = null;

	private boolean resume = false;

	// bandwith limit (bytes per second per FTP connection)
	private long bandwidthLimit = -1;

	private String clientIP;

	private boolean verbose = false;

	public ClientFacade(ConnectionInfoManager connectionInfoManager) {
		this.connectionManager = connectionInfoManager;
	}

	public AuthResponse authenticate(String uri) throws Exception {
		connectionManager.init(uri);
		AuthResponse response = initSession(connectionManager.getAuthClient(this));
		if(!response.success){
			throw new AuthorizationFailureException("Failed to successfully authenticate: "+response.reason);
		}
		return response;
	}

	private AuthResponse initSession(AuthClient authClient) throws Exception {
		String baseDir = connectionManager.getBasedir();
		if(connectionManager.getURI().contains("/rest/access/")) {
			baseDir = connectionManager.getPath();
			return authClient.connect(baseDir);
		}
		else {
			return authClient.createSession(baseDir);
		}
	}

	/**
	 * Connects to given server
	 *
	 * @param uri connection details uri
	 * @throws Exception
	 */
	public UFTPSessionClient doConnect(String uri) throws Exception {
		AuthResponse response = authenticate(uri);
		UFTPSessionClient sc = getUFTPClient(response);
		sc.setNumConnections(streams);
		sc.setCompress(compress);
		sc.setKey(encryptionKey);
		sc.setBandwidthLimit(bandwidthLimit);
		sc.connect();
		return sc;
	}
	
	/**
	 * check if we need to re-authenticate - will return a fresh session client if required
	 *
	 * @param uri - UFTP URI
	 * @param sc - existing session client (can be null)
	 * @return session client for accessing the given URI
	 * @throws Exception
	 */
	public UFTPSessionClient checkReInit(String uri, UFTPSessionClient sc) throws Exception {
		if (sc==null 
				|| !connectionManager.isSameServer(uri) 
				|| !connectionManager.currentSessionContains(uri))
		{
			if(sc!=null)sc.close();
			sc = doConnect(uri);
		}
		return sc;
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

	public String getClientIP() {
		return clientIP;
	}

	public void setClientIP(String clientIP) {
		this.clientIP = clientIP;
	}

	public ConnectionInfoManager getConnectionManager(){
		return connectionManager;
	}

	public void setVerbose(boolean verbose){
		this.verbose = verbose;
	}
	
	public UFTPSessionClient getUFTPClient(AuthResponse response) throws UnknownHostException {
		UFTPSessionClient sc = new UFTPSessionClient(getServerArray(response),
				response.serverPort);
		sc.setSecret(response.secret);
		return sc;
	}
	
	InetAddress[] getServerArray(AuthResponse response) throws UnknownHostException {
		return Utils.parseInetAddresses(response.serverHost, null);
	}

	/**
	 * verbose log to console and to the log4j logger
	 * 
	 * @param msg - log4j-style message
	 * @param params - message parameters
	 */
	public void verbose(String msg, Object ... params) {
		if(!verbose)return;
		message(msg, params);
	}

	/**
	 * log to console and to the log4j logger
	 *
	 * @param msg - log4j-style message
	 * @param params - message parameters
	 */
	public void message(String msg, Object ... params) {
		String f = new ParameterizedMessage(msg, params).getFormattedMessage();
		System.out.println(f);
	}
}
