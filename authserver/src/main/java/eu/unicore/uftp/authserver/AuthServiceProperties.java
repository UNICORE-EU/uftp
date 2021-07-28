package eu.unicore.uftp.authserver;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.Logger;

import eu.unicore.services.Kernel;
import eu.unicore.util.Log;
import eu.unicore.util.configuration.ConfigurationException;
import eu.unicore.util.configuration.DocumentationReferenceMeta;
import eu.unicore.util.configuration.DocumentationReferencePrefix;
import eu.unicore.util.configuration.PropertiesHelper;
import eu.unicore.util.configuration.PropertyMD;

/**
 * Configure a single instance of the AuthService, which is 
 * responsible for one or more UFTPD instances
 * 
 * @author schuller
 */
public class AuthServiceProperties extends PropertiesHelper {
	
	private static final Logger propsLogger=Log.getLogger(Log.CONFIGURATION, AuthServiceProperties.class);

	@DocumentationReferencePrefix
	public static final String PREFIX = "authservice.";
	
	public static final String PROP_UFTPD_PREFIX = "server.";
    
	/**
	 * list of configured servers
	 */
	public static final String PROP_SERVERS = "servers";
	
	private final Kernel kernel;
	
	@DocumentationReferenceMeta
	public final static Map<String, PropertyMD> META = new HashMap<String, PropertyMD>();
	static
	{
		META.put(PROP_SERVERS, new PropertyMD().
				setDescription("Lists the servers, which are further configured with additional properties."));
		META.put(PROP_UFTPD_PREFIX, new PropertyMD().setCanHaveSubkeys().
				setDescription("Properties with this prefix are used to configure the UFTPD server. See separate documentation for details."));
	}

	private Properties rawProperties;
	
	/**
	 * @param p - raw properties
	 * @param k - USE kernel
	 * @throws ConfigurationException
	 */
	public AuthServiceProperties(Properties p, Kernel k) throws ConfigurationException {
		super(PREFIX, p, META, propsLogger);
		this.rawProperties = p;
		this.kernel = k;
		configure();
	}
	
	private final Map<String, LogicalUFTPServer>serverMap = new HashMap<>();
	
	public LogicalUFTPServer getServer(String name){
		return serverMap.get(name);
	}
	
	public Collection<LogicalUFTPServer> getServers(){
		return Collections.unmodifiableCollection(serverMap.values());
	}
	
	protected void configure() throws ConfigurationException {
		String serversProp = getValue(PROP_SERVERS);
		if(serversProp==null) {
			// don't really want to fail startup here
			propsLogger.warn("No UFTPD servers defined (property '"+PREFIX+PROP_SERVERS+"')");
			return;
		}
		String[] servers = serversProp.split(" +");
		propsLogger.info("Will configure servers: "+Arrays.asList(servers));
		for(String s: servers){
			LogicalUFTPServer server = configure(s);
			serverMap.put(s, server);
			propsLogger.info("Configured server: "+server);
		}
	}
	
	protected LogicalUFTPServer configure(String name) throws ConfigurationException {
		LogicalUFTPServer server = new LogicalUFTPServer(name, kernel);
		server.configure(name, rawProperties);
		return server;
	}

}