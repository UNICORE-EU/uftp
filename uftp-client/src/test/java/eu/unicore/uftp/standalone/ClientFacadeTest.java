package eu.unicore.uftp.standalone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import eu.unicore.uftp.authserver.authenticate.UsernamePassword;
import eu.unicore.uftp.client.FileInfo;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.standalone.lists.FileCrawler.RecursivePolicy;

/**
 *
 * @author jj
 */
public class ClientFacadeTest extends BaseServiceTest {

	ClientFacade client ;
	
	@Before
	public void setup() throws Exception {
		client = new ClientFacade(
				new ConnectionInfoManager(new UsernamePassword("demouser", "test123")),
				new UFTPClientFactory());
	}
	
    @Test
    public void testFullDestination() {
        String result = client.getFullLocalDestination("/tmp/source/file1.dat", "/tmp/dest/");
        assertEquals("/tmp/dest/file1.dat", result);
    }
    
    @Test
    public void testFullDestination2() {
        String result = client.getFullLocalDestination("/tmp/source/file1.dat", ".");
        assertEquals("file1.dat", new File(result).getName());
    }
    
    @Test
    public void testFullDestination3() {
        String result = client.getFullRemoteDestination("/tmp/source/file1.dat", "/foo/");
        assertEquals("file1.dat", new File(result).getName());
    }
    
    @Ignore // TODO handle exceptions from ClientPool tasks  
    @Test(expected = IOException.class)
    public void testFailingUpload() throws Exception {
        System.out.println("Testing failing uplaod");
        String nonExistingLocalFile = "/non/existing/path/file.ooo";
        client.cp(nonExistingLocalFile, "uftp://jj:pass@xxxxx/some/path/foo.bar", RecursivePolicy.RECURSIVE);
    }

    @Test
    public void testSingleUpload() throws Exception {
    	String src = "./pom.xml";
    	String target = new File("./").getAbsolutePath();
    	client.cp(src, getAuthURL(target), RecursivePolicy.NONRECURSIVE);
        assertEquals(Utils.md5(new File(src)), Utils.md5(new File(target+"/pom.xml")));
    }

    @Test
    public void testSingleUploadWithRename() throws Exception {
       	String src = "./pom.xml";
    	String target = new File("./target/data/testfile.xml").getAbsolutePath();
    	client.cp(src, getAuthURL(target), RecursivePolicy.NONRECURSIVE);
        assertEquals(Utils.md5(new File(src)), Utils.md5(new File(target)));
    }

    @Test
    public void testMultipleUploads() throws Exception {
    	for(int i = 0; i<3; i++) {
    		FileUtils.writeStringToFile(new File("target/inputs/file"+i), "test123"+i, "UTF-8");
    	}
      	String src = "target/inputs/file*";
    	String target = new File("./target/data/").getAbsolutePath();
    	client.cp(src, getAuthURL(target), RecursivePolicy.NONRECURSIVE);
    	for(int i = 0; i<3; i++) {
    		assertEquals(Utils.md5(new File("target/inputs/file"+i)),
    				     Utils.md5(new File("target/data/file"+i)));
    	}
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLocalCopy() throws Exception {
        System.out.println("Testing local copy");
        client.cp("/tmp/a.dat", "/tmp/b.dat", RecursivePolicy.NONRECURSIVE);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testRemoteCopy() throws Exception {
        System.out.println("Testing pure remote copy");
        client.cp("ufp://jj:pass@xxxx/some/path/a.dat", "ufp://jj:pass@xxxx/some/path/b.dat", RecursivePolicy.NONRECURSIVE);
    }

    @Test(expected = IOException.class)
    public void testFailingDownload() throws Exception {
        String nonExistingFile = "/some/path/file1.dat";
        client.cp(getAuthURL(nonExistingFile), "/tmp/filexxx.dat", RecursivePolicy.NONRECURSIVE);
    }

    @Test
    public void testRenameDownload() throws Exception {        
    	String src =  new File("./pom.xml").getAbsolutePath();
    	String target = "./target/data/dl1.xml";
    	client.cp(getAuthURL(src), target, RecursivePolicy.NONRECURSIVE);
        assertEquals(Utils.md5(new File(src)), Utils.md5(new File(target)));
    }
    
    @Test
    public void testSingleDownload() throws Exception {
       	String src =  new File("./pom.xml").getAbsolutePath();
    	String target = "./target/data/";
    	client.cp(getAuthURL(src), target, RecursivePolicy.NONRECURSIVE);
        assertEquals(Utils.md5(new File(src)), Utils.md5(new File(target+"/pom.xml")));
    }
    
    @Test
    public void testMultipleDownload() throws Exception {
       	for(int i = 0; i<3; i++) {
    		FileUtils.writeStringToFile(new File("target/inputs/file"+i), "test123"+i, "UTF-8");
    	}
    	FileUtils.writeStringToFile(new File("target/inputs/notthis"), "nope", "UTF-8");
    	FileUtils.forceMkdir(new File("target/downloads"));
      	String src = new File("./target/inputs").getAbsolutePath()+"/file*";
      	String target = new File("./target/downloads/").getAbsolutePath();
    	
    	client.cp(getAuthURL(src), target, RecursivePolicy.NONRECURSIVE);
    	for(int i = 0; i<3; i++) {
    		assertEquals(Utils.md5(new File("target/inputs/file"+i)),
    				     Utils.md5(new File("target/downloads/file"+i)));
    	}
    	assertFalse(new File("target/downloads/notthis").exists());
    }

    @Test
    public void testCdPwd() throws Exception {
    	client.connect(getAuthURL(""));
        client.cd(new File("src").getAbsolutePath());
        String pwd = client.pwd();
        assertTrue(pwd.contains("src"));
    }
    
    @Test
    public void testList() throws Exception {
        client.connect(getAuthURL(""));
        List<FileInfo> ls = client.ls("/");
        System.out.println("ls /");
        for (FileInfo string : ls) {
            System.out.println("\t"+string);
        }
    }

}
