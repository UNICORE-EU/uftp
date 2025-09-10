package eu.unicore.uftp.authserver;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import eu.unicore.security.Client;
import eu.unicore.security.XACMLAttribute;
import eu.unicore.services.KernelInjectable;
import eu.unicore.services.rest.impl.ApplicationBaseResource;
import eu.unicore.services.security.util.AuthZAttributeStore;
import eu.unicore.util.Log;
import eu.unicore.util.Pair;

/**
 * some common stuff for the auth and sharing services
 *  
 * @author schuller
 */
public abstract class ServiceBase extends ApplicationBaseResource implements KernelInjectable {

	private static final Logger logger = Log.getLogger("authservice", ServiceBase.class);

	protected static final Gson gson = new GsonBuilder().create();
	
	protected UserAttributes assembleAttributes(UFTPBackend server, String requestedGroup){
		String serverName = server.getServerName();
		Client client = AuthZAttributeStore.getClient();
		String uid = client.getXlogin().getUserName();
		if(requestedGroup!=null)client.getXlogin().setSelectedGroup(requestedGroup);
		String gid = client.getXlogin().getGroup();
		String[] groups = client.getXlogin().getGroups();
		long rateLimit = 0;
		String includes = null;
		String excludes = null;
		String base = "uftpd."+serverName;
		// allow to override these with uftpd specific values
		List<XACMLAttribute> attr = client.getSubjectAttributes().getXacmlAttributes();
		for(XACMLAttribute a: attr){
			if(a.getName().equals(base+".xlogin")){
				uid = a.getValue();
			}
			else if(a.getName().equals(base+".group")){
				gid = a.getValue(); 
			}
			else if(a.getName().equals(base+".rateLimit")){
				rateLimit = UserAttributes.parseRate(a.getValue());
			}
			else if(a.getName().equals(base+".includes")){
				includes = a.getValue();
			}
			else if(a.getName().equals(base+".excludes")){
				excludes = a.getValue();
			}
			else if(a.getName().startsWith(base)){
				logger.warn("Attribute <{}> for {} not known.", a.getName(), base);
			}
		}
		// rate limit from reservations
		long limit = server.getRateLimit(uid);
		if(limit>0) {
			rateLimit = rateLimit>0 ? Math.min(rateLimit, limit) : limit;
		}
		UserAttributes ua = new UserAttributes(uid, gid, groups);
		ua.rateLimit = rateLimit;
		ua.includes = includes;
		ua.excludes = excludes;
		return ua;
	}

	protected AuthServiceConfig getConfig(){
		return kernel.getAttribute(AuthServiceConfig.class);
	}

	/**
	 * get the logical server for the  given server name
	 *
	 * @param serverName
	 */
	protected UFTPBackend getLogicalServer(String serverName) {
		Pair<String,Integer> sn = getServerSpec(serverName);
		UFTPBackend u = getConfig().getServer(sn.getM1());
		return u!=null && u.hasInstance(sn.getM2()) ? u : null;
	}

	/**
	 * get the concrete UFTPD for the given server name
	 *
	 * @param serverName
	 */
	protected UFTPDInstance getUFTPD(String serverName) throws IOException {
		Pair<String,Integer> sn = getServerSpec(serverName);
		return getConfig().getServer(sn.getM1()).getUFTPDInstance(sn.getM2());
	}
	
	@Override
	protected Map<String, Object> getProperties() throws Exception {
		 return new HashMap<>();
	}

	public static Pair<String, Integer> getServerSpec(String serverName){
		int split = serverName.lastIndexOf('-');
		Integer index = null;
		if(split>1) {
			String indexS = serverName.substring(split+1);
			try {
				index = Integer.parseInt(indexS);
				serverName = serverName.substring(0, split);
			}catch(NumberFormatException nfe) {}
		}
		return new Pair<>(serverName, index);
	}
	
    // used only for backwards compatibility
    protected static final String _tag = "___UFTP___MULTI___FILE___SESSION___MODE___";

}
