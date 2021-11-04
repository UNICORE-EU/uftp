package eu.unicore.uftp.dpc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

import eu.unicore.uftp.client.FileInfo;
import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.dpc.DPCServer.Connection;
import eu.unicore.uftp.server.FileAccess;
import eu.unicore.uftp.server.UFTPCommands;
import eu.unicore.uftp.server.UserFileAccess;
import eu.unicore.uftp.server.requests.UFTPTransferRequest;
import eu.unicore.util.Log;

/**
 * A session is used to transfer multiple files and perform common
 * operations like mkdir or remove
 *
 * @author schuller
 */
public class Session {

	private long offset = 0;

	private long numberOfBytes = Long.MAX_VALUE;

	private boolean haveRange = false;

	volatile boolean isAlive = true;

	private final Connection connection;
	private final int maxParCons; // maximum allowed parallel data streams
	private int numParCons = 1; // expected number of data streams
	// keep data connections alive during a session
	private boolean keepAlive = false;

	private final UserFileAccess fileAccess;

	private RandomAccessFile localRandomAccessFile;

	private File localFile;

	public static final int ACTION_END = 99;

	public static final int ACTION_NONE = 0;

	public static final int ACTION_RETRIEVE = 1;

	public static final int ACTION_STORE = 2;

	public static final int ACTION_SYNC_MASTER = 3;

	public static final int ACTION_SYNC_SLAVE = 4;

	public static final int ACTION_OPEN_SOCKET = 5;

	public static final int ACTION_SEND_STREAM_DATA = 6;

	public static final int ACTION_CLOSE_DATA = 7;

	public static final int ACTION_SEND_HASH = 8;

	private final File baseDirectory;

	private File currentDirectory;

	private final boolean allowAbsolutePaths;

	// allowed file patterns
	private String[]includes = {};

	// forbidden file patterns
	private String[]excludes = {};
	private String[] defaultExcludes = {};

	// original file name for rename operation
	private String renameFrom = null;

	// hash algorithm
	private String hashAlgorithm = "MD5";
	private String[] supportedHashAlgorithms = new String[] { "MD5", "SHA-1", "SHA-256", "SHA-512" };
	private MessageDigest md;

	private final Map<String, Pattern>patterns=new HashMap<String, Pattern>();

	private boolean archiveMode = false;

	// false = UFTP "legacy bug" in how to interpret RANG
	// true  = be compliant with the IETF draft
	// http://tools.ietf.org/html/draft-bryan-ftp-range-05
	private boolean rfcRangeMode = false;

	public static enum Mode {
		// ordered by "access level"
		FULL, WRITE, READ, INFO, NONE
	}

	private Mode accessLevel = Mode.FULL;
	
	public Session(Connection connection, UFTPTransferRequest job, FileAccess fileAccess, int maxParCons) {
		this.connection = connection;
		this.fileAccess = new UserFileAccess(fileAccess,job.getUser(),job.getGroup());
		this.includes = parsePathlist(job.getIncludes());
		this.excludes = parsePathlist(job.getExcludes());
		this.defaultExcludes = parsePathlist(Utils.getProperty(UFTPConstants.ENV_UFTP_NO_WRITE, null));
		File requested = job.getFile().getParentFile();
		if (requested!= null) {
			allowAbsolutePaths = false;
			if(requested.isAbsolute()) {
				baseDirectory = job.getFile().getParentFile();
			}else {
				baseDirectory = new File(new File(fileAccess.getHome(job.getUser())), requested.getPath());
			}
		}
		else {
			baseDirectory = new File(fileAccess.getHome(job.getUser()));
			allowAbsolutePaths = true;
		}
		currentDirectory = baseDirectory;
		this.maxParCons = maxParCons;
		this.accessLevel = job.getAccessPermissions();
	}

