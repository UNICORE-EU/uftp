package eu.unicore.uftp.authserver.share;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.datashare.db.ACLStorage;
import eu.unicore.util.Log;
import eu.unicore.util.configuration.ConfigurationException;
import eu.unicore.util.configuration.DocumentationReferenceMeta;
import eu.unicore.util.configuration.DocumentationReferencePrefix;
import eu.unicore.util.configuration.PropertiesHelper;
import eu.unicore.util.configuration.PropertyMD;

/**
 * Configure the sharing service
 * 
 * @author schuller
 */
public class ShareServiceProperties extends PropertiesHelper {

	private static final Logger propsLogger=Log.getLogger(Log.CONFIGURATION, ShareServiceProperties.class);

	@DocumentationReferencePrefix
	public static final String PREFIX = "share.";

	/**
	 * list of servers for which sharing should be enabled
	 */
	public static final String PROP_SERVERS = "servers";

	public static final String PROP_WRITE = "allowWriteAccess";
	
	public static final String PROP_CLIENTIP = "clientIP";

	public static final String PROP_USER_IDENTITY_TYPE = "userIdentityType";
	public static final String PROP_USER_IDENTITY_EXTRACTOR = "userIdentityExtractor";
	
	/**
	 * base for per-server properties
	 */
	public static final String PROP_UFTPD_PREFIX = "server.";

	@DocumentationReferenceMeta
	public final static Map<String, PropertyMD> META = new HashMap<>();
	static
	{
		META.put(PROP_SERVERS, new PropertyMD().
				setDescription("Lists the servers for which data should be shared, which are further configured with additional properties."));
		META.put(PROP_WRITE, new PropertyMD("false").setBoolean().
				setDescription("Should WRITE access be allowed."));
		META.put(PROP_CLIENTIP, new PropertyMD("localhost").
				setDescription("Client IP to use when validating."));
		META.put(PROP_USER_IDENTITY_TYPE, new PropertyMD().setDeprecated().setDescription("DEPRECATED, no effect"));
		META.put(PROP_USER_IDENTITY_EXTRACTOR, new PropertyMD().setDeprecated().setDescription("DEPRECATED, no effect"));
		META.put(PROP_UFTPD_PREFIX, new PropertyMD().setCanHaveSubkeys().
				setDescription("Properties with this prefix are used to configure the server. See separate documentation for details."));
	}

	private final Properties rawProperties;

	/**
	 * @param p - raw properties
	 * @throws ConfigurationException
	 */
	public ShareServiceProperties(Properties p) throws ConfigurationException {
		super(PREFIX, p, META, propsLogger);
		this.rawProperties = p;
		configure();
	}

	private final Map<String, ACLStorage>dbMap = new HashMap<>();

	public ACLStorage getDB(String name){
		return dbMap.get(name);
	}

	public String getClientIP(){
		return getValue(PROP_CLIENTIP);
	}
	
	public boolean isWriteAllowed(){
		return getBooleanValue(PROP_WRITE);
	}
	
	protected void configure() throws ConfigurationException {
		String serversProp = getValue(PROP_SERVERS);
		if(serversProp==null){
			propsLogger.info("No servers configured for data sharing.");
			return;
		}
		String[] servers = getValue(PROP_SERVERS).split(" +");
		for(String s: servers){
			try{
				ACLStorage server = configure(s);
				if(server != null){
					dbMap.put(s, server);
					propsLogger.info("Configured sharing for server <{}>", s);
				}
			}catch(Exception pe){
				throw new ConfigurationException("Error configuring data sharing for server <"+s+">", pe);
			}
		}
	}

	protected ACLStorage configure(String name) throws Exception {
		Properties p = new Properties();
		p.putAll(rawProperties);
		return new ACLStorage(name, p);
	}

}