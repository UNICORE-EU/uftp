package eu.unicore.uftp.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.dpc.AuthorizationFailureException;
import eu.unicore.uftp.dpc.DPCServer;
import eu.unicore.uftp.dpc.DPCServer.Connection;
import eu.unicore.uftp.dpc.ProtocolViolationException;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.server.requests.UFTPBaseRequest;
import eu.unicore.uftp.server.unix.UnixUser;
import eu.unicore.uftp.server.workers.WorkerFactories;

/**
 * This class manages a list of {@link UFTPBaseRequest} instances, and waits
 * for connection attempts from clients. For each connection attempt, it is
 * checked whether a matching job exists.<br/>
 *
 * If no client connects, a job will be expired after a while.
 *
 * @author Tim Pohlmann, 2010, Forschungszentrum Juelich - JSC
 * @author schuller
 */
public class ServerThread extends Thread {

    private static final Logger logger = Utils.getLogger(Utils.LOG_SERVER, ServerThread.class);
    
    private final DPCServer server;

    private final JobStore jobStore = new JobStore();

    private final Map<InetAddress, AtomicInteger> runningConnectionsMap;

    private final FileAccess fileAccess;
    
    private final boolean haveUnixUser;
    
    private volatile boolean isHalt = false;

    private int maxStreams;
    private int maxControlConnectionsPerClient = 128;
    private int bufferSize = FileAccess.DEFAULT_BUFFERSIZE;
    

    /**
     * create a new server thread
     *
     * @param ip - server IP address
     * @param port - server listen port
     * @param backlog - server socket backlog
     * @param maxStreams - maximum number of TCP streams per transfer
     * @throws IOException
     */
	public ServerThread(InetAddress ip, int port, int backlog, int maxStreams,
			String advertiseAddress, PortManager pm, boolean checkClientIP) throws IOException {
		this.maxStreams = maxStreams;
        runningConnectionsMap = new ConcurrentHashMap<InetAddress, AtomicInteger>();
        setupExpiryCheck();
        fileAccess = Utils.getFileAccess(logger);
        haveUnixUser = fileAccess instanceof SetUIDFileAccess;
        this.server = new DPCServer(ip, port, backlog, jobStore, advertiseAddress, pm, checkClientIP);
    }

