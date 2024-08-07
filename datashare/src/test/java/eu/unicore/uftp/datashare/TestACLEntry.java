package eu.unicore.uftp.datashare;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import eu.unicore.uftp.datashare.db.ACLStorage;

public class TestACLEntry {

	@Test
	public void test1() throws Exception {
		ACLStorage s = 	TestACLStorage.getStore();
		Owner owner = new Owner("Me", "nobody", "nobody");
		SharingUser user = new SharingUser("Demo User");
		s.grant(AccessType.READ, "/tmp", user, owner, 0, false);
		user = new SharingUser("Trusted");
		s.grant(AccessType.WRITE, "/tmp", user, owner, 0, false);
		user = new SharingUser("Senior");
		s.grant(AccessType.MODIFY, "/tmp", user, owner, 0, false);
		ACLEntry e = ACLEntry.read("/tmp", owner, s);
		assertEquals(3, e.getAllReadable().size());
		assertEquals(1,e.list(AccessType.MODIFY).size());
		assertEquals(2,e.list(AccessType.WRITE).size());
		assertEquals(3,e.list(AccessType.READ).size());
		
		e.setPermissions(user, AccessType.WRITE);
		e.write();
		
		e = ACLEntry.read("/tmp", owner, s);
		assertEquals(3,e.list(AccessType.READ).size());
		assertEquals(2,e.list(AccessType.WRITE).size());
		assertEquals(0,e.list(AccessType.MODIFY).size());
		
		e.setPermissions(user, AccessType.NONE);
		e.write();
		System.out.println(e.list());
		e = ACLEntry.read("/tmp", owner, s);
		System.out.println(e.list());
		assertEquals(2,e.list(AccessType.READ).size());
		assertEquals(1,e.list(AccessType.WRITE).size());
		assertEquals(0,e.list(AccessType.MODIFY).size());
		
	}
	
}
