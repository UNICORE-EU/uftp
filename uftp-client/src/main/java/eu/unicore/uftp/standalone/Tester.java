package eu.unicore.uftp.standalone;

import java.util.List;

import org.apache.log4j.BasicConfigurator;

import eu.unicore.uftp.client.FileInfo;
import eu.unicore.uftp.standalone.lists.FileCrawler;

/**
 *
 * @author jj
 */
public class Tester {

    ClientFacade facade = new ClientFacade(new ConnectionInfoManager(null), new UFTPClientFactory());
    
    
    
    public static void main(String[] args) throws Exception {
        BasicConfigurator.configure();
        Tester tester = new Tester();
        
        System.out.println("Downloading with wildcards:");
        tester.testCopy("uftp://jj:pass@localhost/tmp/source/*.dat","/home/jj/tmp/");
               
        System.out.println("Downloading single file:");
        tester.testCopy("uftp://jj:pass@localhost/tmp/jj/file.dat", "/home/jj/tmp/a.dat");
        System.out.println("Downloading single file to directory:");
        tester.testCopy("uftp://jj:pass@localhost/tmp/file.dat", "/home/jj/tmp/");
        
        //upload with wildcards
        System.out.println("Uploading single file with rename");
        tester.testCopy("/tmp/jj/file.dat","uftp://jj:pass@localhost:9000/tmp/jj/fa.wd");
        System.out.println("Uploading with wildcard");
        tester.testCopy("/tmp/jj/*.dat","uftp://jj:pass@localhost:9000/tmp/");
        System.out.println("Uploading single file");
        tester.testCopy("/tmp/jj/file.dat","uftp://jj:pass@localhost:9000/tmp/");
        
        tester.disconnect();
    }
    
    public void disconnect() throws Exception {
        facade.disconnect();
    }
   
    public void testList(String dir) throws Exception {
        System.out.println("Testing list: "+dir);        
        List<FileInfo> ls = facade.ls(dir); 
        for (FileInfo file : ls) {
            System.out.println(file);
        }
    }
    
    public void testCopy(String source, String destination) throws Exception {
        facade.cp(source, destination, FileCrawler.RecursivePolicy.RECURSIVE);
    }
    
}
