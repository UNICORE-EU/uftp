package eu.unicore.uftp.standalone.commands;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import eu.unicore.services.rest.client.UsernamePassword;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.standalone.BaseServiceTest;
import eu.unicore.uftp.standalone.ClientDispatcher;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.ConnectionInfoManager;

public class TestRCP extends BaseServiceTest {
	
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
    	String[] args = new String[]{ new URCP().getName(), "-h" };
    	ClientDispatcher._main(args);
    }

    @Test
    public void testRCP1() throws Exception {
    	String src = new File("./pom.xml").getAbsolutePath();
    	String target = new File(testsDir, "test.dat").getAbsolutePath();
    	String[] args = new String[]{ new URCP().getName(), "-u", "demouser:test123",
    			getAuthURL(src), getAuthURL(target)
    	};
    	// Java UFTPD does not do rcp...
    	assertEquals(1, ClientDispatcher._main(args));
    }

}
