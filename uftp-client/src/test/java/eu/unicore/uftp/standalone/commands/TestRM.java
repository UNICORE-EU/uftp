package eu.unicore.uftp.standalone.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import eu.unicore.services.rest.client.UsernamePassword;
import eu.unicore.uftp.standalone.BaseServiceTest;
import eu.unicore.uftp.standalone.ClientDispatcher;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.ConnectionInfoManager;

public class TestRM extends BaseServiceTest {
	
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
    	String[] args = new String[]{ new URM().getName(), "-h" };
    	ClientDispatcher._main(args);
    }

    @Test
    public void testRM() throws Exception {
    	File f = new File(testsDir, "to_remove");
    	FileUtils.writeStringToFile(f, "test123", "UTF-8");
    	String[] args = new String[]{ new URM().getName(), "-u", "demouser:test123",
    			"--quiet",
    			getAuthURL(f.getAbsolutePath())
    	};
    	assertEquals(0, ClientDispatcher._main(args));
    	assertFalse(f.exists());
    }

}
