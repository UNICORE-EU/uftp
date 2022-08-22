package eu.unicore.uftp.standalone.ssh;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

import org.apache.commons.codec.binary.Base64;

import eu.emi.security.authn.x509.helpers.PasswordSupplier;
import eu.unicore.services.rest.security.sshkey.SSHKeyUC;
import eu.unicore.services.rest.security.sshkey.SSHUtils;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.standalone.util.ConsoleUtils;

/**
 * create SSHKey auth info using SSH agent, if possible. 
 * If not, query the key password interactively and 
 * do the signing
 *
 * @author schuller
 */
public class SshKeyHandler implements PasswordSupplier {

	private final File privateKey;

	private final String userName;

	private final String token;

	private boolean verbose = false;

	// will use agent with a user-selected identity
	private boolean selectIdentity = false;

	public SshKeyHandler(File privateKey, String userName, String token) {
		this.privateKey = privateKey;
		this.userName = userName;
		this.token = token;
	}
	
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	@Override
	public char[] getPassword() {
		return ConsoleUtils.readPassword("Enter passphrase for '"+privateKey.getAbsolutePath()+"': ").toCharArray();
	}
	
	public SSHKeyUC getAuthData() throws Exception {
		SSHKeyUC result = null;
		if(SSHAgent.isAgentAvailable()){
			try{
				result = useAgent();
			}catch(Exception ex){
				System.err.println(Utils.createFaultMessage("WARNING: SSH agent is available, but there was an error when using it",ex));
			}
		}
		if(result==null)result = create();
		
		return result;
	}
	
	public void selectIdentity() {
		this.selectIdentity = true;
	}
	
	protected SSHKeyUC create() throws GeneralSecurityException, IOException {
		if(privateKey == null || !privateKey.exists()){
	                 throw new IOException("No private key found!");
		}
		final PasswordSupplier pf = new PasswordSupplier() {
			@Override
			public char[] getPassword() {
				return getPassword();
			}
		};
		SSHKeyUC sshauth = SSHUtils.createAuthData(privateKey, pf , token);
		sshauth.username = userName;
		return sshauth;
	}

	protected SSHKeyUC useAgent() throws Exception {
		SSHAgent agent = new SSHAgent();
		agent.setVerbose(verbose);
		if(selectIdentity) {
			try{
				agent.selectIdentity(privateKey.getAbsolutePath());
			}catch(Exception ex) {
				return null;
			}
		}
		
		byte[] signature = agent.sign(token);
		SSHKeyUC authData = new SSHKeyUC();
		authData.signature = new String(Base64.encodeBase64(signature));
		authData.token = new String(Base64.encodeBase64(token.getBytes()));
		authData.username= userName;
		return authData;
	}

}
