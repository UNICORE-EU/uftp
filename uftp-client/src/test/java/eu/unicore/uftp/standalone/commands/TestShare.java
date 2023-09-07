package eu.unicore.uftp.standalone.commands;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import eu.unicore.uftp.standalone.BaseServiceTest;
import eu.unicore.uftp.standalone.ClientDispatcher;

public class TestShare extends BaseServiceTest {
	
	static File testsDir;
	
	@Before
	public void setup() throws Exception {
		testsDir = new File("target", "testdata");
		FileUtils.deleteQuietly(testsDir);
		testsDir.mkdirs();
	}

	@Test
    public void testCmd() throws Exception {
    	String[] args = new String[]{ new Share().getName(), "-h" };
    	ClientDispatcher._main(args);
    }

    @Test
    public void testList() throws Exception {
    	String[] args = new String[]{ new Share().getName(),
    			"-u", "demouser:test123", "-v", "-l",
    			"--server", getShareURL()
    	};
    	assertEquals(0, ClientDispatcher._main(args));
    }

    @Test
    public void testShare() throws Exception {
    	File f = new File(testsDir, "file.dat");
    	FileUtils.writeStringToFile(f, "test123", "UTF-8");
    	
    	String[] args = new String[]{ new Share().getName(),
    			"-u", "demouser:test123", "-v", 
    			"--server", getShareURL(),
    			"--one-time", "--lifetime", "120",
    			f.getAbsolutePath()
    	};
    	assertEquals(0, ClientDispatcher._main(args));
    	
    }

}
