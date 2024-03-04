package eu.unicore.uftp.server.workers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.dpc.Utils.EncryptionAlgorithm;
import eu.unicore.util.Log;

/**
 * Remote copy thread (called from Session on SEND-FILE and RECEIVE-FILE)
 * 
 * @author schuller
 */
public class RCPThread extends Thread {

	int numberOfStreams = 1;
	boolean compress = false;
	InetAddress[] host;
	int port;
	private final String secret;
	private byte[] key;
	private EncryptionAlgorithm encryptionAlgorithm;

	boolean isSend;
	private String remotePath;
	private File localFile;
	long offset = 0;
	long size = -1;
	private String statusMessage = "OK";

	public RCPThread(File localSource, String remoteTarget, String serverSpec, String password) throws IOException {
		setHost(serverSpec);
		this.localFile = localSource;
		this.remotePath = remoteTarget;
		this.isSend = true;
		this.secret = password;
	}
	
	public RCPThread(String remoteSource, File localTarget, String serverSpec, String password) throws IOException {
		setHost(serverSpec);
		this.localFile = localTarget;
		this.remotePath = remoteSource;
		this.isSend = false;
		this.secret = password;
	}
	
	private void setHost(String serverSpec) throws UnknownHostException {
		String[] t = serverSpec.split(":");
		host = new InetAddress[] { InetAddress.getByName(t[0]) };
		port = Integer.valueOf(t[1]);
	}
	
	public void setNumberOfStreams(int n) {
		this.numberOfStreams = n;
	}

	public void setEncryption(byte[] key, EncryptionAlgorithm algo) {
		this.key = key;
		this.encryptionAlgorithm = algo;
	}

	public void setCompress(boolean compress) {
		this.compress = compress;
	}

	public void setRange(long offset, long size) {
		this.offset = offset;
		this.size = size;
	}

	public String getStatusMessage() {
		return statusMessage;
	}

	@Override
	public void run() {
		try(UFTPSessionClient c = new UFTPSessionClient(host, port)){
			c.setSecret(secret);
			c.setNumConnections(numberOfStreams);
			c.setKey(key);
			c.setEncryptionAlgorithm(encryptionAlgorithm);
			c.setCompress(compress);
			c.connect();
			statusMessage = "RUNNING";
			if(isSend) {
				try(FileInputStream fis = new FileInputStream(localFile)){
					c.put(remotePath, size, offset, fis);
				}
			}
			else {
				try(FileOutputStream fos = new FileOutputStream(localFile)){
					c.get(remotePath, size, offset, fos);
				}
			}
		}catch(IOException e) {
			statusMessage = Log.createFaultMessage("ERROR", e);
		}
	}

}
