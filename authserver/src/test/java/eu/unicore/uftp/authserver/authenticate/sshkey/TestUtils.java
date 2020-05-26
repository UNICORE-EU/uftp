package eu.unicore.uftp.authserver.authenticate.sshkey;

import java.io.File;
import java.util.Date;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

public class TestUtils {

	@Test
	public void testRSAKey() throws Exception {
		String orig = new Date().toString();
		File key = new File("src/test/resources/ssh/id_rsa");
		String pubkey = FileUtils.readFileToString(new File("src/test/resources/ssh/id_rsa.pub"));
		SSHKey sshAuth = SSHUtils.createAuthData(key, new Password("test123".toCharArray()),orig);
		Assert.assertTrue(SSHUtils.validateAuthData(sshAuth,pubkey));
	}
	
	@Test
	public void testDSAKey() throws Exception {
		String orig = new Date().toString();
		File key = new File("src/test/resources/ssh/id_dsa");
		String pubkey = FileUtils.readFileToString(new File("src/test/resources/ssh/id_dsa.pub"));
		SSHKey sshAuth = SSHUtils.createAuthData(key, new Password("test123".toCharArray()),orig);
		Assert.assertTrue(SSHUtils.validateAuthData(sshAuth,pubkey));
	}

}
