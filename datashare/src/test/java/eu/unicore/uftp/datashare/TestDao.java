package eu.unicore.uftp.datashare;

import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;

import de.fzj.unicore.persist.Persist;
import de.fzj.unicore.persist.PersistenceProperties;
import de.fzj.unicore.persist.impl.H2Persist;
import eu.unicore.uftp.datashare.db.ShareDAO;


public class TestDao {

	@Test
	public void testDao() throws Exception {
		PersistenceProperties cf=new PersistenceProperties();
		cf.setDatabaseDirectory("./target/test_data");

		@SuppressWarnings("unchecked")
		Persist<ShareDAO>p=(Persist<ShareDAO>)(H2Persist.class.getConstructor().newInstance());
		p.setDaoClass(ShareDAO.class);
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
		
		p.flush();
		
		Collection<String> ids = p.getIDs("path", "/data/myfile");
		Assert.assertEquals(1,ids.size());
		
		String id = ids.iterator().next();
		ShareDAO d3 = p.read(id);
		System.out.println("Select by path:");
		System.out.println(d3);
		Assert.assertEquals("READ",d3.getAccess());
		
		ids = p.getIDs("target", target);
		Assert.assertEquals(1,ids.size());
		id = ids.iterator().next();
		ShareDAO d4 = p.read(id);
		System.out.println("Select by target:");
		System.out.println(d4);
		Assert.assertEquals("WRITE",d4.getAccess());
		Assert.assertEquals(target,d4.getTargetID());
		Assert.assertTrue(d4.isDirectory());
		
		p.shutdown();
	}
}
