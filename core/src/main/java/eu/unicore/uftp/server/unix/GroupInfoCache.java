package eu.unicore.uftp.server.unix;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.dpc.Utils;

public class GroupInfoCache {
	
	private static final Logger logger=Utils.getLogger(Utils.LOG_SECURITY, GroupInfoCache.class);
	
	private static final Map<String, UnixGroup>groups = new HashMap<>();
	
	private static final Map<String, Long>retrieved = new HashMap<>();
	
	// cache entry time to live in millis
	static final long cacheTTL = 10 * 60 * 1000;
	
	public static synchronized UnixGroup getGroup(String groupName){
		if(groupName==null)return null;
		UnixGroup gr = groups.get(groupName);
		if(gr==null || System.currentTimeMillis()>cacheTTL+retrieved.get(groupName)){
			gr = new UnixGroup(groupName);
			groups.put(groupName, gr);
			retrieved.put(groupName, System.currentTimeMillis());
			logger.debug("Loaded group info "+gr);
		}
		return gr;
	}
	
}
