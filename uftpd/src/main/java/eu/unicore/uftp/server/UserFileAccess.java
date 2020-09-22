package eu.unicore.uftp.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

import eu.unicore.uftp.client.FileInfo;

/**
 * wraps file access (read/write operations) and other operations which need to 
 * be performed as a fixed user/group
 *
 * @author schuller
 */
public class UserFileAccess {

	/**
	 * default file read/write buffersize in bytes
	 */
	public static final int DEFAULT_BUFFERSIZE=128*1024;

	final FileAccess fileAccess;
	final String userID;
	final String groupID;
	
	public UserFileAccess(FileAccess fa, String userID, String groupID){
		this.fileAccess = fa;
		this.userID = userID;
		this.groupID = groupID;
	}
	
	/**
	 * create a {@link InputStream} for reading
	 * 
	 * @param canonicalPath - the canonical path to the file
	 * @param bufferSize - the buffer size for reading from
	 * @return a {@link InputStream}
	 * @throws Exception  
	 */
	public InputStream readFile(String canonicalPath, int bufferSize) throws Exception{
		return fileAccess.readFile(canonicalPath, userID, groupID, bufferSize);
	}

	/**
	 * create a {@link RandomAccessFile} for accessing the given path
	 * 
	 * @param file - the path
	 * @param mode - the mode (see {@link RandomAccessFile#RandomAccessFile(java.io.File, String)}
	 * @return RandomAccessFile
	 * @throws IOException 
	 * @throws Exception
	 */
	public RandomAccessFile getRandomAccessFile(File file, String mode) throws IOException{
		return fileAccess.getRandomAccessFile(file, userID, groupID, mode);
	}
	
	/**
	 * create a {@link File}
	 * 	 * 
	 * @param canonicalPath - the canonical path to the file
	 * @return a {@link File}
	 * @throws Exception  
	 */
	public File getFile(String canonicalPath) throws IOException{
		return fileAccess.getFile(canonicalPath, userID, groupID);
	}

	/**
	 * lists files in the given directory
	 * 
	 * @param directory
	 * @return list of {@link FileInfo} objects
	 */
	public FileInfo[] listFiles(File directory){
		return fileAccess.listFiles(directory, userID, groupID);
	}

	/**
	 * get info on a single file
	 * 
	 * @param path - the file
	 */
	public FileInfo stat(String path){
		return fileAccess.stat(path, userID, groupID);
	}
	
	/**
	 * create a {@link OutputStream} for writing
	 * 
	 * @param canonicalPath - the canonical path to the file
	 * @param bufferSize - the buffer size for writing to disk
	 * @return {@link OutputStream}
	 */
	public OutputStream writeFile(String canonicalPath, boolean append, int bufferSize) throws Exception{
		return fileAccess.writeFile(canonicalPath, append, userID, groupID, bufferSize);
	}

	/**
	 * set user/group on the given file
	 * 
	 * @param canonicalPath
	 */
	public void setUser(String canonicalPath) throws Exception{
		fileAccess.setUser(canonicalPath, userID, groupID);
	}

	/**
	 * create a directory. all parent directories must exist
	 * 
	 * @param canonicalPath - the directory to create
	 * @throws Exception
	 */
	public void mkdir(String canonicalPath) throws IOException{
		fileAccess.mkdir(canonicalPath,userID,groupID);
	}
	/**
	 * delete a file or directory
	 * 
	 * @param canonicalPath - the file/directory to delete
	 * @throws Exception
	 */
	public void rm(String canonicalPath) throws Exception{
		fileAccess.rm(canonicalPath,userID,groupID);
	}

	/**
	 * gets the user's home directory
	 * 
	 * @return user's home directory
	 * @throws Exception
	 */
	public String getHome(){
		return fileAccess.getHome(userID);
	}
	
	/**
	 * run the given task as the current user 
	 * @param runnable
	 * @throws Exception
	 */
	public void asUser(Runnable runnable) throws Exception {
		fileAccess.asUser(runnable, userID, groupID);
	}

	public String getUser() {
		return userID;
	}
	
	public String getGroup() {
		return groupID;
	}
}
