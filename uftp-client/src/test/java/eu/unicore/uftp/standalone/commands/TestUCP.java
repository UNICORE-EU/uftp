package eu.unicore.uftp.standalone.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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

public class TestUCP extends BaseServiceTest {
	
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
    	String[] args = new String[]{ new UCP().getName(), "-h" };
    	ClientDispatcher._main(args);
    }

    @Test
    public void testSingleUpload() throws Exception {
    	String src = "./pom.xml";
    	String target = testsDir.getAbsolutePath();
    	String[] args = new String[]{ new UCP().getName(), "-u", "demouser:test123",
    			src, getAuthURL(target)
    	};
    	assertEquals(0, ClientDispatcher._main(args));
    	assertEquals(Utils.md5(new File(src)), Utils.md5(new File(testsDir, "pom.xml")));
    }

    @Test
    public void testSingleUploadWithRename() throws Exception {
       	String src = "./pom.xml";
    	String target = new File(testsDir, "testfile.xml").getAbsolutePath();
    	String[] args = new String[]{ new UCP().getName(), "-u", "demouser:test123",
    			src, getAuthURL(target)
    	};
    	assertEquals(0, ClientDispatcher._main(args));
    	assertEquals(Utils.md5(new File(src)), Utils.md5(new File(target)));
    }

    @Test
    public void testMultipleUploads() throws Exception {
    	for(int i = 0; i<3; i++) {
    		FileUtils.writeStringToFile(new File(testsDir, "inputs/file"+i), "test123"+i, "UTF-8");
    	}
      	String src = testsDir.getAbsolutePath()+"/inputs/file*";
    	String target = testsDir.getAbsolutePath();
    	String[] args = new String[]{ new UCP().getName(), "-u", "demouser:test123",
    			src, getAuthURL(target)
    	};
    	assertEquals(0, ClientDispatcher._main(args));
    	for(int i = 0; i<3; i++) {
    		assertEquals(Utils.md5(new File(testsDir, "inputs/file"+i)),
    				     Utils.md5(new File(testsDir, "file"+i)));
    	}
    }

    @Test(expected = IOException.class)
    public void testFailingDownload() throws Exception {
        String nonExistingFile = "/some/path/file1.dat";
        UCP ucp = new UCP();
		ucp.client = client;
		ucp.cp(getAuthURL(nonExistingFile), "/tmp/filexxx.dat");
    }

    @Test
    public void testRenameDownload() throws Exception {        
    	String src =  new File("./pom.xml").getAbsolutePath();
    	String target = testsDir.getAbsolutePath()+"/dl1.xml";
    	String[] args = new String[]{ new UCP().getName(), "-u", "demouser:test123",
    			getAuthURL(src), target
    	};
    	assertEquals(0, ClientDispatcher._main(args));
    	assertEquals(Utils.md5(new File(src)), Utils.md5(new File(target)));
    }
    
    @Test
    public void testSingleDownload() throws Exception {
       	String src =  new File("./pom.xml").getAbsolutePath();
    	String target = testsDir.getAbsolutePath();
    	String[] args = new String[]{ new UCP().getName(), "-u", "demouser:test123",
    			getAuthURL(src), target
    	};
    	assertEquals(0, ClientDispatcher._main(args));
    	assertEquals(Utils.md5(new File(src)), Utils.md5(new File(testsDir,"/pom.xml")));
    }
    
    @Test
    public void testRangedDownload() throws Exception {
       	String src =  new File("./pom.xml").getAbsolutePath();
    	String target = testsDir.getAbsolutePath();
    	String[] args = new String[]{ new UCP().getName(), "-u", "demouser:test123",
    			"-B0-10",
    			getAuthURL(src), target
    	};
    	assertEquals(0, ClientDispatcher._main(args));
    	assertEquals(11, new File(testsDir,"/pom.xml").length());
    }

    @Test
    public void testMultipleDownload() throws Exception {
       	for(int i = 0; i<3; i++) {
    		FileUtils.writeStringToFile(new File(testsDir, "inputs/file"+i), "test123"+i, "UTF-8");
    	}
    	FileUtils.writeStringToFile(new File(testsDir, "inputs/not_you"), "nope", "UTF-8");
    	FileUtils.forceMkdir(new File(testsDir, "downloads"));
      	String src = new File(testsDir,"inputs").getAbsolutePath()+"/file*";
      	String target = new File(testsDir,"downloads/").getAbsolutePath();
	    String[] args = new String[]{ new UCP().getName(), "-u", "demouser:test123",
	    		"-t", "2", "-T", "256k",
    			getAuthURL(src), target
    	};
    	assertEquals(0, ClientDispatcher._main(args));
    	for(int i = 0; i<3; i++) {
    		assertEquals(Utils.md5(new File(testsDir, "inputs/file"+i)),
    				     Utils.md5(new File(testsDir, "downloads/file"+i)));
    	}
    	assertFalse(new File(testsDir, "downloads/not_you").exists());
    }

}
