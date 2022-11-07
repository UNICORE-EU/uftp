package eu.unicore.uftp.standalone.ssh;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Test;

import eu.unicore.services.rest.security.sshkey.SSHKeyUC;
import eu.unicore.services.rest.security.sshkey.SSHUtils;

/**
 * functional test that tests the agent support using real keys
 */
public class TestAgent {
	
	@Test
	@Ignore
	public void testSigningUsingAgent() throws Exception {
		String token = "test123";
		String user = "demouser";
		File privKey = new File(System.getProperty("user.home")+"/.ssh/id_rsa");
		SshKeyHandler handler = new SshKeyHandler(privKey, user, token);
		handler.selectIdentity();
		SSHKeyUC authData = (SSHKeyUC)handler.getAuthData();
		String pubKey = FileUtils.readFileToString(new File(System.getProperty("user.home")+"/.ssh/id_rsa.pub"), "UTF-8");
		boolean success = SSHUtils.validateAuthData(authData, pubKey);
		assertTrue("Signature validation failed!",success);
	}

	@Test
	@Ignore
	public void testSigningUsingAgentEd25519() throws Exception {
		String token = "test123";
		String user = "demouser";
		File privKey = new File(System.getProperty("user.home")+"/.ssh/id_ed25519");
		SshKeyHandler handler = new SshKeyHandler(privKey, user, token);
		handler.setVerbose(true);
		handler.selectIdentity();
		SSHKeyUC authData = (SSHKeyUC)handler.getAuthData();
		String pubKey = FileUtils.readFileToString(new File(System.getProperty("user.home")+"/.ssh/id_ed25519.pub"), "UTF-8");
		boolean success = SSHUtils.validateAuthData(authData, pubKey);
		assertTrue("Signature validation failed!",success);
	}

}
