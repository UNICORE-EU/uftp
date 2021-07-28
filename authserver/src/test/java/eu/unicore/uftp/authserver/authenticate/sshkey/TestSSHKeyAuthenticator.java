package eu.unicore.uftp.authserver.authenticate.sshkey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Map;

import org.junit.Test;

import eu.unicore.uftp.authserver.authenticate.SSHKeyAuthenticator;
import eu.unicore.uftp.authserver.authenticate.SSHKeyAuthenticator.AttributeHolders;
import eu.unicore.uftp.authserver.authenticate.SSHKeyAuthenticator.AttributesHolder;

public class TestSSHKeyAuthenticator {

	@Test
	public void test1() throws Exception {
		SSHKeyAuthenticator auth = new SSHKeyAuthenticator();
		auth.setFile("src/test/resources/ssh/ssh-users.txt");
		Map<String,AttributeHolders> db = auth.getMappings();
		assertEquals(2, db.size());
		Collection<AttributesHolder> m1 = db.get("demouser").get();
		assertEquals(2, m1.size());
		Collection<AttributesHolder> m2 = db.get("otheruser").get();
		assertEquals(1, m2.size());
		AttributesHolder a2 = m2.iterator().next();
		assertTrue("otheruser".equals(a2.user));
		assertTrue(a2.dn.contains("CN=Other User"));
		assertTrue(a2.sshkey.contains("key data"));
		auth.removeFileEntriesFromDB();
		m2 = db.get("otheruser").get();
		assertEquals(0, m2.size());
	}
	
}
