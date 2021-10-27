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
import eu.unicore.uftp.authserver.authenticate.UserAttributes;
import eu.unicore.util.Log;

/**
 * some common stuff for the auth and sharing services
 *  
 * @author schuller
 */
public abstract class ServiceBase extends ApplicationBaseResource implements KernelInjectable {

	private static final Logger logger = Log.getLogger("authservice", ServiceBase.class);

	protected static final Gson gson = new GsonBuilder().create();
	
	protected UserAttributes assembleAttributes(String serverName, String requestedGroup){
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
				logger.warn("Attribute <"+a.getName()+"> for "+base+" not known.");
			}
		}
		UserAttributes ua = new UserAttributes(uid, gid, groups);
		ua.rateLimit = rateLimit;
		ua.includes = includes;
		ua.excludes = excludes;
		return ua;
	}

	protected AuthServiceProperties getAuthServiceProperties(){
		return kernel.getAttribute(AuthServiceProperties.class);
	}

	protected boolean haveServer(String serverName){
		return getAuthServiceProperties().getServer(serverName)!=null;
	}
	
	protected UFTPDInstance getServer(String serverName) throws IOException {
		return getAuthServiceProperties().getServer(serverName).getUFTPDInstance();
	}
	
	
	@Override
	protected Map<String, Object> getProperties() throws Exception {
		 Map<String, Object>res = new HashMap<>();
		 return res;
	}

}
