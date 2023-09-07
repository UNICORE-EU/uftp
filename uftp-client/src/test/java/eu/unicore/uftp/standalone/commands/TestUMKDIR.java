package eu.unicore.uftp.standalone.commands;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import eu.unicore.services.rest.client.UsernamePassword;
import eu.unicore.uftp.standalone.BaseServiceTest;
import eu.unicore.uftp.standalone.ClientDispatcher;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.ConnectionInfoManager;

public class TestUMKDIR extends BaseServiceTest {
	
	ClientFacade client ;
	File testsDir;

	@Before
	public void setup() throws Exception {
		client = new ClientFacade(
				new ConnectionInfoManager(new UsernamePassword("demouser", "test123")));
		testsDir = new File("target", "testdata");
		FileUtils.deleteQuietly(testsDir);
		testsDir.mkdirs();
	}

    @Test
    public void testCmd() throws Exception {
    	String[] args = new String[]{ new UMKDIR().getName(), "-h" };
    	ClientDispatcher._main(args);
    }

    @Test
    public void test1() throws Exception {
    	String file = new File(testsDir, "newdir1").getAbsolutePath();
    	String[] args = new String[]{ new UMKDIR().getName(), 
    			"mkdir", "-u", "demouser:test123",
    			getAuthURL(file)};
    	ClientDispatcher._main(args);
    }

}
