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

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.client.FileInfo;
import eu.unicore.uftp.dpc.Utils;

/**
 * default version of {@link FileAccess} interface. 
 * This is used if the SetUID version is not available.
 *  
 * @author schuller
 */
public class DefaultFileAccess implements FileAccess {

	private static final Logger logger = Utils.getLogger(Utils.LOG_SECURITY, DefaultFileAccess.class);
	
	@Override
	public InputStream readFile(String canonicalPath, String userID,
			String groupID, int size) throws Exception {
		return new BufferedInputStream(new FileInputStream(new File(canonicalPath)),size);
	}

	@Override
	public void setUser(String canonicalPath, String user, String group)
			throws IOException {
		String chown=(group!=null && !"null".equals(group)) ? 
				"chown "+user+":"+group + " "+canonicalPath :
					"chown "+user+" "+canonicalPath;
		Process p2=Runtime.getRuntime().exec(chown);
		try{
			int exit=p2.waitFor();
			logger.info("Executed "+chown+" exit code="+exit);
		}catch(InterruptedException e) {
			throw new IOException(e);
		}
	}

	@Override
	public OutputStream writeFile(String canonicalPath, boolean append, 
			String userID, String groupID, int size) throws Exception {
		return new BufferedOutputStream(new FileOutputStream(new File(canonicalPath), append),size);
	}

	@Override
	public File getFile(String path, String userID, String groupID) {
		return new File(path);
	}
	
	@Override
	public RandomAccessFile getRandomAccessFile(File file, String userID,
			String groupID, String mode) throws IOException {
		return new RandomAccessFile(file, mode);
	}

	@Override
	public void mkdir(String canonicalPath, String user, String group)
			throws IOException {
		new File(canonicalPath).mkdir();
		setUser(canonicalPath, user, group);
	}

	@Override
	public void rm(String canonicalPath, String user, String group)
			throws Exception {
		new File(canonicalPath).delete();
	}

	@Override
	public FileInfo[] listFiles(File dir, String userID, String groupID) {
		String[] ls = dir.list();
		FileInfo[] res = new FileInfo[ls.length];
		int i = 0;
		for(String name : ls){
			res[i] = new FileInfo(new File(dir,name));
			i++;
		}
		return res;
	}
	
	@Override
	public FileInfo stat(String path, String userID, String groupID) {
		return new FileInfo(new File(path));
	}
	
	@Override
	public String getHome(String userID) {
		return System.getProperty("user.home");
	}

	@Override
	public void asUser(Runnable runnable, String user, String group)
			throws Exception {
		runnable.run();
	}
	
}