    /**
     * while not halted, new connections are accepted and processed
     * asynchronously using a thread pool
     */
    @Override
    public void run() {
        try {
            while (!isHalt) {
                try {
                    final Connection connection = server.accept();
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            processConnection(connection, maxStreams);
                        }
                    };
                    Utils.getExecutor().execute(r);
                } catch (SocketTimeoutException ste) {
                    //timeout, just re-try if not halted
                } catch (IOException e) {
                    if (e instanceof SocketException) {
                        throw (SocketException) e;
                    }
                    // other IO Exceptions are ignored 
                }
            }
        } catch (SocketException e) {
            // socket was closed
        }
    }

    private void processConnection(final Connection connection, int maxStreams) {
        InetAddress client = connection.getAddress();
        try {
        	final UFTPBaseRequest job = checkValid(connection);
        	if(job!=null){
        		WorkerFactories.INSTANCE.createWorker(this, connection, job, maxStreams, bufferSize).start();
        		return;
        	}
        } catch (ProtocolViolationException e) {
            logger.info("Rejecting connection attempt from " + client + ": protocol violation.", e);
        } catch (AuthorizationFailureException e) {
            logger.info("Rejecting connection attempt from " + client + ": authorization failed.", e);
        } catch (Exception ex) {
            logger.info("Unkown Error occured", ex);
        }
        // we got here in case of errors, so we can close the connection 
        connection.close();
    }
    
    
    private UFTPBaseRequest checkValid(final Connection connection) throws IOException, AuthorizationFailureException{
    	UFTPBaseRequest job = null;
    	InetAddress client = connection.getAddress();
    	List<UFTPBaseRequest> jobs = jobStore.getJobs(client);
    	if (server.isCheckClientIP() && (jobs == null || jobs.isEmpty())) {
    		logger.info("Rejecting connection from " + client + " : no valid job for that client address.");
    	}
    	else {
    		AtomicInteger counter;
    		synchronized (runningConnectionsMap) {
    			counter = runningConnectionsMap.get(client);
    			if (counter == null) {
    				counter = new AtomicInteger();
    			}
    			runningConnectionsMap.put(client, counter);
    		}
    		if (counter.get() == maxControlConnectionsPerClient) {
    			//too many connections already
    			logger.info("Rejecting connection from " + client + " : too many connections for that client .");
    		}
    		else {
    			job = connection.establish();
    			if(!job.isPersistent())jobStore.remove(job);
    	        counter.incrementAndGet();
    		}
    	}
    	return job;
    }

    public void addJob(UFTPBaseRequest job) {
        if (haveUnixUser) {
            UnixUser user = new UnixUser(job.getUser());
            logger.debug("Have Unix user: {}", user.toString());
        }
        jobStore.addJob(job);
    }

    /**
     * shut down the server
     *
     * @throws IOException
     */
    public void close() throws IOException {
        logger.info("Closing UFTPD server");
        halt();
        server.close();
    }

    /**
     * set the timeout for accepting new connections
     *
     * @param time Timeout in ms
     */
    public void setTimeout(int time) {
        server.setTimeout(time);
    }

    /**
     * return the server timeout
     *
     * @return Timeout in ms
     */
    public int getTimeout() {
        return server.getTimeout();
    }
    
    public int getPort() {
        return server.getPort();
    }
    
    public String getHost()  {
        return server.getHost();
    }

    public int getMaxControlConnectionsPerClient() {
        return maxControlConnectionsPerClient;
    }

    public void setMaxControlConnectionsPerClient(int maxControlConnectionsPerClient) {
        this.maxControlConnectionsPerClient = maxControlConnectionsPerClient;
    }

    public void setMaxStreamsPerConnection(int maxStreams) {
        if (maxStreams < 1) {
            throw new IllegalArgumentException("Maximum streams per connection must be >=1, got: " + maxStreams);
        }
        this.maxStreams = maxStreams;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        if (bufferSize < 1) {
            throw new IllegalArgumentException("Buffer size must be >=1, got: " + bufferSize);
        }
        this.bufferSize = bufferSize;
    }


	public void setCheckClientIP(boolean checkIP){
		server.setCheckClientIP(checkIP);
	}
	
	public void setRFCRangeMode(boolean rfcMode){
		server.setRFCRangeMode(rfcMode);
	}

	public boolean getRFCRangeMode(){
		return server.getRFCRangeMode();
	}

    /**
     * a connection to the given client was closed
     *
     * @param client
     */
    public void notifyConnectionClosed(InetAddress client) {
        AtomicInteger i = runningConnectionsMap.get(client);
        if (i != null) {
            i.decrementAndGet();
        }
    }

    public void halt() {
        isHalt = true;
    }

    /**
     * schedule expiry check
     */
    private final void setupExpiryCheck() {
        Runnable r = new Runnable() {
            public void run() {
                try{
		    jobStore.checkForExpiredJobs();
                }catch(Exception ex) {}
            }
        };
        long delay = Math.max(jobStore.getJobLifetime()/3, 60);
        Utils.getExecutor().scheduleWithFixedDelay(r, delay, 60, TimeUnit.SECONDS);
    }

    public void cleanConnectionCounters() {
        Iterator<Map.Entry<InetAddress, AtomicInteger>> iterator = runningConnectionsMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<InetAddress, AtomicInteger> entry = iterator.next();
            AtomicInteger counter = entry.getValue();
            if (counter.get() == 0) {
                iterator.remove();
            }
        }
    }

    public FileAccess getFileAccess() {
        return fileAccess;
    }
    
    public void invalidateJob(UFTPBaseRequest job) {
    	jobStore.remove(job);
    }
   
}
