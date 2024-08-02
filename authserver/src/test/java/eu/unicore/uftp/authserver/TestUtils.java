package eu.unicore.uftp.authserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import eu.unicore.uftp.authserver.share.IdentityExtractor.EmailExtractor;
import eu.unicore.uftp.authserver.share.ShareServiceBase;
import eu.unicore.uftp.datashare.AccessType;
import eu.unicore.util.Pair;

public class TestUtils {

	@Test
	public void testRateParser(){
		long rate = UserAttributes.parseRate("100M");
		assertEquals(100*1024*1024, rate);
		rate = UserAttributes.parseRate("100 M");
		assertEquals(100*1024*1024, rate);
		rate = UserAttributes.parseRate("100 m");
		assertEquals(100*1024*1024, rate);
		rate = UserAttributes.parseRate("100 G");
		assertEquals(100L*1024*1024*1024, rate);
		rate = UserAttributes.parseRate("100 gig/sec");
		assertEquals(100L*1024*1024*1024, rate);
		rate = UserAttributes.parseRate("100 k");
		assertEquals(100*1024, rate);
		try{
			rate = UserAttributes.parseRate("100 Tera");
			fail();
		}catch(Exception ex){
			assertTrue(ex instanceof IllegalArgumentException);
		}
	}
	
	@Test
	public void testEmailExtract() throws Exception {
		String[] in = {"f.oo@bar.org",
				"UID=f.oo@bar.org",
				"UID=f.oo@bar.org,C=EU",
				"OU=x,UID=f.oo@bar.org,C=EU",
		};
		String email = "f.oo@bar.org";
		for(String i: in){
			String o = EmailExtractor.extractEmail(i);
			assertEquals(email, o, "failed for input <"+i+">");
		}
		
		String x = "OU=x,CN=foo,C=EU";
		assertEquals(x, EmailExtractor.extractEmail(x), "failed for input <"+x+">");
		
	}

	@Test
	public void testAccessLevels() {
		assertTrue(ShareServiceBase.checkAccess(AccessType.READ, AccessType.WRITE));
		assertFalse(ShareServiceBase.checkAccess(AccessType.WRITE, AccessType.READ));
	}

	@Test
	public void testServerNameHandling() {
		var sn = new String[] { "TEST-1", "FOO", "FOO-TEST-2"};
		var names = new String[] { "TEST", "FOO", "FOO-TEST" };
		var indices = new Integer[] { 1, null, 2 };

		for(int x=0; x<sn.length; x++) {
			Pair<String, Integer> sn1 = AuthServiceImpl.getServerSpec(sn[x]);
			assertEquals(names[x], sn1.getM1());
			assertEquals(indices[x], sn1.getM2());
		}
	}
	
	@Test
	public void testAuthServerProperties() {
		Properties p = new Properties();
		p.put(AuthServiceProperties.PREFIX+AuthServiceProperties.PROP_SERVERS, "TEST");
		AuthServiceProperties props = new AuthServiceProperties(p);
		Properties p2 = new Properties();
		p2.put(AuthServiceProperties.PREFIX+AuthServiceProperties.PROP_SERVERS, "TEST");
		p2.put("some.other.property", "FOO");
		assertFalse(props.hasChanges(p2));
		p2.put(AuthServiceProperties.PREFIX+AuthServiceProperties.PROP_SERVERS, "TEST ANOTHER");
		assertTrue(props.hasChanges(p2));
	}
}
