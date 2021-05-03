package eu.unicore.uftp.standalone;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import eu.unicore.uftp.client.FileInfo;
import eu.unicore.uftp.client.UFTPProgressListener;
import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.dpc.AuthorizationFailureException;
import eu.unicore.uftp.dpc.DPCClient;
import eu.unicore.uftp.rsync.RsyncStats;

/**
 *
 * @author jj
 */
public class FakeUftpClient extends UFTPSessionClient {

	private boolean connected;
	private String currentDir = "/";
	static final Map<String, String> remoteFiles = new HashMap<>();

	public FakeUftpClient(InetAddress[] servers, int port) {
		super(servers, port);
	}

	boolean fileExist(String fileName) {
		return remoteFiles.containsKey(fileName);
	}

	void removeFile(String fileName) {
		remoteFiles.remove(fileName);
	}

	String getFileContent(String fileName) {
		return remoteFiles.get(fileName);
	}

	void cleanFiles(){
		remoteFiles.clear();
	}

	@Override
	public RsyncStats syncRemoteFile(File localMaster, String remoteSlave) throws Exception {
		System.out.println("syncRemoteFile called");
		return null;
	}

	@Override
	public RsyncStats syncLocalFile(String remoteMaster, File localSlave) throws Exception {
		return null;
	}

	@Override
	public List<String> getFileList(String baseDir) throws IOException {
		List<String> ret = new ArrayList<>();
		StringBuilder buf = new StringBuilder("ls "+baseDir).append(" {");
		for (Map.Entry<String, String> entry : remoteFiles.entrySet()) {
			String string = entry.getKey();
			if (string.startsWith(baseDir)) {
				//record format: (mode + " " + size + " " + lastModified + " " + f.getName());
				//"- 256992 1390379862000 Triple store evaluation 2.0.pptx"
				String fileRecord = String.format("- %d %d %s", entry.getValue().length(), System.currentTimeMillis(), string.replaceFirst(baseDir, "")); 
				ret.add(fileRecord);
				buf.append(fileRecord).append(",");
			}
		}
		System.out.println(buf.append("}").toString());
		return ret;
	}

	@Override
	public List<FileInfo>getFileInfoList(String baseDir)throws IOException {
		List<String> files = getFileList(baseDir);
		List<FileInfo>fileInfos = new ArrayList<>();
		for(String f: files) {
			fileInfos.add(new FileInfo(f));
		}
		return fileInfos;
	}
	
	public FileInfo stat(String path) throws IOException {
		for (Map.Entry<String, String> entry : remoteFiles.entrySet()) {
			String string = entry.getKey();
			if (string.equals(path)) {
				FileInfo fi = new FileInfo();
				fi.setPath(path);
				fi.setSize(123);
				fi.setLastModified(System.currentTimeMillis());
				return fi;
			}
		}
		throw new IOException();
	}


	@Override
	public void cd(String dir) throws IOException {
		System.out.println("changing directory from " + currentDir + " to: " + dir);
		currentDir = dir;
	}

	@Override
	public String pwd() throws IOException {
		System.out.println("pwd ");
		return currentDir;
	}

	@Override
	public void put(String remoteFile, long size, Long offset, InputStream localSource) throws IOException{
		put(remoteFile, size, localSource);
	}

	@Override
	public void put(String remoteFile, long size, InputStream localSource) throws IOException {
		System.out.println("put to " + remoteFile+" "+localSource.available());
		StringWriter tempWriter = new StringWriter();
		IOUtils.copy(localSource, tempWriter, "UTF-8");        
		remoteFiles.put(remoteFile, tempWriter.toString());
	}

	@Override
	public void get(String remoteFile, long offset, long length, OutputStream localTarget) throws IOException {
		System.out.println("get from " + remoteFile + " offset, length");
		if (!fileExist(remoteFile)) throw new IOException(remoteFile+" does not exist");
		localTarget.write(remoteFiles.get(remoteFile).getBytes());
	}

	@Override
	public void get(String remoteFile, OutputStream localTarget) throws IOException {
		System.out.println("get from " + remoteFile);
		if (!fileExist(remoteFile)) {
			System.out.println(remoteFile+" does not exist");
			throw new IOException(remoteFile+" does not exist");
		}
		localTarget.write(remoteFiles.get(remoteFile).getBytes());
	}

	@Override
	public void close() throws IOException {
		System.out.println("disconnecting");
		connected = false;
	}

	@Override
	public boolean isConnected() {
		System.out.println("checking connection status");
		return connected;
	}

	@Override
	public InetAddress[] getServerList() {
		return super.getServerList();
	}

	@Override
	public void setProgressListener(UFTPProgressListener progressListener) {
		System.out.println("setting progress listner...");
		super.setProgressListener(progressListener);
	}

	@Override
	public UFTPProgressListener getProgressListener() {
		return super.getProgressListener();
	}

	@Override
	protected Socket createSocket(int numConnections, DPCClient client, byte[] key) throws IOException {
		System.out.println("Trying to create socket... bad!");
		return super.createSocket(numConnections, client, key);
	}

	@Override
	protected void preparePut(InputStream localSource) throws IOException {
		System.out.println("preparing put");
	}

	@Override
	protected void prepareGet(OutputStream localTarget) throws IOException {
		System.out.println("preparing get");
	}

	@Override
	public void connect() throws IOException, AuthorizationFailureException {
		System.out.println("Connecting");
		this.connected = true;
	}

}
