package eu.unicore.uftp.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.security.auth.x500.X500Principal;

import org.apache.logging.log4j.Logger;

import eu.unicore.security.canl.AuthnAndTrustProperties;
import eu.unicore.security.canl.SSLContextCreator;
import eu.unicore.uftp.dpc.AuthorizationFailureException;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.util.httpclient.ServerHostnameCheckingMode;

/**
 * Deals with reading the SSL config file and setting up the UFTPD command socket.
 *
 * @author schuller
 */
public class SSLHelper {

	private static final Logger logger = Utils.getLogger(Utils.LOG_SERVER, SSLHelper.class);

	private final boolean useSSL;
	
	private ACLHandler acl;

	private AuthnAndTrustProperties securityProperties;
	
	public SSLHelper() throws IOException {
		useSSL = initSSLProperties();
		if (useSSL) {
			acl = new ACLHandler(new File(System.getProperty("uftpd.acl")));
		}
	}
		
	/**
	 * create the UFTPD command socket
	 * 
	 * @param port
	 * @param backlog
	 * @param cmdip
	 * 
	 * @throws IOException
	 */
	public ServerSocket createCommandSocket(int port, int backlog, InetAddress cmdip) throws IOException {
		if (useSSL) {
			return createSecureCommandSocket(port, backlog, cmdip);
		} else {
			logger.info("*****");
			logger.info("*****   WARNING:");
			logger.info("****    Using a plain-text socket for receiving commands.");
			logger.info("****    On production systems you should enable SSL!");
			logger.info("****    Consult the UFTP manual for details.");
			logger.info("*****");
			return new ServerSocket(port, backlog, cmdip);
		}
	}

	/**
	 * if SSL is enabled, check whether the peer is in our ACL
	 * 
	 * @param jobSocket
	 * @throws IOException
	 * @throws AuthorizationFailureException
	 */
	public void checkAccess(Socket jobSocket) throws IOException, AuthorizationFailureException {
		if (useSSL) {
			X500Principal x = (X500Principal) ((SSLSocket) jobSocket).getSession().getPeerPrincipal();
			acl.checkAccess(x.getName());
		}
	}

	private ServerSocket createSecureCommandSocket(int port, int backlog, InetAddress cmdip) throws IOException {
		logger.info("Creating SSL socket for receiving commands.");
		try{
			SSLContext ctx = SSLContextCreator.createSSLContext(securityProperties.getCredential(), 
					securityProperties.getValidator(), 
					"TLS", "UFTPD command socket", logger,
					ServerHostnameCheckingMode.WARN);
			SSLServerSocket s = (SSLServerSocket)ctx.getServerSocketFactory().createServerSocket(port, backlog, cmdip);
			s.setNeedClientAuth(true);
			return s;
		}catch(Exception ex){
			throw new IOException(ex);
		}
	}
	
	private boolean initSSLProperties() throws IOException {
		String file = System.getProperty("uftpd-ssl.conf");
		boolean sslEnabled = true;
		Properties p = null;
		
		if (file != null) {
			File sslProps = new File(file);
			if (!sslProps.exists()) {
				throw new IOException("SSL properties file " + file + " does not exist!");
			} else {
				logger.info("Loading SSL settings from <{}>", file);
			}
			p = new Properties();
			try(InputStream in = new FileInputStream(sslProps)){
				p.load(in);
			}
			if(p.getProperty("credential.path")!=null){
				logger.info("Enabling SSL, using credential = {}", p.getProperty("credential.path"));
			}
			else{
				logger.warn("SSL properties file does not contain credential / keystore definition.");
				sslEnabled = false;
			}
		}
		else{
			logger.info("No SSL properties file defined.");
			sslEnabled = false;
		}
		if(sslEnabled){
			securityProperties = new AuthnAndTrustProperties(p);
		}
		return sslEnabled;
	}

}
