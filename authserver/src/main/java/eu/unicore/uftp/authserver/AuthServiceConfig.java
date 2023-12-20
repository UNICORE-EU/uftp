package eu.unicore.uftp.authserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import eu.unicore.services.ExternalSystemConnector;
import eu.unicore.services.ISubSystem;
import eu.unicore.services.Kernel;
import eu.unicore.util.Log;
import eu.unicore.util.configuration.ConfigurationException;

public class AuthServiceConfig implements ISubSystem {

	private static final Logger propsLogger=Log.getLogger(Log.CONFIGURATION, AuthServiceConfig.class);

	private final Map<String, UFTPBackend>serverMap = new HashMap<>();

	private final Kernel kernel;

	private AuthServiceProperties props;
	
	AuthServiceConfig(Kernel k){
		this.kernel = k;
		configure();
	}

	public static synchronized AuthServiceConfig get(Kernel kernel) {
		AuthServiceConfig ass = kernel.getAttribute(AuthServiceConfig.class);
		if(ass==null) {
			ass = new AuthServiceConfig(kernel);
			kernel.setAttribute(AuthServiceConfig.class, ass);
			kernel.register(ass);
		}
		return ass;
	}

	@Override
	public String getStatusDescription() {
		StringBuilder sb = new StringBuilder();
		if(getServers().size()>0) {
			for(UFTPBackend uftp: getServers()) {
				sb.append(uftp.getServerName()).append(" ");
			}
		}
		else {
			sb.append("No UFTP servers configured.");
		}
		return sb.toString();
	}

	@Override
	public String getName() {
		return "Auth service";
	}

	@Override
	public Collection<ExternalSystemConnector> getExternalConnections(){
		Collection<ExternalSystemConnector>res = new ArrayList<>();
		for(UFTPBackend server: getServers()){
			res.addAll(server.getExternalConnections());
		}
		return res;
	}

	@Override
	public void reloadConfig(Kernel kernel) {
		configure();
	}

	public UFTPBackend getServer(String name){
		return serverMap.get(name);
	}
	
	public Collection<UFTPBackend> getServers(){
		return Collections.unmodifiableCollection(serverMap.values());
	}
	
	protected void configure() throws ConfigurationException {
		if(props!=null && !props.hasChanges(kernel.getContainerProperties().getRawProperties())) {
			propsLogger.info("No changes, skipping re-configuration.");
			return;
		}
		props = new AuthServiceProperties(kernel.getContainerProperties().getRawProperties());
		String serversProp = props.getServers();
		if(serversProp==null) {
			propsLogger.info("No UFTPD servers defined.");
			return;
		}
		String[] servers = serversProp.split(" +");
		propsLogger.info("Will configure servers: {}", Arrays.asList(servers));
		Map<String,UFTPBackend> newServerMap = new HashMap<>();
		for(String s: servers){
			UFTPBackend server = configure(s);
			newServerMap.put(s, server);
			propsLogger.info("Configured server: {}", server);
		}
		Iterator<String> oldServers = serverMap.keySet().iterator();
		while(oldServers.hasNext()) {
			String s = oldServers.next();
			if(!newServerMap.containsKey(s)) {
				oldServers.remove();
			}
		}
		serverMap.putAll(newServerMap);
	}
	
	protected UFTPBackend configure(String name) throws ConfigurationException {
		UFTPBackend server = new UFTPBackend(name, kernel);
		server.configure(name, kernel.getContainerProperties().getRawProperties());
		return server;
	}
}
