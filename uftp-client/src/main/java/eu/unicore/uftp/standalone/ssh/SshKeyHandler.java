package eu.unicore.uftp.standalone.ssh;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.StringTokenizer;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;

import eu.emi.security.authn.x509.helpers.PasswordSupplier;
import eu.unicore.uftp.authserver.authenticate.sshkey.SSHKey;
import eu.unicore.uftp.authserver.authenticate.sshkey.SSHUtils;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.standalone.util.ConsoleUtils;
import eu.unicore.util.Log;

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
	
	private boolean forceIdentity;
	
	public SshKeyHandler(File privateKey, String userName, String token) {
		this.privateKey = privateKey;
		this.userName = userName;
		this.token = token;
	}
	
	public void forceIdentity() {
		this.forceIdentity = true;
	}
	
	@Override
	public char[] getPassword() {
		return ConsoleUtils.readPassword("Enter passphrase for '"+privateKey.getAbsolutePath()+"': ").toCharArray();
	}
	
	public SSHKey getAuthData() throws Exception {
		if(SSHAgent.isAgentAvailable()){
			try{
				return useAgent();
			}catch(Exception ex){
				System.err.println(Utils.createFaultMessage("WARNING: SSH agent is available, but there was an error when using it",ex));
				return create();
			}
		}
		else{
			return create();
		}
	}
	
	protected SSHKey create() throws GeneralSecurityException, IOException {
		if(privateKey == null || !privateKey.exists()){
	                 throw new IOException("No RSA or DSA key found!");
		}
		SSHKey sshauth = SSHUtils.createAuthData(privateKey, this.getPassword(), token);
		sshauth.username = userName;
		return sshauth;
	}

	protected SSHKey useAgent() throws Exception {
		SSHAgent agent = new SSHAgent();
		if(forceIdentity) {
			try {
				String pk = FileUtils.readFileToString(new File(privateKey.getPath()+".pub"), "UTF-8");
				StringTokenizer st = new StringTokenizer(pk);
				String comment = null;
				st.nextToken(); // format
				st.nextToken();
				comment = st.nextToken();
				agent.selectIdentity(comment);
			}catch(Exception ex) {
				System.out.println(Log.createFaultMessage("Warning: ", ex));
			}
		}
		byte[] signature = agent.sign(token);
		SSHKey authData = new SSHKey();
		authData.signature = new String(Base64.encodeBase64(signature));
		authData.token = new String(Base64.encodeBase64(token.getBytes()));
		authData.username= userName;
		return authData;
	}

}
