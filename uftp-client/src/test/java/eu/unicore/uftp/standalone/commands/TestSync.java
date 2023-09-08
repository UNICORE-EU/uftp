package eu.unicore.uftp.standalone.commands;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import eu.unicore.services.rest.client.UsernamePassword;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.standalone.BaseServiceTest;
import eu.unicore.uftp.standalone.ClientDispatcher;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.ConnectionInfoManager;

public class TestSync extends BaseServiceTest {
	
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
    	String[] args = new String[]{ new USYNC().getName(), "-h" };
    	ClientDispatcher._main(args);
    }

    @Test
    public void testSyncLocalMaster() throws Exception {
    	File src = new File("./pom.xml");
    	File target = new File(testsDir, "pom.xml");
    	FileUtils.writeStringToFile(target, "test123", "UTF-8");
    	String[] args = new String[]{ new USYNC().getName(), "-u", "demouser:test123",
    			src.getAbsolutePath(), getAuthURL(target.getAbsolutePath())
    	};
    	assertEquals(0, ClientDispatcher._main(args));
    	Thread.sleep(1000);
    	assertEquals(Utils.md5(src), Utils.md5(target));
    }
    
    @Test
    public void testSyncRemoteMaster() throws Exception {
    	File src = new File("./pom.xml");
    	File target = new File(testsDir, "pom.xml");
    	FileUtils.writeStringToFile(target, "test123", "UTF-8");
    	String[] args = new String[]{ new USYNC().getName(), "-u", "demouser:test123",
    			getAuthURL(src.getAbsolutePath()), target.getAbsolutePath()
    	};
    	assertEquals(0, ClientDispatcher._main(args));
    	Thread.sleep(1000);
    	assertEquals(Utils.md5(src), Utils.md5(target));
    }

}
