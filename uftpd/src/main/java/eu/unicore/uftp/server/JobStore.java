package eu.unicore.uftp.server;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.server.requests.UFTPBaseRequest;

/**
 * stores UFTP requests and offers various retrieval methods
 * 
 * @author schuller
 */
public class JobStore {

	private static final Logger logger = Utils.getLogger(Utils.LOG_SERVER, JobStore.class);

	// default maximum job age is 10 minutes
	public static final long MAX_JOB_AGE_DEFAULT = 10 * 60;

	public final long maxJobAge;

	private final Map<String,UFTPBaseRequest>jobs = new ConcurrentHashMap<>();

	public JobStore(){
		maxJobAge = Integer.parseInt(System.getProperty("uftpd.maxJobAge", "" + MAX_JOB_AGE_DEFAULT));
		logger.info("Limiting request lifetime to " + maxJobAge + " seconds.");
	}

	public void addJob(UFTPBaseRequest job){
		if(jobs.containsKey(job.getSecret()))throw new IllegalArgumentException("Secret already in use");
		jobs.put(job.getSecret(), job);
	}

	public void remove(UFTPBaseRequest job){
		if(job!=null){
			jobs.remove(job.getSecret());
		}
	}

	public UFTPBaseRequest getJob(String secret){
		return jobs.get(secret);
	}
	
	public List<UFTPBaseRequest>getJobs(InetAddress clientAddress){
		List<UFTPBaseRequest>result = new ArrayList<>();
		Iterator<Map.Entry<String, UFTPBaseRequest>> iterator = jobs.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, UFTPBaseRequest> entry = iterator.next();
			UFTPBaseRequest job = entry.getValue();
			if(Arrays.asList(job.getClient()).contains(clientAddress)){
				result.add(job);
			};
		}
		return result;
	}

	public boolean haveJobs(InetAddress inetAddress){
		Iterator<Map.Entry<String, UFTPBaseRequest>> iterator = jobs.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, UFTPBaseRequest> entry = iterator.next();
			UFTPBaseRequest job = entry.getValue();
			if(Arrays.asList(job.getClient()).contains(inetAddress))return true;
		}
		return false;
	}


	/**
	 * check if "zombie" jobs exist and clean them up
	 */
	public void checkForExpiredJobs() {
		List<UFTPBaseRequest> toRemove= new ArrayList<>();
		Iterator<Map.Entry<String, UFTPBaseRequest>> iterator = jobs.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, UFTPBaseRequest> entry = iterator.next();
			UFTPBaseRequest job = entry.getValue();
			long age = System.currentTimeMillis() - job.getCreatedTime();
			boolean expired = age > 1000*maxJobAge;
			logger.debug("Checking job {} for {} active-sessions={} expired={}",
					job.getJobID(), job.getUser(), job.getActiveSessions(), expired);
			if(job.isPersistent()) {
				expired = expired && job.getActiveSessions()==0;
			}
			if (expired) {
				logger.info("Removing expired job from " + job.getUser()
				+ " @ " + Utils.encodeInetAddresses(job.getClient()));
				toRemove.add(job);
			}
		}
		for(UFTPBaseRequest j: toRemove){
			remove(j);
		}
	}

	/**
	 * job lifetime in seconds
	 */
	public long getJobLifetime() {
		return maxJobAge;
	}

}
