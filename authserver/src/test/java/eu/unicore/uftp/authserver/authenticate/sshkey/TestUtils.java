package eu.unicore.uftp.authserver.authenticate.sshkey;

import java.io.File;
import java.security.PublicKey;
import java.util.Date;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

public class TestUtils {

	@Test
	public void testHandlePublicKeys() throws Exception {
		String[] pub = new String[] {
			"src/test/resources/ssh/id_rsa.pub",
			"src/test/resources/ssh/id_dsa.pub",
			"src/test/resources/ssh/id_ed25519.pub",
		};
		for(String p: pub) {
			File key = new File(p);
			PublicKey pubKey = SSHUtils.readPubkey(key);
			Assert.assertNotNull(pubKey);
		}
	}
	
	@Test
	public void testRSAKey() throws Exception {
		String orig = new Date().toString();
		File key = new File("src/test/resources/ssh/id_rsa");
		String pubkey = FileUtils.readFileToString(new File("src/test/resources/ssh/id_rsa.pub"), "UTF-8");
		SSHKey sshAuth = SSHUtils.createAuthData(key, "test123".toCharArray(), orig);
		Assert.assertTrue(SSHUtils.validateAuthData(sshAuth,pubkey));
	}
	
	@Test
	public void testDSAKey() throws Exception {
		String orig = new Date().toString();
		File key = new File("src/test/resources/ssh/id_dsa");
		String pubkey = FileUtils.readFileToString(new File("src/test/resources/ssh/id_dsa.pub"), "UTF-8");
		SSHKey sshAuth = SSHUtils.createAuthData(key, "test123".toCharArray(), orig);
		Assert.assertTrue(SSHUtils.validateAuthData(sshAuth,pubkey));
	}

	@Test
	public void testECDSAKey() throws Exception {
		String orig = new Date().toString();
		File key = new File("src/test/resources/ssh/id_ed25519");
		String pubkey = FileUtils.readFileToString(new File("src/test/resources/ssh/id_ed25519.pub"), "UTF-8");
		SSHKey sshAuth = SSHUtils.createAuthData(key, "test123".toCharArray(), orig);
		Assert.assertTrue(SSHUtils.validateAuthData(sshAuth,pubkey));
	}
}
