package eu.unicore.uftp.authserver;

import java.security.SecureRandom;

import javax.net.ssl.SSLSocketFactory;

import eu.emi.security.authn.x509.impl.SocketFactoryCreator2;
import eu.unicore.services.ExternalSystemConnector;
import eu.unicore.services.Kernel;
import eu.unicore.uftp.server.UFTPDInstanceBase;
import eu.unicore.util.httpclient.HostnameMismatchCallbackImpl;
import eu.unicore.util.httpclient.IClientConfiguration;
import eu.unicore.util.httpclient.ServerHostnameCheckingMode;

/**
 * Holds properties and parameters for a single UFTPD server,
 * and is used for communicating with the UFTPD.
 *
 * @author schuller
 */
public class UFTPDInstance extends UFTPDInstanceBase implements ExternalSystemConnector {

	private final String serverName;

	private final Kernel kernel;
	
	public UFTPDInstance(String serverName, Kernel kernel){
		super();
		this.serverName = serverName;
		this.kernel = kernel;
	}

	public String getServerName(){
		return serverName;
	}
		
	public Status getConnectionStatus() {
		checkConnection();
		return isUp ? Status.OK : Status.DOWN;
	}
	
	public String getExternalSystemName() {
		return  "UFTPD server '"+serverName+"'";
	}
	
	private SSLSocketFactory socketfactory = null;
	
	protected synchronized SSLSocketFactory getSSLSocketFactory() {
		if(socketfactory==null) {
			IClientConfiguration cfg = kernel.getClientConfiguration();
			socketfactory = new SocketFactoryCreator2(cfg.getCredential(), cfg.getValidator(), 
					new HostnameMismatchCallbackImpl(ServerHostnameCheckingMode.NONE),
					getRandom(), "TLS").getSocketFactory();
		}
		return socketfactory;
	}
	
	private static SecureRandom random=null;

	private synchronized SecureRandom getRandom(){
		if(random==null){
			random=new SecureRandom();
		}
		return random;
	}
}
