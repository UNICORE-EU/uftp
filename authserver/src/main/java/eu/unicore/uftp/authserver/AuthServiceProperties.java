package eu.unicore.uftp.authserver;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.Logger;

import com.google.common.collect.Maps;

import eu.unicore.util.Log;
import eu.unicore.util.configuration.ConfigurationException;
import eu.unicore.util.configuration.DocumentationReferenceMeta;
import eu.unicore.util.configuration.DocumentationReferencePrefix;
import eu.unicore.util.configuration.PropertiesHelper;
import eu.unicore.util.configuration.PropertyGroupHelper;
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
	
	@DocumentationReferenceMeta
	public final static Map<String, PropertyMD> META = new HashMap<>();
	static
	{
		META.put(PROP_SERVERS, new PropertyMD().
				setDescription("Lists the servers, which are further configured with additional properties."));
		META.put(PROP_UFTPD_PREFIX, new PropertyMD().setCanHaveSubkeys().
				setDescription("Properties with this prefix are used to configure the UFTPD server. See separate documentation for details."));
	}

	private final Properties rawProperties;

	/**
	 * @param p - raw properties
	 * @throws ConfigurationException
	 */
	public AuthServiceProperties(Properties p) throws ConfigurationException {
		super(PREFIX, p, META, propsLogger);
		this.rawProperties = p;
	}

	public String getServers() {
		return getValue(PROP_SERVERS);
	}

	public boolean hasChanges(Properties newProperties) {
		var existing = new PropertyGroupHelper(rawProperties, PREFIX).getFilteredMap();
		var newValues = new PropertyGroupHelper(newProperties, PREFIX).getFilteredMap();
		return !Maps.difference(existing, newValues).areEqual();
	}

}