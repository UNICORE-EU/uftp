package eu.unicore.uftp.datashare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import eu.unicore.persist.Persist;
import eu.unicore.persist.PersistenceProperties;
import eu.unicore.persist.impl.H2Persist;
import eu.unicore.uftp.datashare.db.ACLStorage;
import eu.unicore.uftp.datashare.db.ShareDAO;


public class TestDao {

	@Test
	public void testDao() throws Exception {
		PersistenceProperties cf=new PersistenceProperties();
		cf.setDatabaseDirectory("./target/test_data");

		@SuppressWarnings({"rawtypes","unchecked"})
		Persist<ShareDAO>p = (Persist<ShareDAO>)new H2Persist(ShareDAO.class, null);
		p.setConfigSource(cf);
		p.init();
		p.removeAll();

		ShareDAO d1 = new ShareDAO();
		d1.setPath("/data/myfile");
		d1.setUid("nobody");
		d1.setGid("users");
		d1.setOwnerID("me@foo.com");
		d1.setTargetID("you@baz.com");
		d1.setAccess(AccessType.READ);
		p.write(d1);

		String target = "verytrusted@x.com";
		ShareDAO d2 = new ShareDAO();
		d2.setPath("/home/my");
		d2.setUid("me");
		d2.setGid("users");
		d2.setOwnerID("me@foo.com");
		d2.setTargetID(target);
		d2.setAccess(AccessType.WRITE);
		d2.setDirectory(true);
		p.write(d2);

		Collection<String> ids = p.getIDs("path", "/data/myfile");
		assertEquals(1,ids.size());

		String id = ids.iterator().next();
		ShareDAO d3 = p.read(id);
		System.out.println("Select by path:");
		System.out.println(d3);
		assertEquals("READ",d3.getAccess());

		ids = p.getIDs("target", target);
		assertEquals(1,ids.size());
		id = ids.iterator().next();
		ShareDAO d4 = p.read(id);
		System.out.println("Select by target:");
		System.out.println(d4);
		assertEquals("WRITE",d4.getAccess());
		assertEquals(target,d4.getTargetID());
		assertTrue(d4.isDirectory());

		p.shutdown();
	}
	
	@Test
	public void testCompare() {
		ShareDAO d1 = new ShareDAO();
		d1.setPath("/data");
		ShareDAO d2 = new ShareDAO();
		d2.setPath("/data/myfile");
		List<ShareDAO> l = new ArrayList<>();
		l.add(d1);
		l.add(d2);
		Collections.sort(l, ACLStorage.MostSpecificPath);
		assertEquals(d2.getPath(), l.get(0).getPath());
	}

}