	public int getNextAction() throws IOException, ProtocolViolationException {
		String cmd = connection.readControl();
		String chk = cmd!=null? cmd.toUpperCase() : null;
		
		if (chk == null || chk.startsWith("BYE") || chk.startsWith("QUIT")) {
			isAlive = false;
			return ACTION_END;
		}
		
		if(chk.isEmpty())return ACTION_NONE;
		
		else if (chk.startsWith("NOOP ")) {
			boolean wantDataConnections = handleNoop(cmd);
			if(wantDataConnections) {
				// make sure to close existing ones!
				return ACTION_CLOSE_DATA;
			}
		}

		else if (chk.startsWith("RANG ")) {
			handleRange(cmd);
		}

		else if (chk.startsWith("REST ")) {
			handleRestart(cmd);
		}

		else if (chk.startsWith("RETR ")) {
			boolean ok = handleRetrieve(cmd);
			return ok ? ACTION_RETRIEVE : ACTION_NONE;
		}

		else if (chk.startsWith("HASH ")) {
			boolean ok = handleHash(cmd);
			return ok ? ACTION_SEND_HASH : ACTION_NONE;
		}

		else if (chk.startsWith("STAT ")) {
			handleStat(cmd);
		}
		
		else if (chk.startsWith("MLST")) {
			handleMStat(cmd);
		}
		
		else if (chk.startsWith("CDUP")) {
			handleCDUP();
		}

		else if (chk.startsWith("CWD ")) {
			handleCWD(cmd);
		}

		else if (chk.startsWith("MKD ")) {
			handleMKD(cmd);
		}

		else if (chk.startsWith("DELE ") || chk.startsWith("RMD ")) {
			handleRM(cmd);
		}

		else if (chk.startsWith("SIZE ")) {
			handleSize(cmd);
		}

		else if (chk.startsWith("PWD")) {
			handlePWD();
		}

		else if (chk.startsWith("ALLO ")) {
			handleAllocate(cmd);
		}

		else if (chk.startsWith("STOR ")) {
			boolean ok = handleStore(cmd);
			return ok ? ACTION_STORE : ACTION_NONE;
		}

		else if (chk.startsWith("APPE ")) {
			boolean ok = handleAppend(cmd);
			return ok ? ACTION_STORE : ACTION_NONE;
		}
		
		else if (chk.startsWith("SYNC-MASTER ")) {
			boolean ok = setupSyncFile(cmd);
			return ok ? ACTION_SYNC_MASTER : ACTION_NONE;
		}

		else if (chk.startsWith("SYNC-SLAVE ")) {
			boolean ok = setupSyncFile(cmd);
			return ok ? ACTION_SYNC_SLAVE : ACTION_NONE;
		}

		else if (chk.startsWith("RNFR ")) {
			handleRenameFrom(cmd);
		}

		else if (chk.startsWith("RNTO ")) {
			handleRenameTo(cmd);
		}

		else if (chk.startsWith("MFMT ")) {
			handleSetModificationTime(cmd);
		}

		else if (chk.startsWith("SYST")) {
			handleSystem(cmd);
		}

		else if (chk.startsWith("FEAT")) {
			handleFeatures(cmd);
		}

		else if (chk.startsWith("OPTS ")) {
			handleOptions(cmd);
		}

		else if (chk.startsWith("PASV") || chk.startsWith("EPSV")) {
			if(numParCons<=maxParCons){
				connection.addNewDataConnection(cmd);
			}
			else{
				connection.sendError("Already too many open data connections.");
			}
			if(connection.getDataSockets().size()==numParCons){
				return ACTION_OPEN_SOCKET;
			}
		}

		else if (chk.startsWith("TYPE ")) {
			handleType(cmd);
		}

		else if (chk.startsWith("LIST")) {
			boolean ok = handleList(cmd);
			return ok? ACTION_SEND_STREAM_DATA : ACTION_CLOSE_DATA;
		}
		
		else if (chk.startsWith("MLSD")) {
			boolean ok = handleMList(cmd);
			return ok? ACTION_SEND_STREAM_DATA : ACTION_CLOSE_DATA;
		}
		
		else if (chk.startsWith("KEEP-ALIVE")) {
			handleKeepAlive(cmd);
		}
		
		else if (chk.startsWith("ABOR")) {
			return ACTION_CLOSE_DATA;
		}
		
		else {
			connection.sendError("Command not implemented / not understood.");
		}
		
		return ACTION_NONE;
	}

