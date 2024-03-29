package eu.unicore.uftp.datashare;

import java.util.Collection;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

import eu.unicore.uftp.datashare.db.ACLStorage;
import eu.unicore.uftp.datashare.db.ShareDAO;

public class TestACLStorage {
	
	@Test
	public void test1() throws Exception {
		ACLStorage s = getStore();
		fillTestContent(s);
		
		Collection<ShareDAO> grants = s.readAll("/tmp/");
		Assert.assertEquals(1, grants.size());
		ShareDAO share = grants.iterator().next();
		Assert.assertEquals("READ", share.getAccess());
		Assert.assertTrue(share.isDirectory());
		
		grants = s.readAll("/tmp/somefile");
		Assert.assertEquals(1, grants.size());
		Assert.assertEquals("READ", grants.iterator().next().getAccess());

		grants = s.readAll("/tmp/somedir/somefile");
		Assert.assertEquals(1, grants.size());
		Assert.assertEquals("READ", grants.iterator().next().getAccess());
		
		grants = s.readAll("/");
		Assert.assertEquals(0, grants.size());
		
	}
	
	@Test
	public void testExpiry() throws Exception {
		ACLStorage s = getStore();
		fillTestContent2(s, 1);
		Collection<ShareDAO> grants = s.readAll("/tmp/file.txt");
		Assert.assertEquals(1, grants.size());
		Thread.sleep(2000);		
		grants = s.readAll("/tmp/file.txt");
		Assert.assertEquals(0, grants.size());
	}
	
	public static ACLStorage getStore() throws Exception {
		Properties p = new Properties();
		p.put("persistence.directory", "./target/acldata");
		ACLStorage s = new ACLStorage("TEST", p);
		s.deleteAllData();
		return s;
	}
	
	
	private void fillTestContent(ACLStorage s) throws Exception {
		SharingUser user = new SharingUser("Demo User");
		Owner owner = new Owner("Me", "nobody", "nobody");
		s.grant(AccessType.READ, "/tmp/", user, owner, 0, false);
	}
	
	private void fillTestContent2(ACLStorage s, long lifetime) throws Exception {
		SharingUser user = new SharingUser("Demo User");
		Owner owner = new Owner("Me", "nobody", "nobody");
		s.grant(AccessType.READ, "/tmp/file.txt", user, owner,  lifetime + System.currentTimeMillis()/1000, false);
	}
}
