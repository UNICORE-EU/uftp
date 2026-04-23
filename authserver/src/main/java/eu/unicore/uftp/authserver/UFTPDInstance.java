package eu.unicore.uftp.authserver;

import java.security.SecureRandom;

import javax.net.ssl.SSLSocketFactory;

import eu.emi.security.authn.x509.impl.SocketFactoryCreator2;
import eu.unicore.services.ExternalSystemConnector;
import eu.unicore.services.Kernel;
import eu.unicore.services.utils.ExternalConnectorHelper;
import eu.unicore.uftp.server.UFTPDInstanceBase;
import eu.unicore.uftp.server.requests.UFTPPingRequest;
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

	private final Kernel kernel;

	private final ExternalConnectorHelper ech = new ExternalConnectorHelper();

	public UFTPDInstance(String serverName, Kernel kernel){
		super();
		this.kernel = kernel;
		ech.setCheckService(kernel.getExecutorService());
		ech.setCheckSupplier(()-> checkConnection());
		ech.setExternalSystemName("UFTPD server '"+serverName+"'");
	}

	@Override
	public Status getConnectionStatus() {
		return ech.getConnectionStatus();
	}

	@Override
	public String getConnectionStatusMessage() {
		return  ech.getConnectionStatusMessage();
	}

	@Override
	public String getExternalSystemName() {
		return  ech.getExternalSystemName();
	}

	@Override
	public boolean isOK() {
		return ech.isOK();
	}

	@Override
	protected void notOK(String errorMsg) {
		ech.notOK(errorMsg);
	}

	@Override
	protected void setOK() {
		ech.setOK();
	}

	// run a PING to make sure that service is available
	public boolean isServiceAvailable() {
		try {
			sendRequest(new UFTPPingRequest());
			return true;
		}catch(Exception e){
			return false;
		}
	}

	private SSLSocketFactory socketfactory = null;

	@Override
	protected synchronized SSLSocketFactory getSSLSocketFactory() {
		if(socketfactory==null) {
			IClientConfiguration cfg = kernel.getClientConfiguration();
			socketfactory = new SocketFactoryCreator2(cfg.getCredential(), cfg.getValidator(), 
					new HostnameMismatchCallbackImpl(ServerHostnameCheckingMode.NONE),
					getRandom(), "TLS").getSocketFactory();
		}
		return socketfactory;
	}

	private static SecureRandom random;

	private synchronized SecureRandom getRandom() {
		if(random==null)random = new SecureRandom();
		return random;
	}
}
