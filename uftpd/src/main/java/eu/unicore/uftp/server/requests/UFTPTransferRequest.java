package eu.unicore.uftp.server.requests;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Properties;

import eu.unicore.uftp.dpc.Session;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.server.workers.UFTPWorker;

/**
 * describes a data transfer between server and client
 *
 * @author Tim Pohlmann, 2010, Forschungszentrum Juelich - JSC
 * @author schuller
 */
public class UFTPTransferRequest extends UFTPBaseRequest {

    public static final String REQUEST_TYPE = "uftp-transfer-request";
    
    /**
     * true = server sends a file to the client false = server receives a file
     * from the client
     */
    private final boolean send;

    /**
     * local file to send or write to
     */
    private final File file;

    /**
     * number of parallel data streams
     */
    private int streams = 1;

    /**
     * the group for which to access the file
     */
    private String group;

    /**
     * whether to compress the data
     */
    private boolean compress;
    
    /**
     * encryption key - null for no encryption
     */
    private byte[] key;

    /**
     * whether to append to an existing file
     */
    private boolean append = false;

    /**
     * limit bandwidth usage (i.e. transfer rate) in bytes per second. Only
     * active if greater than zero
     */
    private long rateLimit = 0;

    // colon-separated list of file patterns that are allowed to access
    private String includes;
    
    // colon-separated list of file patterns that are forbidden to access
    private String excludes;

    // offset for supporting partial reads of single files
    private long offset = 0;
    
    // overall session permissions
    private Session.Mode accessPermissions = null;
    
    /**
     * 
     * @param client - acceptable IP address(es) of the client
     * @param user - Unix user name
     * @param secret - one-time password to use
     * @param file - the file to send/receive
     * @param send - <code>true</code> if server should send data
     */
    public UFTPTransferRequest(InetAddress[] client,  String user, String secret, File file, boolean send) {
        super(client, user, secret);
        this.send = send;
        this.file = file;
    }

    /**
     * build a job from the given string in the java properties format
     * 'client-ip=...' 'send=...' 'file=-...' 'numcons=...' 'secret=....'
     *
     * @param properties properties
     * @throws UnknownHostException
     * @throws IOException when encoded properties cannot be read
     */
    public UFTPTransferRequest(Properties properties) {
    	super(Utils.parseInetAddresses(properties.getProperty("client-ip"),logger), 
    			properties.getProperty("user"),
        		properties.getProperty("secret"));
        send = Boolean.parseBoolean(properties.getProperty("send"));
        file = new File(properties.getProperty("file"));
        append = Boolean.parseBoolean(properties.getProperty("append"));
        compress = Boolean.parseBoolean(properties.getProperty("compress"));
        streams = Integer.parseInt(properties.getProperty("streams", "2"));
        key = Utils.decodeBase64(properties.getProperty("key"));
        group = properties.getProperty("group");
        rateLimit = Long.parseLong(properties.getProperty("rateLimit", "0"));
        offset = Long.parseLong(properties.getProperty("offset", "0"));
        includes = properties.getProperty("includes");
        excludes = properties.getProperty("excludes");
        accessPermissions = Session.Mode.valueOf(properties.getProperty("access-permissions", "FULL"));
        isPersistent = Boolean.parseBoolean(properties.getProperty("persistent"));
    }

    public UFTPTransferRequest(String properties) throws UnknownHostException, IOException {
        this(loadProperties(properties));
    }

    /**
     * writes out the job in properties format 'client-ip=...' 'send=...' etc
     *
     * @param os output stream where the options are written to
     */
    public void writeEncoded(OutputStream os) throws IOException {
        super.writeEncoded(os);
        os.write(("send=" + String.valueOf(send) + "\n").getBytes());
        os.write(("file=" + file.getPath() + "\n").getBytes());
        os.write(("append=" + String.valueOf(append) + "\n").getBytes());
        os.write(("streams=" + String.valueOf(streams) + "\n").getBytes());
        os.write(("compress=" + String.valueOf(compress) + "\n").getBytes());
        if(group!=null){
        	os.write(("group=" + group + "\n").getBytes());
        }
        if (rateLimit > 0) {
            os.write(("rateLimit=" + rateLimit + "\n").getBytes());
        }
        if (send && offset > 0) {
            os.write(("offset=" + offset+ "\n").getBytes());
        }
        if (key != null) {
            os.write(("key=" + Utils.encodeBase64(key) + "\n").getBytes());
        }
        if (includes !=null) {
            os.write(("includes=" + includes + "\n").getBytes());
        }
        if (excludes !=null) {
            os.write(("excludes=" + excludes + "\n").getBytes());
        }
        if (accessPermissions !=null) {
            os.write(("access-permissions=" + accessPermissions + "\n").getBytes());
        }
        os.write("END\n".getBytes());
        os.flush();
    }

    @Override
    public String toString() {
        return "UFTPTransferRequest <" + getJobID()
	    + "> user=" + getUser() + " group=" + group
	    +" clientIP(s)=" + Arrays.asList(getClient())
	    + ( isSession()?" session / persistent=" + isPersistent
		: (" file=" + file.getPath() + " send=" + send) )
	    + " streams=" + streams
	    + " compress=" + compress
	    + " encrypted=" + (key != null);
    }

    @Override
    protected String getRequestType(){
    	return REQUEST_TYPE;
    }

    public boolean isSession(){
    	return file!=null && UFTPWorker.sessionModeTag.equals(file.getName());
    }

    public static boolean isTransferRequest(Properties props){
    	String reqType = props.getProperty("request-type",REQUEST_TYPE);
    	return REQUEST_TYPE.equals(reqType);
    }

	public boolean isSend() {
		return send;
	}

	public File getFile() {
		return file;
	}

	public int getStreams() {
		return streams;
	}

	public void setStreams(int streams) {
		this.streams = streams;
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public boolean isCompress() {
		return compress;
	}

	public void setCompress(boolean compress) {
		this.compress = compress;
	}

	public byte[] getKey() {
		return key;
	}

	public void setKey(byte[] key) {
		this.key = key;
	}

	public boolean isAppend() {
		return append;
	}

	public void setAppend(boolean append) {
		this.append = append;
	}

	public long getRateLimit() {
		return rateLimit;
	}

	public void setRateLimit(long rateLimit) {
		this.rateLimit = rateLimit;
	}

	public String getIncludes() {
		return includes;
	}

	public void setIncludes(String includes) {
		this.includes = includes;
	}

	public String getExcludes() {
		return excludes;
	}

	public void setExcludes(String excludes) {
		this.excludes = excludes;
	}

	public long getOffset() {
		return offset;
	}

	public void setOffset(long offset) {
		this.offset = offset;
	}

	public Session.Mode getAccessPermissions() {
		return accessPermissions;
	}

	public void setAccessPermissions(Session.Mode mode) {
		this.accessPermissions = mode;
	}

}

