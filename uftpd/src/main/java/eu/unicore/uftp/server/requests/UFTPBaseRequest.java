package eu.unicore.uftp.server.requests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.dpc.Utils;

/**
 * request to the UFTPD sent via the command socket
 * 
 * @author schuller
 */
public abstract class UFTPBaseRequest {
    
	protected static final Logger logger = Utils.getLogger(Utils.LOG_CLIENT, UFTPBaseRequest.class);
    
	/**
     * the instant at which this job was created
     */
	private final long createdTime;

	private static final AtomicInteger job_id_generator = new AtomicInteger();

	private final Integer jobID = job_id_generator.incrementAndGet();

	/**
	 * @param user 
	 * @param secret 
	 * @throws UnknownHostException 
	 * 
	 */
	protected UFTPBaseRequest(InetAddress[] clientAddress, String user, String secret) {
		this.clientAddress = clientAddress; 
		this.user = user;
		this.secret = secret;
		createdTime = System.currentTimeMillis();
	}

	public static Properties loadProperties(String props) throws IOException {
		StringReader r = new StringReader(props);
	    Properties p = new Properties();
	    p.load(r);
	    return p;
	}

	private final InetAddress[] clientAddress;
	
	/**
	 * the user for which to create the file
	 */
	private final String user;

	/**
	 * authorization secret
	 */
	private final String secret;

	/**
	 * allow multiple sessions/connections for the same request
	 * (usually: no)
	 */
	protected boolean isPersistent = false;
	
    private final AtomicInteger activeSessions = new AtomicInteger(0);

	/**
     * writes out the request in properties format 'client-ip=...' 'send=...' etc
     *
     * @param os output stream where the options are written to
     */
    public void writeEncoded(OutputStream os) throws IOException {
        os.write(("request-type="+ getRequestType() + "\n").getBytes());
    	os.write(("client-ip=" + Utils.encodeInetAddresses(clientAddress) + "\n").getBytes());
        os.write(("user=" + ( user != null ? user : "") + "\n").getBytes());
        os.write(("secret=" + secret + "\n").getBytes());
        if (isPersistent) {
            os.write(("persistent=" + isPersistent + "\n").getBytes());
        }
    };
    
    protected abstract String getRequestType();

    /**
     * utility method to send this job to a server.
     * 
     * @param server server address
     * @param port server port
     * @return server response
     */
    public String sendTo(InetAddress server, int port) throws IOException {
        try(Socket s = new Socket(server, port)){
        	return sendTo(s);
        }
    }

    /**
     * utility method to send this job to a socket
     * 
     * @param s socket to send the job to
     * @return server response
     * @throws java.io.IOException
     */
	public String sendTo(Socket s) throws IOException {
		if(logger.isDebugEnabled())logger.debug("Sending " + getRequestType() + " to " + s.getInetAddress().getHostAddress() + ":" + s.getPort()
				+ " : " + this);
		try {
			writeEncoded(s.getOutputStream());
			String res = readResponse(s.getInputStream());
			logger.debug("Finished sending request, got response '" + res + "'");
			return res;
		} finally {
			s.close();
		}
	}

    protected String readResponse(InputStream is) throws IOException {
    	StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String line = null;
        do{
        	line = br.readLine();
        	if(line!=null){
        		sb.append(line);
        		sb.append("\n");
        	}
        }while(line!=null);
        return sb.toString();
    }

	/**
	 * get the client IP address
	 * 
	 * @return client address
	 */
	public InetAddress[] getClient() {
	    return clientAddress;
	}

	public String getUser() {
	    return user;
	}

	/**
	 * @return the createdTime
	 */
	public long getCreatedTime() {
		return createdTime;
	}

	/**
	 * returns the secret
	 *
	 * @return secret
	 */
	public String getSecret() {
	    return secret;
	}
	
	public void setPersistent(boolean persistent) {
		this.isPersistent = persistent;
	}
	
	public boolean isPersistent() {
		return isPersistent;
	}

	public int getActiveSessions() {
		return activeSessions.get();
	}
	
	public int newActiveSession() {
		return activeSessions.incrementAndGet();
	}
	
	public int endActiveSession() {
		return activeSessions.decrementAndGet();
	}

	public int getJobID() {
		return jobID;
	}

}
