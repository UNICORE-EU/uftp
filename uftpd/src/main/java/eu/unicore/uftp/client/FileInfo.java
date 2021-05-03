package eu.unicore.uftp.client;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

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
	private boolean executable = false;

	// TODO obviously
	private String owner="schuller", group="schuller";

	public FileInfo(){
	}

	public FileInfo(String ls){
		parseLSEntry(ls);
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
		path = !file.getName().isEmpty()? file.getName() : file.getPath();
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

	public static FileInfo fromMListEntry(String mlist){
		FileInfo info = new FileInfo();
		String [] tok1 = mlist.split(" ",2);
		info.path = tok1[1];

		String[] tok2 = tok1[0].split(";");
		Map<String,String> kv = new HashMap<>();
		for(String t: tok2) {
			try {
				kv.put(t.substring(0, t.indexOf("=")), 
						t.substring(t.indexOf("=")+1));
			}catch(Exception e) {}
		}
		try {
			info.size = Long.parseLong(kv.get("size"));
			info.isDirectory = "dir".equals(kv.get("type"));
			info.lastModified = toTime(kv.get("modify"));
			String perm = kv.get("perm");
			info.writable = perm.contains("w");
			info.executable = perm.contains("x");
			info.readable = perm.contains("r");
		}catch(Exception e) {e.printStackTrace();}
		return info;
	}

	public static String toTimeVal(long time){
		return new SimpleDateFormat("yyyyMMddHHmmss").format(new Date(time));
	}

	public static long toTime(String timeVal) throws ParseException {
		return new SimpleDateFormat("yyyyMMddHHmmss").parse(timeVal).getTime();
	}

	/*
	 * Licensed to the Apache Software Foundation (ASF) under one
	 * or more contributor license agreements.  See the NOTICE file
	 * distributed with this work for additional information
	 * regarding copyright ownership.  The ASF licenses this file
	 * to you under the Apache License, Version 2.0 (the
	 * "License"); you may not use this file except in compliance
	 * with the License.  You may obtain a copy of the License at
	 *
	 *  http://www.apache.org/licenses/LICENSE-2.0
	 *
	 * Unless required by applicable law or agreed to in writing,
	 * software distributed under the License is distributed on an
	 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
	 * KIND, either express or implied.  See the License for the
	 * specific language governing permissions and limitations
	 * under the License.
	 */

	private static final TimeZone TIME_ZONE_UTC = TimeZone.getTimeZone("UTC");

	private final static String[] MONTHS = { "Jan", "Feb", "Mar", "Apr", "May",
			"Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };

	/*
	 * Creates the DateFormat object used to parse/format
	 * dates in FTP format.
	 */
	private static final ThreadLocal<DateFormat> FTP_DATE_FORMAT = new ThreadLocal<DateFormat>() {

		@Override
		protected DateFormat initialValue() {
			DateFormat df=new SimpleDateFormat("yyyyMMddHHmmss");
			df.setLenient(false);
			df.setTimeZone(TimeZone.getTimeZone("GMT"));
			return df;
		}

	};

	/**
	 * Get unix style date string.
	 */
	public final static String getUnixDate(long millis) {
		if (millis < 0) {
			return "------------";
		}

		StringBuilder sb = new StringBuilder(16);
		Calendar cal = new GregorianCalendar();
		cal.setTimeInMillis(millis);

		// month
		sb.append(MONTHS[cal.get(Calendar.MONTH)]);
		sb.append(' ');

		// day
		int day = cal.get(Calendar.DATE);
		if (day < 10) {
			sb.append(' ');
		}
		sb.append(day);
		sb.append(' ');

		long sixMonth = 15811200000L; // 183L * 24L * 60L * 60L * 1000L;
		long nowTime = System.currentTimeMillis();
		if (Math.abs(nowTime - millis) > sixMonth) {

			// year
			int year = cal.get(Calendar.YEAR);
			sb.append(' ');
			sb.append(year);
		} else {

			// hour
			int hh = cal.get(Calendar.HOUR_OF_DAY);
			if (hh < 10) {
				sb.append('0');
			}
			sb.append(hh);
			sb.append(':');

			// minute
			int mm = cal.get(Calendar.MINUTE);
			if (mm < 10) {
				sb.append('0');
			}
			sb.append(mm);
		}
		return sb.toString();
	}

	/**
	 * Get ISO 8601 timestamp.
	 */
	public final static String getISO8601Date(long millis) {
		StringBuilder sb = new StringBuilder(19);
		Calendar cal = new GregorianCalendar();
		cal.setTimeInMillis(millis);

		// year
		sb.append(cal.get(Calendar.YEAR));

		// month
		sb.append('-');
		int month = cal.get(Calendar.MONTH) + 1;
		if (month < 10) {
			sb.append('0');
		}
		sb.append(month);

		// date
		sb.append('-');
		int date = cal.get(Calendar.DATE);
		if (date < 10) {
			sb.append('0');
		}
		sb.append(date);

		// hour
		sb.append('T');
		int hour = cal.get(Calendar.HOUR_OF_DAY);
		if (hour < 10) {
			sb.append('0');
		}
		sb.append(hour);

		// minute
		sb.append(':');
		int min = cal.get(Calendar.MINUTE);
		if (min < 10) {
			sb.append('0');
		}
		sb.append(min);

		// second
		sb.append(':');
		int sec = cal.get(Calendar.SECOND);
		if (sec < 10) {
			sb.append('0');
		}
		sb.append(sec);

		return sb.toString();
	}

	/**
	 * Get FTP date.
	 */
	public final static String getFTPDate(long millis) {
		StringBuilder sb = new StringBuilder(20);

		// MLST should use UTC
		Calendar cal = new GregorianCalendar(TIME_ZONE_UTC);
		cal.setTimeInMillis(millis);


		// year
		sb.append(cal.get(Calendar.YEAR));

		// month
		int month = cal.get(Calendar.MONTH) + 1;
		if (month < 10) {
			sb.append('0');
		}
		sb.append(month);

		// date
		int date = cal.get(Calendar.DATE);
		if (date < 10) {
			sb.append('0');
		}
		sb.append(date);

		// hour
		int hour = cal.get(Calendar.HOUR_OF_DAY);
		if (hour < 10) {
			sb.append('0');
		}
		sb.append(hour);

		// minute
		int min = cal.get(Calendar.MINUTE);
		if (min < 10) {
			sb.append('0');
		}
		sb.append(min);

		// second
		int sec = cal.get(Calendar.SECOND);
		if (sec < 10) {
			sb.append('0');
		}
		sb.append(sec);

		// millisecond
		sb.append('.');
		int milli = cal.get(Calendar.MILLISECOND);
		if (milli < 100) {
			sb.append('0');
		}
		if (milli < 10) {
			sb.append('0');
		}
		sb.append(milli);
		return sb.toString();
	}

	/*
	 *  Parses a date in the format used by the FTP commands 
	 *  involving dates(MFMT, MDTM)
	 */
	public final static Date parseFTPDate(String dateStr) throws ParseException{
		return FTP_DATE_FORMAT.get().parse(dateStr);

	}

	public String getLISTFormat() {
		StringBuilder sb = new StringBuilder();
		sb.append(getPermission());
		sb.append("   ");
		sb.append(String.valueOf(isDirectory?3:1));
		sb.append(' ');
		sb.append(owner);
		sb.append(' ');
		sb.append(group);
		sb.append(' ');
		sb.append(size);
		sb.append(' ');
		sb.append(getUnixDate(lastModified));
		sb.append(' ');
		sb.append(path);
		sb.append("\r\n");

		return sb.toString();
	}
	
    /**
     * Get permission string.
     */
    private char[] getPermission() {
        char permission[] = new char[10];
        Arrays.fill(permission, '-');

        permission[0] = isDirectory ? 'd' : '-';
        permission[1] = readable?  'r' : '-';
        permission[2] = writable ? 'w' : '-';
        permission[3] = executable ? 'x' : '-'; 
        return permission;
    }

}
