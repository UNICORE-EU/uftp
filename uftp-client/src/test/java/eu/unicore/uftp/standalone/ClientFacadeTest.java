package eu.unicore.uftp.standalone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import eu.unicore.uftp.authserver.messages.AuthResponse;
import eu.unicore.uftp.client.FileInfo;
import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.standalone.lists.FileCrawler.RecursivePolicy;

/**
 *
 * @author jj
 */
public class ClientFacadeTest {

    static final String path = "/tmp/";
    static final String[] files = {path + "file1.dat", path + "file2.dat", path + "file.txt"};
    static FakeUftpClient fakeClient;

    static {
        BasicConfigurator.configure(new ConsoleAppender());
        InetAddress[] addresses = new InetAddress[]{};
        fakeClient = new FakeUftpClient(addresses, 6666);
    }

    @BeforeClass
    public static void setUpClass() throws IOException {
        for (String file : files) {
            File f = new File(file);
            f.createNewFile();
            try (PrintWriter writer = new PrintWriter(file, "UTF-8")) {
                writer.println("This is the content of the "+file);
            }
        }
    }

    @AfterClass
    public static void tearDownClass() {
        for (String file : files) {
            File f = new File(file);
            f.delete();
        }
    }

    @Before
    public void setUp() throws Exception {
        instance.connect("uftp://jj:pass@remote/some/path");
        fakeClient.cleanFiles();
    }

    @After
    public void tearDown() throws Exception {
        instance.disconnect();
    }

    ClientFacade instance = new ClientFacade(new FakeConnectionInfoManager(), new FakeUftpClientFactory());

    @Test
    public void testFullDestination() {
        System.out.println("Testing fullDestination");

        String result = instance.getFullLocalDestination("/tmp/source/file1.dat", "/tmp/dest/");
        assertEquals("/tmp/dest/file1.dat", result);
    }
    
    @Test
    public void testFullDestination2() {
        System.out.println("Testing fullDestination2");
        String result = instance.getFullLocalDestination("/tmp/source/file1.dat", ".");
        assertEquals("file1.dat", new File(result).getName());
    }
    
    @Test
    public void testFullDestination3() {
        System.out.println("Testing fullDestination3");
        String result = instance.getFullRemoteDestination("/tmp/source/file1.dat", "/foo/");
        assertEquals("file1.dat", new File(result).getName());
    }
    
    @Ignore // TODO handle exceptions from ClientPool tasks  
    @Test(expected = IOException.class)
    public void testFailingUpload() throws Exception {
        System.out.println("Testing failing uplaod");
        String nonExistingLocalFile = "/non/existing/path/file.ooo";
        instance.cp(nonExistingLocalFile, "uftp://jj:pass@xxxxx/some/path/foo.bar", RecursivePolicy.RECURSIVE);
    }

    @Test
    public void testSingleUpload() throws Exception {
        System.out.println("Tesing cp");
        instance.cp(files[0], "uftp://jj:pass@exxx/UFTP:/some/path/", RecursivePolicy.NONRECURSIVE);
        assertTrue(fakeClient.fileExist("/some/path/file1.dat"));
        System.out.println("Content of the uploaded file is: "+fakeClient.getFileContent("/some/path/file1.dat"));
        //content has name of the file (see init)
        assertTrue(fakeClient.getFileContent("/some/path/file1.dat").contains(files[0]));
    }

    @Test
    public void testSingleUploadWithRename() throws Exception {
        System.out.println("Testing upload with rename");
        instance.cp(files[0], "uftp://jj:pass@exxx/UFTP:/some/path/blahblah.txt", RecursivePolicy.NONRECURSIVE);
        assertTrue(fakeClient.fileExist("/some/path/blahblah.txt"));
    }

