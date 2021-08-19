package eu.unicore.uftp.authserver.authenticate.sshkey;

import java.io.File;
import java.security.PublicKey;
import java.util.Date;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import net.schmizz.sshj.userauth.password.PasswordFinder;
import net.schmizz.sshj.userauth.password.Resource;

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

	@Ignore @Test
	public void testDSAKey() throws Exception {
		String orig = new Date().toString();
		File key = new File("src/test/resources/ssh/id_dsa");
		String pubkey = FileUtils.readFileToString(new File("src/test/resources/ssh/id_dsa.pub"), "UTF-8");
		SSHKey sshAuth = SSHUtils.createAuthData(key, "test123".toCharArray(), orig);
		Assert.assertTrue(SSHUtils.validateAuthData(sshAuth,pubkey));
	}

	@Test
	public void testED25519Key() throws Exception {
		String orig = new Date().toString();
		File key = new File("src/test/resources/ssh/id_ed25519");
		String pubkey = FileUtils.readFileToString(new File("src/test/resources/ssh/id_ed25519.pub"), "UTF-8");
		SSHKey sshAuth = SSHUtils.createAuthData(key, "test123".toCharArray(), orig);
		Assert.assertTrue(SSHUtils.validateAuthData(sshAuth,pubkey));
	}
	
	@Test
	public void testECDSAKey() throws Exception {
		String orig = new Date().toString();
		File key = new File("src/test/resources/ssh/id_ecdsa");
		String pubkey = FileUtils.readFileToString(new File("src/test/resources/ssh/id_ecdsa.pub"), "UTF-8");
		SSHKey sshAuth = SSHUtils.createAuthData(key, "test123".toCharArray(), orig);
		Assert.assertTrue(SSHUtils.validateAuthData(sshAuth,pubkey));
	}

	@Test
	public void testPuttyKey() throws Exception {
		String orig = new Date().toString();
		File key = new File("src/test/resources/ssh/putty-key.ppk");
		String pubkey = FileUtils.readFileToString(new File("src/test/resources/ssh/putty-key.pub"), "UTF-8");
		SSHKey sshAuth = SSHUtils.createAuthData(key, (char[])null, orig);
		Assert.assertTrue(SSHUtils.validateAuthData(sshAuth,pubkey));
	}
	
	@Test
	public void testAuthKeyParse() throws Exception {
		String data = "from=\"w.x.y.z\" ssh-ed25519 "
				+ "AAAAC3NzaC1lZDI1NTE5AAAAIDSrmN76orlz7Zf0oSXlum5uYfpMEjKpAxp2W0OQWaLl"
				+ " comment@somewhere";
		Assert.assertNotNull(SSHUtils.readPubkey(data));
	}
	
	@Test
	public void testNoPassKey() throws Exception {
		String orig = new Date().toString();
		File key = new File("src/test/resources/ssh/id_nopass");
		String pubkey = FileUtils.readFileToString(new File("src/test/resources/ssh/id_nopass.pub"), "UTF-8");
		SSHKey sshAuth = SSHUtils.createAuthData(key, (char[])null, orig);
		Assert.assertTrue(SSHUtils.validateAuthData(sshAuth,pubkey));
	}
	
	@Test
	public void testNoPassKeyWithoutQuery() throws Exception {
		PasswordFinder pf = new PasswordFinder() {
			@Override
			public boolean shouldRetry(Resource<?> resource) {
				return false;
			}
			@Override
			public char[] reqPassword(Resource<?> resource) {
				throw new IllegalStateException("Should not query for password-less key");
			}
		};
		
		String orig = new Date().toString();
		File key = new File("src/test/resources/ssh/id_nopass");
		String pubkey = FileUtils.readFileToString(new File("src/test/resources/ssh/id_nopass.pub"), "UTF-8");
		SSHKey sshAuth = SSHUtils.createAuthData(key, pf, orig);
		Assert.assertTrue(SSHUtils.validateAuthData(sshAuth,pubkey));
	}
}