	private File getFile(String pathname) throws IOException {
		String path = (pathname.startsWith("/") && allowAbsolutePaths)?
			          FilenameUtils.normalize(pathname) :
			          FilenameUtils.normalize(new File(currentDirectory, pathname).getPath());
		return fileAccess.getFile(path);
	}

	private boolean handleRetrieve(String cmd) throws IOException {
		assertMode(Mode.READ);
		String[] tok = cmd.trim().split(" ", 2);
		String localFileName = tok[1];
		localFile = getFile(localFileName);

		try{
			assertACL(localFile, Mode.READ);
			localRandomAccessFile = createRandomAccessFile(localFile, "r");
		}catch(Exception e){
			connection.sendError( "Can't open file for reading: "+e.getMessage());
			return false;
		}
		long size ;

		if(haveRange) {
			size = numberOfBytes;
		}
		else {
			size = stat(localFile).getSize();
			numberOfBytes = size - offset;
		}
		connection.sendControl( UFTPCommands.RETR_OK+" " + numberOfBytes + " bytes available for reading.");
		return true;
	}
	
	private boolean handleHash(String cmd) throws IOException {
		assertMode(Mode.READ);
		String[] tok = cmd.trim().split(" ", 2);
		String localFileName = tok[1];
		localFile = getFile(localFileName);

		try{
			assertACL(localFile, Mode.READ);
			localRandomAccessFile = createRandomAccessFile(localFile, "r");
		}catch(Exception e){
			connection.sendError( "Can't open file for reading: "+e.getMessage());
			return false;
		}
		try {
			md = MessageDigest.getInstance(hashAlgorithm);
		}catch(Exception e){
			connection.sendError( "Can't create message digest for algorithm '"
					+hashAlgorithm+"': "+e.getMessage());
			return false;
		}
		long size ;

		if(haveRange) {
			size = numberOfBytes;
		}
		else {
			size = stat(localFile).getSize();
			numberOfBytes = size - offset;
		}
		return true;
	}

	private void handleSize(String cmd) throws IOException {
		assertMode(Mode.INFO);
		String[] tok = cmd.trim().split(" ", 2);
		String localFileName = tok[1];
		File file = new File(localFileName);
		if(!file.isAbsolute()){
			file = new File(currentDirectory,localFileName);
		}
		if(!checkACL(file, Mode.INFO)){
			connection.sendError("Access denied!");	
		}
		else {
			FileInfo fi = stat(file);
			if(!fi.exists()) {
				connection.sendError("File does not exist");
			}
			else{
				connection.sendControl(UFTPCommands.SIZE_REPLY + " " + fi.getSize());
			}
		}
	}

	/*
	 * http://tools.ietf.org/html/draft-bryan-ftp-range-05
	 */
	private void handleRange(String cmd) throws IOException {
		assertMode(Mode.READ);
		String[] tok = cmd.trim().split(" ");
		String response;
		long localOffset, lastByte;

		try {
			localOffset = Long.parseLong(tok[1]);
			lastByte = Long.parseLong(tok[2]);
		} catch (Exception ex) {
			connection.sendError("RANG argument syntax error.");
			return;
		}

		if (localOffset == 1L && lastByte == 0L) {
			response = "350 Resetting range";
			resetRange();
		} else {
			response = "350 Restarting at " + localOffset + ". End byte range at " + lastByte;
			long numberOfBytes = rfcRangeMode ? lastByte - localOffset + 1: lastByte - localOffset;
			setRange(localOffset, numberOfBytes);
		}
		connection.sendControl(response);
	}

	private void handleRestart(String cmd) throws IOException {
		assertMode(Mode.READ);
		String[] tok = cmd.trim().split(" ");
		long localOffset;
		try {
			localOffset = Long.parseLong(tok[1]);
		} catch (Exception ex) {
			connection.sendError("REST argument syntax error.");
			return;
		}
		this.offset = localOffset;
		haveRange = false;
		connection.sendControl("350 Restarting at " + localOffset + ".");
	}

