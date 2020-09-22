package eu.unicore.uftp.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

import eu.unicore.uftp.client.FileInfo;

/**
 * wraps file access (read/write operations) and other operations which need to 
 * be performed as a different user using setuid
 *
 * @author schuller
 */
public interface FileAccess {

	/**
	 * default file read/write buffersize in bytes
	 */
	public static final int DEFAULT_BUFFERSIZE=128*1024;

	/**
	 * create a {@link InputStream} for reading
	 * 
	 * @param canonicalPath - the canonical path to the file
	 * @param userID - the user id
	 * @param groupID - the group id
	 * @param bufferSize - the buffer size for reading from
	 * @return a {@link InputStream}
	 * @throws Exception  
	 */
	public InputStream readFile(String canonicalPath, String userID, String groupID, int bufferSize) throws Exception;

	/**
	 * create a {@link RandomAccessFile} for accessing the given path
	 * 
	 * @param file - the path
	 * @param userID - the user id
	 * @param groupID - the group id
	 * @param mode - the mode (see {@link RandomAccessFile#RandomAccessFile(java.io.File, String)}
	 * @return RandomAccessFile
	 * @throws IOException 
	 * @throws Exception
	 */
	public RandomAccessFile getRandomAccessFile(File file, String userID, String groupID, String mode) throws IOException;

	/**
	 * lists files in the given directory
	 * 
	 * @param directory - the directory to list
	 * @param userID - the user id
	 * @param groupID - the group id
	 * @return list of {@link FileInfo} objects
	 */
	public FileInfo[] listFiles(File directory,String userID, String groupID);

	/**
	 * create a file as the given user/group
	 * 
	 * @param path - the file
	 * @param userID - the user id
	 * @param groupID - the group id
	 */
	public File getFile(String path, String userID, String groupID) ;
	
	/**
	 * get info on a single file
	 * 
	 * @param path - the file
	 * @param userID - the user id
	 * @param groupID - the group id
	 */
	public FileInfo stat(String path, String userID, String groupID);
	
	/**
	 * create a {@link OutputStream} for writing
	 * 
	 * @param canonicalPath - the canonical path to the file
	 * @param userID - the user id
	 * @param groupID - the group id
	 * @param bufferSize - the buffer size for writing to disk
	 * @return {@link OutputStream}
	 */
	public OutputStream writeFile(String canonicalPath, boolean append, String userID, String groupID, int bufferSize) throws Exception;

	/**
	 * set user/group on the given file
	 * 
	 * @param canonicalPath
	 * @param user
	 * @param group
	 */
	public void setUser(String canonicalPath, String user, String group) throws Exception;

	/**
	 * create a directory. all parent directories must exist
	 * 
	 * @param canonicalPath - the directory to create
	 * @param user - user ID
	 * @param group - group IP
	 * @throws Exception
	 */
	public void mkdir(String canonicalPath, String user, String group) throws IOException;

	/**
	 * delete a file or directory
	 * 
	 * @param canonicalPath - the file/directory to delete
	 * @param user - user ID
	 * @param group - group IP
	 * @throws Exception
	 */
	public void rm(String canonicalPath, String user, String group) throws Exception;

	/**
	 * gets the user's home directory
	 * 
	 * @param user
	 * @return user's home directory
	 * @throws Exception
	 */
	public String getHome(String user);
	
	/**
	 * execute the given Runnable as given user
	 *  
	 * @param runnable - a very SHORT task
	 * @param user - user ID
	 * @param group - group ID
	 * @throws Exception
	 */
	public void asUser(Runnable runnable, String user, String group) throws Exception;
	
}
