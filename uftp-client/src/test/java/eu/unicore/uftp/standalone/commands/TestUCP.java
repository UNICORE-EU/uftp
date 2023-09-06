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
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.ConnectionInfoManager;
import eu.unicore.uftp.standalone.UFTPClientFactory;
import eu.unicore.uftp.standalone.lists.FileCrawler.RecursivePolicy;

public class TestUCP extends BaseServiceTest {
	
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


	protected void cp(String source, String target, RecursivePolicy policy) throws Exception {
		UCP ucp = new UCP();
		ucp.client = client;
		ucp.recurse = RecursivePolicy.RECURSIVE.equals(policy);
		ucp.cp(source, target);
	}

    @Test
    public void testSingleUpload() throws Exception {
    	String src = "./pom.xml";
    	String target = testsDir.getAbsolutePath();
    	cp(src, getAuthURL(target), RecursivePolicy.NONRECURSIVE);
        assertEquals(Utils.md5(new File(src)), Utils.md5(new File(testsDir, "pom.xml")));
    }

    @Test
    public void testSingleUploadWithRename() throws Exception {
       	String src = "./pom.xml";
    	String target = new File(testsDir, "testfile.xml").getAbsolutePath();
    	cp(src, getAuthURL(target), RecursivePolicy.NONRECURSIVE);
        assertEquals(Utils.md5(new File(src)), Utils.md5(new File(target)));
    }

    @Test
    public void testMultipleUploads() throws Exception {
    	for(int i = 0; i<3; i++) {
    		FileUtils.writeStringToFile(new File(testsDir, "inputs/file"+i), "test123"+i, "UTF-8");
    	}
      	String src = testsDir.getAbsolutePath()+"/inputs/file*";
    	String target = testsDir.getAbsolutePath();
    	cp(src, getAuthURL(target), RecursivePolicy.NONRECURSIVE);
    	for(int i = 0; i<3; i++) {
    		assertEquals(Utils.md5(new File(testsDir, "inputs/file"+i)),
    				     Utils.md5(new File(testsDir, "file"+i)));
    	}
    }

    @Test(expected = IOException.class)
    public void testFailingDownload() throws Exception {
        String nonExistingFile = "/some/path/file1.dat";
        cp(getAuthURL(nonExistingFile), "/tmp/filexxx.dat", RecursivePolicy.NONRECURSIVE);
    }

    @Test
    public void testRenameDownload() throws Exception {        
    	String src =  new File("./pom.xml").getAbsolutePath();
    	String target = testsDir.getAbsolutePath()+"/dl1.xml";
    	cp(getAuthURL(src), target, RecursivePolicy.NONRECURSIVE);
        assertEquals(Utils.md5(new File(src)), Utils.md5(new File(target)));
    }
    
    @Test
    public void testSingleDownload() throws Exception {
       	String src =  new File("./pom.xml").getAbsolutePath();
    	String target = testsDir.getAbsolutePath();
    	cp(getAuthURL(src), target, RecursivePolicy.NONRECURSIVE);
        assertEquals(Utils.md5(new File(src)), Utils.md5(new File(testsDir,"/pom.xml")));
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
    	
    	cp(getAuthURL(src), target, RecursivePolicy.NONRECURSIVE);
    	for(int i = 0; i<3; i++) {
    		assertEquals(Utils.md5(new File(testsDir, "inputs/file"+i)),
    				     Utils.md5(new File(testsDir, "downloads/file"+i)));
    	}
    	assertFalse(new File(testsDir, "downloads/not_you").exists());
    }

}
