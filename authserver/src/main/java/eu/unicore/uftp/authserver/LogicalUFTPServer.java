package eu.unicore.uftp.authserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.Logger;

import eu.unicore.services.ExternalSystemConnector;
import eu.unicore.services.Kernel;
import eu.unicore.services.utils.Utilities;
import eu.unicore.util.Log;
import eu.unicore.util.configuration.ConfigurationException;
import eu.unicore.util.configuration.PropertyGroupHelper;

/**
 * Holds properties and parameters for set of UFTPD servers
 * that make up a single logical server that is used in 
 * round-robin mode
 *
 * @author schuller
 */
public class LogicalUFTPServer implements ExternalSystemConnector {

	public static final Logger log = Log.getLogger(Log.SERVICES, LogicalUFTPServer.class);
    

	private String statusMessage = "N/A";
	
	private String description="n/a";

	private Status status= Status.UNKNOWN;
		
	private final Kernel kernel;

	private final String serverName;
	
	private final List<UFTPDInstance> instances = new ArrayList<>();
	
	public LogicalUFTPServer(String serverName, Kernel kernel){
		this.serverName = serverName;
		this.kernel = kernel;
	}
	
	public void configure(String name, Properties properties) {
		String prefix = "authservice.server." + name + ".";
		String desc = properties.getProperty(prefix+"description", "n/a");
		setDescription(desc);
		if(properties.getProperty(prefix+"host")!=null) {
			UFTPDInstance server = createUFTPD(name, prefix, properties);
			instances.add(server);
		}
		else {
			int num = 1;
			while(true) {
				prefix = "authservice.server." + name + "." + num +".";
				if(properties.getProperty(prefix+"host")==null) {
					break;
				}
				UFTPDInstance server = createUFTPD(name+"-"+num, prefix, properties);
				server.setLogicalName(name);
				instances.add(server);
				num++;
			}
		}
		log.info("Configured <"+instances.size()+"> UFTPD server(s) as "+name);
	}
	
	private UFTPDInstance createUFTPD(String name, String prefix, Properties properties) {
		UFTPDInstance server = new UFTPDInstance(name, kernel);
		mapSettings(server, prefix, properties);
		if(server.getHost()==null)throw new ConfigurationException("Property 'host' not set!");
		kernel.getExternalSystemConnectors().add(server);
		log.info("Configured "+name+": "+server);
		return server;
	}

	private void mapSettings(Object thing, String prefix, Properties properties) {
		Map<String,String>params = new PropertyGroupHelper(properties, 
			new String[]{prefix}).getFilteredMap();
		params.remove(prefix+"class");
		Utilities.mapParams(thing, params, log);
	}
	
	
	public String getServerName(){
		return serverName;
	}
	
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
	
	public Status getConnectionStatus(){
		checkConnection();
		return status;
	}
	
	public String getConnectionStatusMessage(){
		checkConnection();
		return statusMessage;
	}

	public String toString(){
		return "[UFTP Server '"+serverName+"' "+getConnectionStatusMessage()+"]";
	}

	public String getExternalSystemName(){
		return "UFTP Server '"+serverName+"'";
	}

	public boolean isUFTPAvailable(){
		checkConnection();
		return Status.OK.equals(status);
	}
	
	private synchronized void checkConnection(){
		status = Status.DOWN;
		int avail = 0;
		
		for(UFTPDInstance i: instances){
			if(i.isUFTPAvailable()) {
				status = Status.OK;
				avail++;
				if(log.isDebugEnabled()) {
					log.debug("UFTP server "+i.getCommandHost()+":"+i.getCommandPort()+" is UP: "
						+i.getConnectionStatusMessage());
				}
			}
			else {
				if(log.isDebugEnabled()) {
					log.debug("UFTP server "+i.getCommandHost()+":"+i.getCommandPort()+" is DOWN: "
						+i.getConnectionStatusMessage());
				}
			}
		}
		if(instances.size()>1) {
			statusMessage = "OK ["+avail+" of "+instances.size()+" UFTPD servers available]";
		}else {
			statusMessage = instances.get(0).getConnectionStatusMessage();	
		}
	}

	int index = 0;
	
	public synchronized UFTPDInstance getUFTPDInstance() throws IOException {
		int c=0;
		while(c<=instances.size()) {
			UFTPDInstance i = instances.get(index);
			index++;
			if(index==instances.size())index=0;
			if(i.isUFTPAvailable()) {
				if(log.isDebugEnabled()) {
					log.debug("Using "+index+": "+i);
				}
				return i;
			}
			c++;
		}
		throw new IOException("None of the configured UFTPD servers is available!");
	}
}