	private void handleStat(String cmd) throws IOException {
		assertMode(Mode.INFO);
		String[] tok = cmd.split(" ", 3);

		String options=tok[1];
		// only file info - do not list a directory
		boolean asFile=!"N".equalsIgnoreCase(options);

		String path = tok.length>2 ? tok[2]:".";
		
		File file = getFile(path);
		if(!checkACL(file, Mode.INFO)){
			connection.sendError("Access denied!");
		}
		else{
			FileInfo info = stat(file); 
			if(!info.exists()){
				connection.sendError("Directory/file does not exist or cannot be accessed!");
			}
			else{
				FileInfo[] files;
				if(!info.isDirectory() || asFile){
					files = new FileInfo[]{info};
				}
				else {
					files = listFiles(file);
					if(files==null){
						connection.sendError("Access forbidden!");
						return;
					}
				}
				connection.sendControl("211- Sending file list");
				for (FileInfo f : files) {
					connection.sendControl(" "+f.toString());
				}
				connection.sendControl("211 End of file list.");
			}
		}
	}
	
	private void handleMStat(String cmd) throws IOException {
		assertMode(Mode.INFO);
		String[] tok = cmd.split(" ", 2);
		String path = tok.length>1 ? tok[1]:".";
		File file = getFile(path);
		if(!checkACL(file, Mode.INFO)){
			connection.sendError("Access denied!");
		}
		else{
			FileInfo info = stat(file); 
			if(!info.exists()){
				connection.sendError("Directory/file does not exist or cannot be accessed!");
			}
			else{
				connection.sendControl("250- Listing " + info.getPath());
				connection.sendControl(" " + info.toMListEntry());
				connection.sendControl("250 End");
			}
		}
	}

	private boolean handleList(String cmd) throws IOException {
		assertMode(Mode.INFO);
		String[] tok = cmd.split(" ", 2);
		String path = tok.length>1 ? tok[1]:".";
		boolean linuxMode = false;
		
		if("-a".equals(path)){
			// the authors of certain linux file browsers apparently 
			// did not read the FTP RFC and send options with "LIST"
			path=".";
			linuxMode = true;
		}
		
		File file = getFile(path);
		
		if(!checkACL(file, Mode.INFO)){
			connection.sendError("Access denied!");
			return false;
		}
		
		if(!file.exists()){
			connection.sendError("Directory does not exist or cannot be accessed!");
			return false;
		}
		
		if(!file.isDirectory()){
			connection.sendError("Not a directory!");
			return false;
		}
		
		FileInfo[] files = listFiles(file);
		if(files==null){
			connection.sendError("Access forbidden!");
			return false;
		}
		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		for (FileInfo f : files) {
			if(linuxMode) {
				bos.write(f.getLISTFormat().getBytes());
			}
			else {
				bos.write((f.toString()+"\r\n").getBytes());
			}
		}
		stream = new ByteArrayInputStream(bos.toByteArray());
		numberOfBytes = bos.size();
		connection.sendControl(UFTPCommands.RETR_OK);

		return true;
	}

	private boolean handleMList(String cmd) throws IOException {
		assertMode(Mode.INFO);
		String[] tok = cmd.split(" ", 2);

		String path = tok.length>1 ? tok[1]:".";
		File file = getFile(path);
		
		if(!checkACL(file, Mode.INFO)){
			connection.sendError("Access denied!");
			return false;
		}
		if(!file.exists()){
			connection.sendError(501,"Directory does not exist or cannot be accessed!");
			return false;
		}
		if(!file.isDirectory()){
			connection.sendError(501,"Not a directory!");
			return false;
		}
		FileInfo[] files = listFiles(file);
		if(files==null){
			connection.sendError("Access forbidden!");
			return false;
		}
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		FileInfo cwd = new FileInfo(file);
		cwd.setPath(".");
		bos.write((cwd.toMListEntry()+"\r\n").getBytes());
		for (FileInfo f : files) {
			bos.write((f.toMListEntry()+"\r\n").getBytes());
		}
		stream = new ByteArrayInputStream(bos.toByteArray());
		numberOfBytes = bos.size();
		connection.sendControl(UFTPCommands.RETR_OK);
		return true;
	}

