package eu.unicore.uftp.client;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Holds information about a file such as name, size, last modified...
 *  
 * @author schuller
 */
public class FileInfo {

	private boolean exists;

	private boolean isDirectory;
	
	private String path;
	
	private long lastModified;
	
	private long size;

	private boolean readable = true;
	private boolean writable = true;
	private boolean executable = true;
	
	public FileInfo(){
	}
	
	public FileInfo(String lsEntry){
		parseLSEntry(lsEntry);
	}
	
	/**
	 * Generates FileInfo for the given file. The physical file is accessed to 
	 * retrieve data like length or last modified. 
	 * @param file - the file to get info for
	 */
	public FileInfo(File file){
		exists = file.exists();
		isDirectory = file.isDirectory();
		size = file.length();
		lastModified = file.lastModified();
		path = file.getName();
		readable = file.canRead();
		writable = file.canWrite();
		executable = file.canExecute();
	}

	protected void parseLSEntry(String ls){
		String[]tok=ls.trim().split(" ", 4);
		parsePermissions(tok[0].toLowerCase());
		size=Long.valueOf(tok[1]);
		lastModified=Long.valueOf(tok[2]);
		path=tok[3];
	}
	
	protected void parsePermissions(String perms){
		isDirectory='d'==perms.charAt(0);
		if(perms.length()>1){
			try{
				readable = 'r'==perms.charAt(1);
				writable = 'w'==perms.charAt(2);
				executable = 'x'==perms.charAt(3);
			}catch(Exception e){}
		}
	}
	
	public boolean exists() {
		return exists;
	}
	
	public boolean isDirectory() {
		return isDirectory;
	}

	public void setDirectory(boolean isDirectory) {
		this.isDirectory = isDirectory;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public long getLastModified() {
		return lastModified;
	}

	public void setLastModified(long lastModified) {
		this.lastModified = lastModified;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}
	
	public boolean isReadable() {
		return readable;
	}

	public void setReadable(boolean readable) {
		this.readable = readable;
	}

	public boolean isWritable() {
		return writable;
	}

	public void setWritable(boolean writable) {
		this.writable = writable;
	}

	public boolean isExecutable() {
		return executable;
	}

	public void setExecutable(boolean executable) {
		this.executable = executable;
	}

	public String getIsDirectory(){
		StringBuilder perm = new StringBuilder();
		perm.append(isDirectory?"d":"-");
		return perm.toString();
	}
	
	public String getUnixPermissions(String empty){
		StringBuilder perm = new StringBuilder();
		perm.append(readable?"r":empty);
		perm.append(writable?"w":empty);
		perm.append(executable?"x":empty);
		return perm.toString();
	}

	public String toString(DateFormat format){
		StringBuilder info = new StringBuilder();
		info.append(getIsDirectory());
		info.append(getUnixPermissions("-"));
		info.append(" ").append(size);
		info.append(" ");
		if(format==null) {
			info.append(lastModified);
		}
		else {
			info.append(format.format(new Date(lastModified)));	
		}
		info.append(" ").append(path);
		return info.toString();
	}

	final static SimpleDateFormat defaultFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

	public String toString(){
		return toString(null);			
	}
	
	public String toPrettyString(){
		synchronized (defaultFormat) {
			return toString(defaultFormat);			
		}
	}

	public String toMListEntry(){
		StringBuilder info = new StringBuilder();
		info.append("size=").append(size);
		info.append(";modify=").append(toTimeVal(lastModified));
		info.append(";type=").append(isDirectory?"dir":"file");
		info.append(";perm=").append(getUnixPermissions(""));
		info.append(" ").append(path);
		return info.toString();
	}
	
	
	
	public static String toTimeVal(long time){
		return new SimpleDateFormat("yyyyMMddhhmmss").format(new Date(time));
	}
	
	public static long toTime(String timeVal) throws ParseException {
		return new SimpleDateFormat("yyyyMMddhhmmss").parse(timeVal).getTime();
	}
	
}
