package eu.unicore.uftp.standalone.commands;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

import eu.unicore.services.rest.client.UsernamePassword;
import eu.unicore.uftp.standalone.BaseServiceTest;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.ConnectionInfoManager;
import eu.unicore.uftp.standalone.UFTPClientFactory;

public class TestChecksum extends BaseServiceTest {
	
	ClientFacade client ;
	
	@Before
	public void setup() throws Exception {
		client = new ClientFacade(
				new ConnectionInfoManager(new UsernamePassword("demouser", "test123")),
				new UFTPClientFactory());
	}

	protected void checksum(String file, String algo) throws Exception {
		Checksum ucheck = new Checksum();
		ucheck.fileArgs = new String[] { file };
		ucheck.hashAlgorithm = algo;
		ucheck.run(client);
	}

    @Test
    public void test1() throws Exception {
    	String src = new File("./pom.xml").getAbsolutePath();
    	checksum(getAuthURL(src), null);
    }
    
    @Test
    public void testSHA512() throws Exception {
    	String src = new File("./pom.xml").getAbsolutePath();
    	checksum(getAuthURL(src), "SHA-512");
    }
    
}