	private void handleCDUP() throws IOException {
		assertMode(Mode.FULL);
		if (!allowAbsolutePaths && currentDirectory.getAbsolutePath().equals(baseDirectory.getAbsolutePath())){
			connection.sendError("Can't cd up, already at base directory");
		}
		else {
			currentDirectory = currentDirectory.getParentFile();
			if (currentDirectory==null) {
				currentDirectory = new File("/");
			}
			connection.sendControl(UFTPCommands.OK);
		}
	}

	private void handleCWD(String cmd) throws IOException {
		assertMode(Mode.FULL);
		String dir = cmd.split(" ", 2)[1];
		File newDir = new File(dir);
		File newWD = allowAbsolutePaths && newDir.isAbsolute() ? newDir :
					new File(baseDirectory, dir);
		if (!(newWD.exists() && newWD.isDirectory())) {
			connection.sendError("Can't cd to " + newWD.getAbsolutePath());
		} else {
			currentDirectory = newWD;
			connection.sendControl(UFTPCommands.OK);
		}
	}

	private void handlePWD() throws IOException {
		assertMode(Mode.READ);
		connection.sendControl("257 \"" + currentDirectory.getAbsolutePath()+"\"");
	}

	private void handleMKD(String cmd) throws IOException {
		assertMode(Mode.WRITE);
		String dir = cmd.split(" ", 2)[1];
		File newDir = new File(dir);
		if (!newDir.isAbsolute()) {
			newDir = new File(currentDirectory, dir);
		}
		try {
			assertACL(newDir, Mode.WRITE);
			fileAccess.mkdir(newDir.getAbsolutePath());
		} catch (Exception ex) {
			String msg = Utils.createFaultMessage("Can't create directory " + newDir.getAbsolutePath(), ex);
			connection.sendError(msg);
			return;
		}
		connection.sendControl("257 \""+newDir.getAbsolutePath()+"\" directory created.");
	}

	private void handleRM(String cmd) throws IOException {
		assertMode(Mode.FULL);
		String path = cmd.split(" ", 2)[1];
		File toRemove = getFile(path);
		try {
			assertACL(toRemove, Mode.WRITE);
			fileAccess.rm(toRemove.getAbsolutePath());
		} catch (Exception ex) {
			connection.sendError("Can't delete " + toRemove.getAbsolutePath());
			return;
		}
		connection.sendControl(UFTPCommands.OK);
	}

	private void handleAllocate(String cmd) throws IOException {
		assertMode(Mode.WRITE);
		String[] tok = cmd.trim().split(" ", 2);
		String sizeS = tok[1];
		long size = Long.parseLong(sizeS);
		numberOfBytes = size;
		connection.sendControl(UFTPCommands.OK + " Will read up to " + numberOfBytes + " bytes from data connection.");
	}

	private boolean handleStore(String cmd) throws IOException {
		assertMode(Mode.WRITE);
		String[] tok = cmd.trim().split(" ", 2);
		String localFileName = tok[1];
		localFile = getFile(localFileName);
		try {
			assertACL(localFile, Mode.WRITE);
			if(!archiveMode)localRandomAccessFile = createRandomAccessFile(localFile, "rw");
		} catch (Exception ex) {
			connection.sendError("Can't open file for writing: " + ex.getMessage());
			return false;
		}
		if(numberOfBytes<=0){
			// could have STOR without ALLO
			numberOfBytes = Long.MAX_VALUE;
		}
		connection.sendControl(UFTPCommands.RETR_OK);
		return true;
	}

	
	private boolean handleAppend(String cmd) throws IOException {
		assertMode(Mode.WRITE);
		String[] tok = cmd.trim().split(" ", 2);
		String localFileName = tok[1];
		localFile = getFile(localFileName);
		try {
			assertACL(localFile, Mode.WRITE);
			localRandomAccessFile = createRandomAccessFile(localFile, "rw");
		} catch (Exception ex) {
			connection.sendError("Can't open file for writing: " + ex.getMessage());
			return false;
		}
		if(numberOfBytes<=0){
			// could have STOR without ALLO
			numberOfBytes = Long.MAX_VALUE;
		}
		offset = localFile.exists() ? localFile.length() : 0;
		
		connection.sendControl(UFTPCommands.RETR_OK);
		return true;
	}

