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

/**
 * Requests permission for a client to open a session on
 * the UFTPD server
 *
 * @author Tim Pohlmann, 2010, Forschungszentrum Juelich - JSC
 * @author schuller
 */
public class UFTPSessionRequest extends UFTPBaseRequest {

    public static final String REQUEST_TYPE = "uftp-transfer-request";

    /**
     * base directory of the session
     */
    private final File baseDirectory;

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
    
    // used only for backwards compatibility
    private static final String _sessionModeTag = "___UFTP___MULTI___FILE___SESSION___MODE___";

    /**
     * 
     * @param client - acceptable IP address(es) of the client
     * @param user - Unix user name
     * @param secret - one-time password to use
     * @param baseDirectory - the base directory of the session
     */
    public UFTPSessionRequest(InetAddress[] client,  String user, String secret, File baseDirectory) {
        super(client, user, secret);
        // for backwards compatibility
        if(baseDirectory.getPath().equals(_sessionModeTag)) {
            baseDirectory = new File("");
        }
        else if(baseDirectory.getName().equals(_sessionModeTag)) {
            baseDirectory = baseDirectory.getParentFile();
            if (baseDirectory==null) {
                baseDirectory = new File(".");
            }
        }
        this.baseDirectory = baseDirectory;
    }

    /**
     * build a job from the given string in the java properties format
     * 'client-ip=...' 'send=...' 'file=-...' 'numcons=...' 'secret=....'
     *
     * @param properties properties
     * @throws UnknownHostException
     * @throws IOException when encoded properties cannot be read
     */
    public UFTPSessionRequest(Properties properties) {
    	super(Utils.parseInetAddresses(properties.getProperty("client-ip"),logger), 
    			properties.getProperty("user"),
        		properties.getProperty("secret"));
        baseDirectory = new File(properties.getProperty("file"));
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

    public UFTPSessionRequest(String properties) throws UnknownHostException, IOException {
        this(loadProperties(properties));
    }

    /**
     * writes out the job in properties format 'client-ip=...' 'send=...' etc
     *
     * @param os output stream where the options are written to
     */
    public void writeEncoded(OutputStream os) throws IOException {
        super.writeEncoded(os);
        String dir = baseDirectory.getPath();
        if(dir.length()>0 && !dir.endsWith("/"))dir+="/";
        os.write(("file=" + dir + "\n").getBytes());
        os.write(("append=" + String.valueOf(append) + "\n").getBytes());
        os.write(("streams=" + String.valueOf(streams) + "\n").getBytes());
        os.write(("compress=" + String.valueOf(compress) + "\n").getBytes());
        if(group!=null){
        	os.write(("group=" + group + "\n").getBytes());
        }
        if (rateLimit > 0) {
            os.write(("rateLimit=" + rateLimit + "\n").getBytes());
        }
        if (offset > 0) {
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
        return "UFTPSessionRequest <" + getJobID()
	    + "> user=" + getUser() + " group=" + group
	    +" clientIP(s)=" + Arrays.asList(getClient())
	    + " baseDirectory=" + baseDirectory.getPath()
	    + " streams=" + streams
	    + " compress=" + compress
	    + " encrypted=" + (key != null);
    }

    @Override
    protected String getRequestType(){
    	return REQUEST_TYPE;
    }

    public static boolean isTransferRequest(Properties props){
    	String reqType = props.getProperty("request-type",REQUEST_TYPE);
    	return REQUEST_TYPE.equals(reqType);
    }

	public File getBaseDirectory() {
		return baseDirectory;
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

