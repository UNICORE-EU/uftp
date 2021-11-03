package eu.unicore.uftp.dpc;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.server.DefaultFileAccess;
import eu.unicore.uftp.server.FileAccess;
import eu.unicore.uftp.server.SetUIDFileAccess;
import eu.unicore.util.Log;

public class Utils {

	// Logger categories
	public static final String LOG_SERVER = "uftp.server";
	public static final String LOG_CLIENT = "uftp.client";
	public static final String LOG_SECURITY = "uftp.security";

	//symmetric encryption algorithm
	private static final String algo = "Blowfish";
	//key type
	private static final String keyAlgo = "Blowfish";
	//mode
	private static final String mode = "ECB";
	//padding type
	private static final String padding = "PKCS5Padding";
	private static KeyGenerator keyGenerator;

	static {
		try {
			keyGenerator = KeyGenerator.getInstance(keyAlgo);
			keyGenerator.init(new SecureRandom());
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public static Logger getLogger(String prefix, Class<?>clazz){
		return Log.getLogger(prefix, clazz);
	}

	/**
	 * returns a stream that decrypts data
	 *
	 * @param source - the underlying physical stream
	 * @param key - encoded key
	 * 
	 * @throws IOException
	 */
	public static InputStream getDecryptStream(InputStream source, byte[] key) throws IOException {
		try {
			return new CipherInputStream(source, Utils.makeDecryptionCipher(key));
		} catch (Exception ex) {
			throw new IOException(ex);
		}
	}

	/**
	 * returns a stream that encrypts data
	 *
	 * @param sink - the underlying physical stream
	 * @param key - encoded key
	 * 
	 * @throws IOException
	 */
	public static OutputStream getEncryptStream(OutputStream sink, byte[] key) throws IOException {
		try {
			return new MyCipherOutputStream(sink, Utils.makeEncryptionCipher(key));
		} catch (Exception ex) {
			throw new IOException(ex);
		}
	}

	/**
	 * returns a stream that compresses data
	 *
	 * @param sink - the underlying physical stream
	 * 
	 * @throws IOException
	 */
	public static OutputStream getCompressStream(OutputStream sink) throws IOException {
		try {
			return new MyGZIPOutputStream(sink);
		} catch (Exception ex) {
			throw new IOException(ex);
		}
	}

	/**
	 * returns a stream that uncompresses data
	 *
	 * @param source - the underlying physical stream
	 * 
	 * @throws IOException
	 */
	public static InputStream getDecompressStream(InputStream source) throws IOException {
		try {
			return new GZIPInputStream(source);
		} catch (Exception ex) {
			throw new IOException(ex);
		}
	}
	public static String encodeBase64(byte[] data) {
		if (data == null) {
			return null;
		}
		return new String(Base64.encodeBase64(data));
	}

	public static byte[] decodeBase64(String base64) {
		if (base64 == null) {
			return null;
		}
		return Base64.decodeBase64(base64.getBytes());
	}

	/**
	 * create a {@link Cipher} for encryption
	 *
	 * @param encodedKey - encoded key
	 * @throws Exception
	 */
	public static Cipher makeEncryptionCipher(byte[] encodedKey) throws Exception {
		Cipher c = Cipher.getInstance(algo + "/" + mode + "/" + padding);
		SecretKeySpec keySpec = new SecretKeySpec(encodedKey, keyAlgo);
		c.init(Cipher.ENCRYPT_MODE, keySpec);
		return c;
	}

	/**
	 * create a {@link Cipher} for decryption
	 *
	 * @param encodedKey - encoded key
	 */
	public static Cipher makeDecryptionCipher(byte[] encodedKey) throws Exception {
		Cipher c = Cipher.getInstance(algo + "/" + mode + "/" + padding);
		SecretKeySpec keySpec = new SecretKeySpec(encodedKey, keyAlgo);
		c.init(Cipher.DECRYPT_MODE, keySpec);
		return c;
	}

	/**
	 * create a new symmetric key
	 *
	 * @return the encoded key
	 */
	public static byte[] createKey() throws InvalidKeySpecException, NoSuchAlgorithmException, NoSuchProviderException {
		Key key = keyGenerator.generateKey();
		return key.getEncoded();
	}

	public static String md5(File file) throws Exception {
		return hexString(digest(file));
	}

	public static String md5(byte[] data) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(data);
			return hexString(md);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	public static void writeToFile(String content, File target) throws IOException {
		FileOutputStream o = new FileOutputStream(target);
		try {
			o.write(content.getBytes());
		} finally {
			o.close();
		}
	}

	public static MessageDigest digest(File file) throws Exception {
		return digest(file, "MD5");
	}

	public static MessageDigest digest(File file, String algo) throws Exception {
		FileInputStream fis = new FileInputStream(file);
		byte[] buf = new byte[1024];
		int r = 0;
		MessageDigest md = MessageDigest.getInstance(algo);
		while (true) {
			r = fis.read(buf);
			if (r < 0) {
				break;
			}
			md.update(buf, 0, r);
		}
		fis.close();
		return md;
	}

	/**
	 * converts the message digest into a user-friendly hex string
	 *
	 * @param digest
	 */
	public static String hexString(MessageDigest digest) {
		return hexString(digest.digest());
	}

	/**
	 * converts the byte-array into a more user-friendly hex string
	 *
	 * @param bytes
	 */
	public static String hexString(byte[] bytes) {
		StringBuilder hexString = new StringBuilder();
		for (int i = 0; i < bytes.length; i++) {
			String hex = Integer.toHexString(0xFF & bytes[i]);
			if (hex.length() == 1) {
				hexString.append('0');
			}
			hexString.append(hex);
		}
		return hexString.toString();
	}

	private static ScheduledExecutorService executor;

	public static synchronized ScheduledExecutorService getExecutor() {
		if(executor == null){
			//TODO configure thread pool
			executor = new ScheduledThreadPoolExecutor(10);
			Runtime.getRuntime().addShutdownHook(new Thread(){
				public void run(){
					executor.shutdownNow();
				}
			});;
		}
		return executor;
	}
	private static Set<String> whiteList;

	public static synchronized Set<String> getWhiteList() {
		if (whiteList == null) {
			whiteList = new HashSet<String>();
		}
		return whiteList;
	}

	public static String getDetailMessage(Throwable throwable) {
		StringBuilder sb = new StringBuilder();
		Throwable cause = throwable;
		String message = null;
		String type = null;
		type = cause.getClass().getName();
		do {
			type = cause.getClass().getName();
			message = cause.getMessage();
			cause = cause.getCause();
		} while (cause != null);

		sb.append(type).append(" ");
		if (message != null) {
			sb.append(message);
		} else {
			sb.append(" (no further message available)");
		}
		return sb.toString();
	}

	public static String createFaultMessage(String message, Throwable cause) {
		return message + ": " + getDetailMessage(cause);
	}

	/**
	 * this removes leading and trailing \" characters
	 *
	 * @param s
	 * 
	 */
	public static String trim(String s) {
		int begin = s.startsWith("\"") ? 1 : 0;
		int end = s.endsWith("\"") ? s.length() - 1 : s.length();
		return s.substring(begin, end);
	}

	private static final Pattern pattern = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");

	/**
	 * parses a line into tokens separated by spaces
	 * Allows both single and double quotes for escaping parts with spaces
	 * E.g. the line <code>this is 'an example'</code> will be parsed into
	 * "this", "is" and "an example"
	 * 
	 * @param line
	 */
	public static List<String>parseLine(String line){
		List<String> result = new ArrayList<String>();
		Matcher matcher = pattern.matcher(line);
		while (matcher.find()) {
			if (matcher.group(1) != null) {
				// Add double-quoted string without the quotes
				result.add(matcher.group(1));
			} else if (matcher.group(2) != null) {
				// Add single-quoted string without the quotes
				result.add(matcher.group(2));
			} else {
				// Add unquoted word
				result.add(matcher.group());
			}
		} 
		return result;
	}


	public static FileAccess getFileAccess(Logger logger) {
		FileAccess fa = null;
		try {
			fa = new SetUIDFileAccess();
			if(logger!=null)logger.info("Will switch user IDs");
		} catch (UnsatisfiedLinkError ex) {
			if(logger!=null)logger.warn("Can't load native library for setuid switching, falling back to default mode: " + ex);
		} catch (Error e) {
			if(logger!=null)logger.warn("Error loading setuid switcher, falling back to default mode: " + e);
		}
		if (fa == null) {
			fa = new DefaultFileAccess();
			String user = System.getProperty("user.name");
			if("root".equals(user)){
				throw new Error("UFTPD accesses files with root privileges, this is not allowed!");
			}
			if(logger!=null)logger.info("NOT switching user IDs, will access all files as user <" + user + ">");
		}
		return fa;
	}

	/**
	 * properly flush the stream, which may be compressed and/or encrypted
	 * @param target
	 * @throws IOException
	 */
	public static void finishWriting(OutputStream target) throws IOException {
		if(target instanceof MyGZIPOutputStream){
			((MyGZIPOutputStream)target).finish();
		}
		else if(target instanceof MyCipherOutputStream){
			((MyCipherOutputStream)target).finish();
		}
	}

	public static void closeQuietly(Closeable x) {
		try {
			if(x!=null)x.close();
		} catch (IOException ex) {}
	}

	public static InetAddress[] parseInetAddresses(String addresses, Logger logger){
		List<InetAddress>serverList = new ArrayList<>();
		String[] servers=addresses.split("[ ,]+");
		for(String s: servers){
			try{
				InetAddress a=InetAddress.getByName(s);
				serverList.add(a);
			}
			catch(UnknownHostException uhe){
				if(logger!=null)logger.debug("No such host: "+s, uhe);
			}
		}
		return serverList.toArray(new InetAddress[serverList.size()]);
	}

	public static String encodeInetAddresses(InetAddress[] addresses){
		StringBuilder sb = new StringBuilder();
		for(InetAddress a: addresses){
			if(sb.length()>0)sb.append(",");
			sb.append(a.getHostAddress());
		}
		return sb.toString();
	}

	
	public static String getProperty(String key, String def) {
		String p = System.getProperty(key);
		if(p==null) {
			p = System.getenv().getOrDefault(key, def);
		}
		return p;
	}

}