	private boolean setupSyncFile(String cmd) throws IOException {
		assertMode(Mode.READ);
		String[] tok = cmd.trim().split(" ", 2);
		String localFileName = tok[1];
		localFile = getFile(localFileName);
		try {
			assertACL(localFile, Mode.READ);
			localRandomAccessFile = createRandomAccessFile(localFile, "r");
		} catch (Exception ex) {
			connection.sendError("Can't open file for reading: " + ex.getMessage());
			return false;
		}
		connection.sendControl(UFTPCommands.OK);
		return true;
	}

	public void setRange(long offset, long numberOfBytes) {
		this.offset = offset;
		this.numberOfBytes = numberOfBytes;
		haveRange = true;
	}

	private void handleRenameFrom(String cmd) throws IOException {
		assertMode(Mode.FULL);
		String[] tok = cmd.trim().split(" ", 2);
		renameFrom = tok[1];
		connection.sendControl(UFTPCommands.FILE_ACTION_OK+" Please send rename-to");
	}

	private void handleRenameTo(String cmd) throws IOException {
		assertMode(Mode.FULL);
		String[] tok = cmd.trim().split(" ", 2);
		final String renameTo = tok[1];

		try{
			Runnable r = new Runnable(){
				public void run(){
					File from = new File(renameFrom);
					if (!from.isAbsolute()) {
						from = new File(currentDirectory, renameFrom);
					}
					File to = new File(renameTo);
					if (!to.isAbsolute()) {
						to = new File(currentDirectory, renameTo);
					}
					try{
						Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE);
					}catch(IOException ex){
						throw new RuntimeException(ex);
					}
				}
			};
			fileAccess.asUser(r);
			connection.sendControl(UFTPCommands.OK);
		}
		catch(Exception ex){
			String msg = Utils.createFaultMessage("Could not rename file", ex);
			connection.sendError(msg);
		}
	}

	private void handleSetModificationTime(String cmd) throws IOException {
		assertMode(Mode.WRITE);
		String[] tok = cmd.trim().split(" ", 3);
		final String time = tok[1];
		final String target = tok[2];
		try{
			final long instant = FileInfo.toTime(time);
			Runnable r = new Runnable(){
				public void run(){
					File targetFile = new File(target);
					if (!targetFile.isAbsolute()) {
						targetFile = new File(currentDirectory, target);
					}
					try{
						Files.setLastModifiedTime(targetFile.toPath(),FileTime.fromMillis(instant));
					}catch(IOException ex){
						throw new RuntimeException(ex);
					}
				}
			};
			fileAccess.asUser(r);
			connection.sendControl("213 Modify="+time+" "+target);
		}
		catch(Exception ex){
			String msg = Utils.createFaultMessage("Could not set modification time for file", ex);
			connection.sendError(msg);
		}
	}


	private void handleSystem(String cmd) throws IOException {
		assertMode(Mode.INFO);
		connection.sendControl("215 Unix Type: L8");
	}

	private void handleFeatures(String cmd) throws IOException {
		assertMode(Mode.INFO);
		String featureReply = UFTPCommands.FEATURES_REPLY_LONG;
		Collection<String>features = connection.getFeatures();
		connection.sendControl(featureReply);
		for (String s : features) {
			connection.sendControl(" " + s );
		}
		if(rfcRangeMode) {
			connection.sendControl(" "+UFTPCommands.FEATURE_RFC_RANG);
		}
		StringBuilder hashFeature = new StringBuilder();
		hashFeature.append(" HASH ");
		for(String supp: supportedHashAlgorithms)
		{
			hashFeature.append(supp);
			if(hashAlgorithm.equals(supp))hashFeature.append("*");
			hashFeature.append(";");
		}
		hashFeature.deleteCharAt(hashFeature.length()-1);
		connection.sendControl(hashFeature.toString());
		connection.sendControl(UFTPCommands.ENDCODE);
	}

	private void handleOptions(String cmd) throws IOException {
		try {
			String[] tokens = cmd.split(" ");
			if(tokens.length<2){
				throw new IllegalArgumentException("'OPTS' requires at least one argument.");
			}
			String optArg = tokens[1];
			if("HASH".equalsIgnoreCase(optArg)) {
				if(tokens.length==3) {
					String newAlgo = tokens[2].toUpperCase();
					boolean validAlgorithm = false;
					for(String supported: supportedHashAlgorithms) {
						if(supported.equalsIgnoreCase(newAlgo)) {
							hashAlgorithm = newAlgo.toUpperCase();
							validAlgorithm = true;
						}
					}
					if(!validAlgorithm) {
						throw new IllegalArgumentException("Hash algorithm '"+newAlgo+"' not supported. "
								+ "Must be one of: "+Arrays.asList(supportedHashAlgorithms));
					}
				}
				connection.sendControl("200 "+hashAlgorithm);
			}
			else {
				throw new IllegalArgumentException("Parameters to 'OPTS' command not understood");
			}
		} catch(Exception ex){
			connection.sendControl(UFTPCommands.ERROR+" "+Log.createFaultMessage("Error: ", ex));
		}
	}

	/**
	 * 
	 * @param cmd - NOOP 'n' message from client requesting to open 'n' data connections
	 * @param maxParCons - maximum number of allowed connections
	 * @return true of client wants connections, false for "real" NOOP...
	 */
	private boolean handleNoop(String cmd) throws IOException {
		try{
			numParCons = Integer.parseInt(cmd.split(" ")[1]);
			String noopResponse = "222";			//accept numParCons
			if (numParCons > maxParCons) {
				numParCons = maxParCons;
				noopResponse = "223";				//limit numParCons
			}
			noopResponse += " Opening " + numParCons + " data connections";
			connection.sendControl(noopResponse);
			return true;
		}catch(Exception ex){
			// some other noop message, ignore it
			connection.sendControl(UFTPCommands.OK);
			return false;
		}
	}


	private void handleType(String cmd) throws IOException {
		assertMode(Mode.READ);
		String type = cmd.split(" ", 2)[1];
		if(UFTPSessionClient.TYPE_ARCHIVE.equalsIgnoreCase(type)) {
			archiveMode = true;
		}
		else if(UFTPSessionClient.TYPE_NORMAL.equalsIgnoreCase(type)) {
			archiveMode = false;
		}
		connection.sendControl(UFTPCommands.OK);
	}

	private void handleKeepAlive(String cmd) throws IOException {
		String setting = cmd.split(" ", 2)[1];
		if("1".equals(setting)||Boolean.parseBoolean(setting)) {
			keepAlive = true;
		}
		else {
			keepAlive = false;
		}
		connection.sendControl(UFTPCommands.OK);
	}

	public long getOffset() {
		return offset;
	}

	public long getNumberOfBytes() {
		return numberOfBytes;
	}

	public UserFileAccess getFileAccess(){
		return fileAccess;
	}

	private void resetRange() {
		setRange(0, 0);
		haveRange = false;
	}

	public void reset() throws IOException {
		reset(true);
	}

	public void reset(boolean send226) throws IOException {
		resetRange();
		Utils.closeQuietly(localRandomAccessFile);
		if(!keepAlive)connection.closeData();
		if(send226)connection.sendControl("226 File transfer successful");
		Utils.closeQuietly(stream);
		md = null;
		stream = null;
	}

	public boolean haveRange() {
		return haveRange;
	}

	public boolean isAlive() {
		return isAlive;
	}

	public RandomAccessFile getLocalRandomAccessFile() {
		return localRandomAccessFile;
	}

	public File getLocalFile() {
		return localFile;
	}

	public void setupLocalFile(File file, boolean write) throws IOException {
		this.localFile = file;
		this.localRandomAccessFile = createRandomAccessFile(file, write?"rw":"r");
	}

	public File getBaseDirectory() {
		return baseDirectory;
	}

	/**
	 * create a random access file for the current user and group
	 *
	 * @param file
	 * @param mode - RandomAccessFile creation mode ("r" or "rw")
	 *
	 * @throws IOException
	 */
	public RandomAccessFile createRandomAccessFile(File file, String mode) throws IOException {
		return fileAccess.getRandomAccessFile(file, mode);
	}

	public String[] getIncludes() {
		return includes;
	}

	public String[] getExcludes() {
		return excludes;
	}

	public boolean isKeepAlive() {
		return keepAlive;
	}

	public boolean isArchiveMode() {
		return archiveMode;
	}
	
	public MessageDigest getMessageDigest() {
		return md;
	}
	
	public String getHashAlgorithm() {
		return hashAlgorithm;
	}

	private FileInfo[] listFiles(File directory) throws IOException {
		return fileAccess.listFiles(directory);
	}

	private FileInfo stat(File file) throws IOException {
		return fileAccess.stat(file.getPath());
	}


	/**
	 * checks whether the session's mode allows the current operation
	 */
	public void assertMode(Mode requestedAccess) throws AuthorizationFailureException {
		if(accessLevel.compareTo(requestedAccess)>0) {
			throw new AuthorizationFailureException(requestedAccess + " access denied!");
		}
	}

	/**
	 * checks whether the session's ACLs allow access to the file
	 * @param path
	 * @return <code>true</code> if the file can be accessed, <code>false</code> otherwise
	 */
	public boolean checkACL(File path, Mode requestedAccess) {
		return isAllowed(path, requestedAccess) && !isForbidden(path, requestedAccess);
	}

	/**
	 * checks whether the session's ACLs allow access to the file
	 * @param path
	 * @throws AuthorizationFailureException if the file cannot be accessed
	 */
	public void assertACL(File path, Mode requestedAccess) throws AuthorizationFailureException {
		if(!checkACL(path, requestedAccess))throw new AuthorizationFailureException("Access denied!");
	}

	public boolean isAllowed(File path, Mode requestedAccess){
		boolean res=false;
		//check if it is in the includes
		if(includes!=null && includes.length>0){
			for(String include: includes){
				res=res || match(path,include);
			}
		}
		//else everything is included
		else res=true;
		return res;
	}

	public boolean isForbidden(File path, Mode requestedAccess){
		if(excludes!=null && excludes.length>0){
			for(String exclude: excludes){
				if(match(path,exclude))return true;
			}
		}
		if(Mode.READ.compareTo(requestedAccess)>0 && defaultExcludes!=null && defaultExcludes.length>0){
			for(String exclude: defaultExcludes){
				if(match(path,exclude))return true;
			}
		}
		return false; 
	}

	boolean match(File path, String expr){
		Pattern p = getPattern(expr);
		boolean res=p.matcher(path.getAbsolutePath()).find();
		return res;
	}

	Pattern getPattern(String expr){
		Pattern p=patterns.get(expr);
		if(p==null){
			p=compilePattern(expr);
			patterns.put(expr, p);
		}
		return p;
	}

	/*
	 * translate wildcards "*" and "?" into a regular expression pattern
	 * and create the regexp Pattern
	 *
	 * TODO handle special characters?
	 */
	private Pattern compilePattern(String expr){
		StringBuilder pattern=new StringBuilder();
		pattern.append(expr.replace(".","\\.").replace("*", "[^/]*").replace("?", "."));
		return Pattern.compile(pattern.toString());
	}

	private String[] parsePathlist(String pathList){
		return pathList != null ? pathList.split(":") : null;
	}

	private InputStream stream;
	public InputStream getStream(){
		return stream;
	}
	
	public String getClientDescription() {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append(fileAccess.getUser()).append(":");
		sb.append(fileAccess.getGroup()!=null?fileAccess.getGroup():"n/a").append(":");
		sb.append(connection.getAddress()).append("]");
		return sb.toString();
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Session for ").append(getClientDescription());
		sb.append(" base_directory=\"").append(getBaseDirectory()).append("\"");
		sb.append(" allow_abspaths=").append(allowAbsolutePaths);
		sb.append(" access_level=").append(String.valueOf(accessLevel));
		if(includes!=null)sb.append(" include=").append(Arrays.asList(includes));
		if(excludes!=null)sb.append(" exclude=").append(Arrays.asList(excludes));
		if(defaultExcludes!=null)sb.append(" write_forbidden=").append(Arrays.asList(defaultExcludes));
		
		return sb.toString();
	}
	
	public void setRFCRangeMode(boolean rfcMode) {
		this.rfcRangeMode = rfcMode;
	}
}
