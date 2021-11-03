package eu.unicore.uftp.client;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;

import eu.unicore.uftp.dpc.AuthorizationFailureException;
import eu.unicore.uftp.dpc.DPCClient;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.jparss.PSocket;

/**
 * Client base class
 *
 * @author schuller
 */
public abstract class AbstractUFTPClient implements Runnable, Closeable {

	/**
	 * Errorcodes
	 */
	public final static int SYNERR = 1;
	public final static int HOSTERR = 2;

	/**
	 * NETWORK send/receive buffer size, this must be the same on client/server
	 * if multiple streams are to be used
	 */
	public final static int BUFFSIZE = 16384;

	private final InetAddress[] servers;
	private final int port;
	private int timeout = 3000;
	private int authtimeout = 0;
	private String secret;

	protected long bandwidthLimit = -1;

	protected byte[] key;

	protected boolean compress = false;

	protected int numcons = 1;

	protected UFTPProgressListener progressListener = null;

	protected final DPCClient client = new DPCClient();

	// data socket
	protected Socket socket = null;
	protected InputStream reader = null;
	protected OutputStream writer = null;

	protected volatile boolean cancelled = false;

	/**
	 * create a new UFTP client for file transfer from/to the given server
	 *
	 * @param servers
	 *            - list of alternative IP addresses of the UFTP server
	 * @param port
	 *            - the server port
	 */
	public AbstractUFTPClient(InetAddress[] servers, int port) {
		this.servers = servers;
		this.port = port;
	}

	public void connect() throws IOException, AuthorizationFailureException {
		client.setTimeout(timeout);
		client.setAuthTimeout(authtimeout);
		client.connect(servers, port, secret);
		openDataConnection();
	}

	/**
	 * if necessary, open data connection
	 * 
	 * @throws IOException
	 */
	public void openDataConnection() throws IOException {
		if (socket == null) {
			socket = createSocket(numcons, client, key, compress);
		}
	}

	/**
	 * close the data connection
	 */
	public void closeData() {
		Utils.closeQuietly(socket);
		socket = null;
		client.closeData();
	}

	/**
	 * setup reader and writer for getting remote data, by connecting them to
	 * the appropriate socket input stream and local output stream
	 *
	 * @param localTarget
	 *            local output stream
	 * @throws IOException
	 */
	protected void prepareGet(OutputStream localTarget) throws IOException {
		if (numcons == 1) {
			if (key != null) {
				reader = Utils.getDecryptStream(socket.getInputStream(), key);
			} else {
				reader = socket.getInputStream();
			}
			if (compress) {
				reader = Utils.getDecompressStream(reader);
			}
		} else {
			reader = socket.getInputStream();
		}
		writer = localTarget;
	}

	/**
	 * setup reader and writer for writing local data to the remote target, by
	 * connecting them to the socket output stream and local input source
	 *
	 * @param localSource
	 *            local input source
	 * @throws IOException
	 */
	protected void preparePut(InputStream localSource) throws IOException {
		writer = socket.getOutputStream();
		if (numcons == 1) {
			if (key != null) {
				writer = Utils.getEncryptStream(writer, key);
			}
			if (compress) {
				writer = Utils.getCompressStream(writer);
			}
		}
		reader = localSource;
	}

	protected Socket createSocket(int numConnections, DPCClient client, byte[] key) throws IOException {
		return createSocket(numConnections, client, key, false);
	}

	protected Socket createSocket(final int numConnections, DPCClient client, byte[] key, boolean compress)
			throws IOException {
		Socket localSocket;
		List<Socket> dataCons = client.openDataConnections(numConnections);
		// server may have given us less connections than we requested
		numcons = dataCons.size();
		if (numcons > 1) {
			localSocket = new PSocket(key, compress);
			PSocket parallelSocket = (PSocket) localSocket;
			parallelSocket.init(1, numcons);
			for (Socket dataCon : dataCons) {
				parallelSocket.addSocketStream(dataCon);
			}
		} else {
			localSocket = dataCons.get(0);
		}
		return localSocket;
	}

	public void close() throws IOException {
		Utils.closeQuietly(reader);
		Utils.closeQuietly(writer);
		Utils.closeQuietly(client);
	}

	public void setNumConnections(int num) {
		if (num < 1) {
			throw new IllegalArgumentException();
		}
		this.numcons = num;
	}

	public int getNumConnections() {
		return numcons;
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public int getAuthtimeout() {
		return authtimeout;
	}

	public void setAuthtimeout(int authtimeout) {
		this.authtimeout = authtimeout;
	}

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	/**
	 * set the (encoded) symmetric key to be used for encryption/decryption
	 *
	 * @param key
	 */
	public void setKey(byte[] key) {
		this.key = key;
	}

	public byte[] getKey() {
		return key;
	}

	public boolean isCompress() {
		return compress;
	}

	public void setCompress(boolean compress) {
		this.compress = compress;
	}

	public UFTPProgressListener getProgressListener() {
		return progressListener;
	}

	public void setProgressListener(UFTPProgressListener progressListener) {
		this.progressListener = progressListener;
	}

	public InetAddress[] getServerList() {
		return servers;
	}

	public long getBandwidthLimit() {
		return bandwidthLimit;
	}

	public void setBandwidthLimit(long limit) {
		this.bandwidthLimit = limit;
	}

	public boolean isConnected() {
		return client != null && client.isConnected();
	}

	public List<String> getServerFeatures() {
		return client.getServerFeatures();
	}

	public void assertFeature(String feature) throws IOException {
		if(!getServerFeatures().contains(feature)){
			throw new RuntimeException("UFTPD server does not support the '"+feature+"' feature.");
		}
	}
	
	/**
	 * signal the client that it should cancel an operation
	 */
	public void cancel() {
		cancelled = true;
	}
}
