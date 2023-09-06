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

public class TestULS extends BaseServiceTest {
	
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

	protected void ls(String... targets) throws Exception {
		ULS uls = new ULS();
		uls.fileArgs = targets;
		uls.run(client);
	}

    @Test
    public void testSingle() throws Exception {
    	String file = new File("./pom.xml").getAbsolutePath();
    	ls(getAuthURL(file));
    }

    @Test
    public void testMultiple() throws Exception {
    	String file1 = new File(".").getAbsolutePath();
    	String file2 = testsDir.getAbsolutePath();
    	ls(getAuthURL(file1), getAuthURL(file2));
    }

}
