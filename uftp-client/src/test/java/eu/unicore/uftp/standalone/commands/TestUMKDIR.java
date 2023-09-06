package eu.unicore.uftp.standalone.commands;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import eu.unicore.services.rest.client.UsernamePassword;
import eu.unicore.uftp.standalone.BaseServiceTest;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.ConnectionInfoManager;
import eu.unicore.uftp.standalone.UFTPClientFactory;

public class TestUMKDIR extends BaseServiceTest {
	
	ClientFacade client ;
	File testsDir;

	@Before
	public void setup() throws Exception {
		client = new ClientFacade(
				new ConnectionInfoManager(new UsernamePassword("demouser", "test123")),
				new UFTPClientFactory());
		testsDir = new File("target", "testdata");
		FileUtils.deleteQuietly(testsDir);
		testsDir.mkdirs();
	}

	protected void mkdir(String... dir ) throws Exception {
		UMKDIR umkdir = new UMKDIR();
		umkdir.fileArgs = dir;
		umkdir.run(client);
	}

    @Test
    public void test1() throws Exception {
    	String file = new File(testsDir, "newdir1").getAbsolutePath();
    	mkdir(getAuthURL(file));
    }

}
