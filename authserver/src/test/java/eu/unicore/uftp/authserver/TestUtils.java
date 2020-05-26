package eu.unicore.uftp.authserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import eu.unicore.uftp.authserver.authenticate.UserAttributes;
import eu.unicore.uftp.authserver.share.IdentityExtractor.EmailExtractor;

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
			assertEquals("failed for input <"+i+">",email, o);
		}
		
		String x = "OU=x,CN=foo,C=EU";
		assertEquals("failed for input <"+x+">", x, EmailExtractor.extractEmail(x));
		
	}

}