    @Test
    public void testMultipleUploads() throws Exception {
        System.out.println("Testing multiple uploads");
        instance.cp(path+"*.dat", "uftp://xxxx/UFTP:/some/path/", RecursivePolicy.NONRECURSIVE);
        for (String string : files) {
        	String msg = "Checking if " + string + " was " + (string.endsWith(".dat") ? "" : "not") + " uploaded";
        	System.out.println(msg);
            assertEquals(msg, string.endsWith(".dat"), fakeClient.fileExist(string.replace(path, "/some/path/")));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLocalCopy() throws Exception {
        System.out.println("Testing local copy");
        instance.cp("/tmp/a.dat", "/tmp/b.dat", RecursivePolicy.NONRECURSIVE);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testRemoteCopy() throws Exception {
        System.out.println("Testing pure remote copy");
        instance.cp("ufp://jj:pass@xxxx/some/path/a.dat", "ufp://jj:pass@xxxx/some/path/b.dat", RecursivePolicy.NONRECURSIVE);
    }

    @Test(expected = IOException.class)
    public void testFailingDownload() throws Exception {
        System.out.println("Testing download");
        String nonExistingFile = "/some/path/file1.dat";
        assertFalse(fakeClient.fileExist(nonExistingFile));
        instance.cp("uftp://jj:pass@xxxx/UFTP:"+nonExistingFile, "/tmp/filexxx.dat", RecursivePolicy.NONRECURSIVE);
    }

    @Test
    public void testRenameDownload() throws Exception {        
        System.out.println("Testing download with rename");
        //upload first to download
        String destination="/path/to/file.dat";
        String server = "uftp://jj:pass@xxxx/UFTP:";
        instance.cp(files[0], server+destination, RecursivePolicy.NONRECURSIVE);
        assertTrue(fakeClient.fileExist(destination));
        
        String localDestination = "/tmp/rando.dat";
        instance.cp(server+destination, localDestination, RecursivePolicy.NONRECURSIVE);
        File file = new File(localDestination);
        assertTrue(file.exists());
        assertEquals(file.length(), fakeClient.getFileContent(destination).length());
        file.delete();
    }
    
    @Test
    public void testSingleDownload() throws Exception {
        System.out.println("Testing single download");
        
        String destination="/path/to/file.dat";
        String server = "uftp://jj:pass@xxxx/UFTP:";
        instance.cp(files[0], server+destination, RecursivePolicy.NONRECURSIVE);
        assertTrue(fakeClient.fileExist(destination));
        
        String localDestination = "/tmp/";
        instance.cp(server+destination, localDestination, RecursivePolicy.NONRECURSIVE);
        localDestination = destination.replace("/path/to/", localDestination);
        File file = new File(localDestination);
        assertTrue(file.exists());
        assertEquals(file.length(), fakeClient.getFileContent(destination).length());
        file.delete();
    }
    
    @Test
    public void testMultipleDownload() throws Exception {
        System.out.println("Multiple download");
        String destinaitonPath = "/some/path/";
        String destinationServer = "uftp://jj:pass@xxxx/UFTP:";
        for (String string : files) {
            instance.cp(string, destinationServer+destinaitonPath, RecursivePolicy.NONRECURSIVE);
        }
        
        for (String string : files) {
            assertTrue("Checking if " + string + " was uploaded", fakeClient.fileExist(string.replace(path, "/some/path/")));
        }
        
        String localDownloadPath = "/tmp/mylocaldir/";
        File localDir = new File(localDownloadPath);
        localDir.mkdir();
        assertTrue(localDir.exists());
        assertTrue(localDir.isDirectory());
        
        
        String remoteLocation = destinationServer+destinaitonPath+"*.dat";
        System.out.println("Downloading from "+remoteLocation+" to "+localDownloadPath);
        instance.cp(remoteLocation, localDownloadPath, RecursivePolicy.RECURSIVE);
        
        List<String> downloaded = Arrays.asList(localDir.list());
        System.out.println("Local downloaded files:");
        for (String string : downloaded) {
            System.out.println("\t"+string);
        }
        
        for (String string : files) {            
            assertEquals("Checking if "+string+" was "+(string.endsWith(".dat")?"":"not")+" downloaded",
                    string.endsWith(".dat"), downloaded.contains(string.replace(path, "")));
        }
        
        
        FileUtils.deleteDirectory(localDir);
    }

    @Test
    public void testCdPwd() throws Exception {
        System.out.println("Tesing cd");
        instance.cd("somePath");
        String pwd = instance.pwd();
        assertEquals("somePath", pwd);

    }
    
    @Test
    public void testList() throws Exception {
        System.out.println("Testing list");
        List<FileInfo> ls = instance.ls("/");
        System.out.println("ls /");
        for (FileInfo string : ls) {
            System.out.println("\t"+string);
        }
        String remotePath = "/some/path/";
        for (String string : files) {
            instance.cp(string, "uftp://jj:pass@xxxx/UFTP:"+remotePath, RecursivePolicy.RECURSIVE);            
        }
        
        List<FileInfo> ls2 = instance.ls(remotePath);
        assertNotNull(ls2);
        System.out.println("ls /");
        for (FileInfo string : ls2) {
            System.out.println("\t"+string);
        }
        assertEquals(files.length, ls2.size());
        
        ls2 = instance.ls("/empty/path/");
        assertNotNull(ls2);
        assertTrue(ls2.isEmpty());
    }

    class FakeUftpClientFactory extends UFTPClientFactory {
        @Override
        public UFTPSessionClient getUFTPClient(AuthResponse response) throws UnknownHostException {
            return fakeClient;
        }
    }

}
