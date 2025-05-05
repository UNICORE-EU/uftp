package eu.unicore.uftp.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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

    private final Map<InetAddress, AtomicInteger> runningConnectionsMap = new ConcurrentHashMap<>();

    private final FileAccess fileAccess = Utils.getFileAccess(logger);

    private volatile boolean isHalt = false;

    private int maxStreams;
    private int maxControlConnectionsPerClient = 128;
    private int bufferSize = FileAccess.DEFAULT_BUFFERSIZE;
    

    /**
     * create a new server thread
     * @throws IOException
     */
	public ServerThread(InetAddress ip, int port, int backlog, int maxStreams,
			String advertiseAddress, PortManager pm, boolean checkClientIP) throws IOException {
		this.maxStreams = maxStreams;
        setupExpiryCheck();
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
                    Utils.getExecutor().execute(()->processConnection(connection, maxStreams));
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
    
    public int getPort() {
        return server.getPort();
    }

    public void setMaxControlConnectionsPerClient(int maxControlConnectionsPerClient) {
        this.maxControlConnectionsPerClient = maxControlConnectionsPerClient;
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

    public void notifyConnectionClosed(InetAddress client) {
        AtomicInteger i = runningConnectionsMap.get(client);
        if (i != null) {
            i.decrementAndGet();
        }
    }

    public void halt() {
        isHalt = true;
    }

    private final void setupExpiryCheck() {
    	Utils.getExecutor().scheduleWithFixedDelay(()-> {
    		try{
    			jobStore.checkForExpiredJobs();
    		}catch(Exception ex) {}},
    			Math.max(jobStore.getJobLifetime()/3, 60), 60, TimeUnit.SECONDS);
    }

    public FileAccess getFileAccess() {
        return fileAccess;
    }

    public void invalidateJob(UFTPBaseRequest job) {
    	jobStore.remove(job);
    }

}
