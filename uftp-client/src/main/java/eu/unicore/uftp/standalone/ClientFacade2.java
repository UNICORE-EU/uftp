package eu.unicore.uftp.standalone;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.dpc.AuthorizationFailureException;
import eu.unicore.uftp.standalone.authclient.AuthClient;
import eu.unicore.uftp.standalone.authclient.AuthResponse;
import eu.unicore.util.Log;

/**
 * Helper for creating UFTP Sessions
 *
 * @author jj
 */
public class ClientFacade2 {

	private static final Logger logger = Log.getLogger(Log.CLIENT+".uftp");

	private final ConnectionInfoManager connectionManager;

	private final UFTPClientFactory factory;

	private String group = null;

	private int streams = 1;

	private boolean compress = false;

	private byte[] encryptionKey = null;

	private boolean resume = false;

	// bandwith limit (bytes per second per FTP connection)
	private long bandwidthLimit = -1;

	private String clientIP;

	private boolean verbose = false;

	public ClientFacade2(ConnectionInfoManager connectionInfoManager, UFTPClientFactory clientFactory) {
		this.connectionManager = connectionInfoManager;
		this.factory = clientFactory;
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
		return authClient.createSession(connectionManager.getBasedir());
	}

	/**
	 * Connects to given server
	 *
	 * @param uri connection details uri
	 * @throws Exception
	 */
	public UFTPSessionClient doConnect(String uri) throws Exception {
		verbose("Connecting to {}", uri);
		AuthResponse response = authenticate(uri);
		UFTPSessionClient sc = factory.getUFTPClient(response);
		sc.setNumConnections(streams);
		sc.setCompress(compress);
		sc.setKey(encryptionKey);
		sc.setBandwidthLimit(bandwidthLimit);
		sc.connect();
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
	
	/**
	 * verbose log to console and to the log4j logger
	 * 
	 * @param msg - log4j-style message
	 * @param params - message parameters
	 */
	public void verbose(String msg, Object ... params) {
		logger.debug(msg, params);
		if(!verbose)return;
		String f = logger.getMessageFactory().newMessage(msg, params).getFormattedMessage();
		System.out.println(f);
	}

}
