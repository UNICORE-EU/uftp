package eu.unicore.uftp.datashare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import eu.unicore.uftp.datashare.db.ACLStorage;
import eu.unicore.uftp.datashare.db.ShareDAO;

public class TestACLStorage {

	@Test
	public void testBasic() throws Exception {
		ACLStorage s = getStore();
		assertNotNull(s.getPersist());
		fillTestContent(s);

		Collection<ShareDAO> grants = s.readAll("/tmp/");
		assertEquals(1, grants.size());
		ShareDAO share = grants.iterator().next();
		assertEquals("READ", share.getAccess());
		assertEquals("nobody", share.getUid());
		assertEquals("nogroup", share.getGid());
		assertEquals(0, share.getExpires());
		assertTrue(share.isDirectory());
		assertFalse(share.isOneTime());

		grants = s.readAll("/tmp/somefile");
		assertEquals(1, grants.size());
		assertEquals("READ", grants.iterator().next().getAccess());

		grants = s.readAll("/tmp/somedir/somefile");
		assertEquals(1, grants.size());
		assertEquals("READ", grants.iterator().next().getAccess());

		grants = s.readAll("/");
		assertEquals(0, grants.size());

		Owner owner = new Owner("Me", "nobody", "nobody");
		grants = s.readAll(owner);
		assertEquals(1, grants.size());

		owner = new Owner("someone else", "nobody", "nobody");
		grants = s.readAll(owner);
		assertEquals(0, grants.size());

		SharingUser user = new SharingUser("Demo User");
		grants = s.readAll(user);
		assertEquals(1, grants.size());

		user = new SharingUser("Some other user");
		grants = s.readAll(user);
		assertEquals(0, grants.size());
	}

	@Test
	public void testExpiry() throws Exception {
		ACLStorage s = getStore();
		fillTestContent2(s, 1);
		Collection<ShareDAO> grants = s.readAll("/tmp/file.txt");
		assertEquals(1, grants.size());
		assertTrue(grants.iterator().next().getExpires()>0);
		Thread.sleep(2000);		
		grants = s.readAll("/tmp/file.txt");
		assertEquals(0, grants.size());
	}
	
	@Test
	public void testDelete() throws Exception {
		ACLStorage s = getStore();
		fillTestContent2(s, 120);
		Collection<ShareDAO> grants = s.readAll("/tmp/file.txt");
		assertEquals(1, grants.size());
		SharingUser user = new SharingUser("Demo User");
		Owner owner = new Owner("Me", "nobody", "nobody");
		s.deleteAccess("/tmp/file.txt", user,owner);
		grants = s.readAll("/tmp/file.txt");
		assertEquals(0, grants.size());
	}

	@Test
	public void testAccessCount() throws Exception {
		ACLStorage s = getStore();
		String uid = fillTestContent2(s, 120);
		ShareDAO g = s.read(uid);
		assertEquals(0, g.getAccessCount());
		s.incrementAccessCount(uid);
		g = s.read(uid);
		assertEquals(1, g.getAccessCount());
	}

	public static ACLStorage getStore() throws Exception {
		Properties p = new Properties();
		p.put("persistence.directory", "./target/acldata");
		ACLStorage s = new ACLStorage("TEST", p);
		s.deleteAllData();
		return s;
	}

	private String fillTestContent(ACLStorage s) throws Exception {
		SharingUser user = new SharingUser("Demo User");
		Owner owner = new Owner("Me", "nobody", "nogroup");
		return s.grant(AccessType.READ, "/tmp/", user, owner, 0, false);
	}

	private String fillTestContent2(ACLStorage s, long lifetime) throws Exception {
		SharingUser user = new SharingUser("Demo User");
		Owner owner = new Owner("Me", "nobody", "nogroup");
		return s.grant(AccessType.READ, "/tmp/file.txt", user, owner,  lifetime + System.currentTimeMillis()/1000, false);
	}
}
