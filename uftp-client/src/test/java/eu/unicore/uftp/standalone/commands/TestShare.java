package eu.unicore.uftp.standalone.commands;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import eu.unicore.uftp.dpc.Utils;
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
    	File src = new File(testsDir, "file.dat");
    	FileUtils.writeStringToFile(src, "test123", "UTF-8");
    	
    	// share and download
    	String[] args = new String[]{ new Share().getName(),
    			"-u", "demouser:test123", "-v", 
    			"--server", getShareURL(),
    			"--lifetime", "120",
    			src.getAbsolutePath()
    	};
    	assertEquals(0, ClientDispatcher._main(args));
    	args = new String[]{ new Share().getName(),
    			"-u", "demouser:test123", "-l",
    			"--server", getShareURL() };
    	assertEquals(0, ClientDispatcher._main(args));
    	JSONObject lastList = Share._lastList;
    	String uftpURL = lastList.getJSONArray("shares").getJSONObject(0).getString("uftp");
    	
    	File target = new File(testsDir, "file1.dat");
    	args = new String[]{ new UCP().getName(),
    			"-u", "anonymous", "-v",
    			uftpURL, target.getAbsolutePath() };
    	assertEquals(0, ClientDispatcher._main(args));
    	assertEquals(Utils.md5(src), Utils.md5(target));
    	
    	args = new String[]{ new Share().getName(),
    			"-u", "demouser:test123",
    			"--server", getShareURL(),
    			"--delete",
    			src.getAbsolutePath()
    	};
    	assertEquals(0, ClientDispatcher._main(args));

    	
    	// writeable share
    	args = new String[]{ new Share().getName(),
    			"-u", "demouser:test123", "-v", 
    			"--server", getShareURL(),
    			"--write",
    			"--access", "cn=anonymous,o=unknown,ou=unknown",
    			src.getAbsolutePath()
    	};
    	assertEquals(0, ClientDispatcher._main(args));
    	uftpURL = Share._lastShare.getJSONObject("share").getString("uftp");
    	args = new String[]{ new UCP().getName(),
    			"-u", "anonymous",
    			"./pom.xml", uftpURL};
    	assertEquals(0, ClientDispatcher._main(args));
    	assertEquals(Utils.md5(src), Utils.md5(new File("./pom.xml")));
    	
    }

}
