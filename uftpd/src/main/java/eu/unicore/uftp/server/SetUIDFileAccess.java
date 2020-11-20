package eu.unicore.uftp.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.client.FileInfo;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.server.unix.GroupInfoCache;
import eu.unicore.uftp.server.unix.UnixUser;

/**
 * This implementation of FileAccess uses native code to switch to the required user ID
 * before creating or reading a file.</br>
 * <em>Thread safety: since the user id of the process is modified, all public methods of this 
 * class MUST be synchronized</em>
 * 
 * @author schuller
 */
public class SetUIDFileAccess implements FileAccess {

	private static final Logger logger=Utils.getLogger(Utils.LOG_SECURITY, SetUIDFileAccess.class);
	
	/** load library for unix utilities */
	static {
		try{
			System.loadLibrary("uftp-unix");
		}catch(Error err){
			if(System.getProperty("uftp-unit-test")==null)throw err;
		}
	}

	private final UnixUser uftpdUser;

	public SetUIDFileAccess(){
		uftpdUser=new UnixUser(System.getProperty("user.name"));
		logger.info("Started as "+uftpdUser);
	}
	
	private void reset(){
		int err = UnixUser.changeIdentity(uftpdUser.getUid(), uftpdUser.getUid());
		if(err!=0)logger.warn("Could not re-set UID!");
		err = UnixUser.setEGid(uftpdUser.getGid());
		if(err!=0)logger.warn("Could not re-set GID!");
		if(logger.isDebugEnabled())logger.debug("Reset to "+UnixUser.whoami());
	}

	private void switchTo(UnixUser user, String group){
		long start=System.currentTimeMillis();
		if(user.getUid()==0){
			throw new IllegalArgumentException("Attempting to access file as root.");
		}
		if(logger.isDebugEnabled()) {
			logger.debug("Switching to "+user);
		}
		//set egid first, because privileges required to set egid
		int gid = user.getGid();
		if(group!=null && !"null".equals(group)){
			gid = GroupInfoCache.getGroup(group).getGid();
		}
		UnixUser.setEGid(gid);
		int err=UnixUser.initGroups(user.getLoginName(), gid);
		if(err!=0){
			logger.warn("initgroups() call returned: "+err);
		}
		UnixUser.changeIdentity(user.getUid(), uftpdUser.getUid());
		if(logger.isDebugEnabled()) {
			logger.debug("New effective user "+UnixUser.whoami()+
					" [time for switch: "+(System.currentTimeMillis()-start)+" ms]");
		}

	}

	@Override
	public synchronized InputStream readFile(String canonicalPath, String userID,
			String groupID, int bufferSize) throws Exception {
		UnixUser user=new UnixUser(userID);
		FileInputStream fis=null;
		try{
			switchTo(user, groupID);
			fis=new FileInputStream(canonicalPath);
			return new BufferedInputStream(fis, bufferSize);
		}
		finally{
			reset();
		}
	}

	@Override
	public synchronized OutputStream writeFile(String canonicalPath, boolean append,
			String userID, String groupID, int bufferSize) throws Exception {
		UnixUser user=new UnixUser(userID);
		FileOutputStream fos=null;
		try{
			switchTo(user, groupID);
			fos=new FileOutputStream(canonicalPath,append);
			return new BufferedOutputStream(fos, bufferSize);
		}
		finally{
			reset();
		}
	}

	@Override
	public void setUser(String canonicalPath, String user, String group)throws Exception {
		//nothing to do here, since files are created with correct permissions
	}


	@Override
	public synchronized RandomAccessFile getRandomAccessFile(File file, String userID,
			String groupID, String mode) throws IOException {
		UnixUser user=new UnixUser(userID);
		try{
			switchTo(user, groupID);
			return new RandomAccessFile(file, mode);
		}
		finally{
			reset();
		}
	}

	@Override
	public synchronized FileInfo[] listFiles(File dir, String userID, String groupID) {
		UnixUser user=new UnixUser(userID);
		try{
			switchTo(user, groupID);
			String[] ls = dir.list();
			if (ls == null) return null;
			FileInfo[] res = new FileInfo[ls.length];
			int i = 0;
			for(String name : ls){
				res[i] = new FileInfo(new File(dir,name));
				i++;
			}
			return res;
		}
		finally{
			reset();
		}
	}

	@Override
	public FileInfo stat(String path, String userID, String groupID) {
		UnixUser user=new UnixUser(userID);
		try{
			switchTo(user, groupID);
			return new FileInfo(new File(path));
		}
		finally{
			reset();
		}
	}
	
	@Override
	public synchronized File getFile(String path, String userID, String groupID) {
		UnixUser user=new UnixUser(userID);
		try{
			switchTo(user, groupID);
			return new File(path);
		}
		finally{
			reset();
		}
	}
	
	@Override
	public synchronized void mkdir(String path, String userID, String groupID) throws IOException {
		UnixUser user=new UnixUser(userID);
		try{
			switchTo(user, groupID);
			Files.createDirectories(Paths.get(path));
		}
		finally{
			reset();
		}
	}

	@Override
	public synchronized void rm(String path, String userID, String groupID) throws IOException {
		UnixUser user=new UnixUser(userID);
		try{
			switchTo(user, groupID);
			new File(path).delete();
		}
		finally{
			reset();
		}
	}
	
	@Override
	public String getHome(String userID) {
		UnixUser user=new UnixUser(userID);
		return user.getHome();
	}

	@Override
	public void asUser(Runnable runnable, String userID, String groupID)
			throws Exception {
		UnixUser user=new UnixUser(userID);
		try{
			switchTo(user, groupID);
			runnable.run();
		}
		finally{
			reset();
		}
	}
	
}
