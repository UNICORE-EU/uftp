package eu.unicore.uftp.standalone.lists;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.util.Log;

/**
 *
 * @author jj
 */
public class LocalFileCrawler extends FileCrawler {

	private static final Logger logger = Log.getLogger(Log.CLIENT, LocalFileCrawler.class);

	private String path;
    
	private FileFilter filter;

	private String target;
    
	private final UFTPSessionClient sc;
    
    public LocalFileCrawler(String path, String target, UFTPSessionClient sc) {
    	this.path = path;
    	this.sc = sc;
    	this.target = target;
    }

    private boolean init(RecursivePolicy policy) throws Exception {
    	File source = new File(path);
    	if(source.isDirectory()){
    		if(RecursivePolicy.RECURSIVE!=policy){
    			System.err.println("uftp: omitting directory '"+path+"'");
    			return false;
    		}
    		target = new File(target, source.getName()).getPath();
    		sc.mkdir(target);
    		if(!path.endsWith("/"))path=path+"/";
    	}
    	String name = FilenameUtils.getName(path);
        if (name == null || name.isEmpty()) {
            name = "*";
        }
        filter = new WildcardFileFilter(name);
        path = FilenameUtils.getFullPath(path);
        if("".equals(path))path="./";

        return true;
    }
    
    Command cmd;
    
    public void crawl(Command cmd, RecursivePolicy policy) throws Exception {
    	this.cmd = cmd;
    	if(isSingleFile(path)){
    		if(!"-".equals(path)) {
    			// target can be a directory...
    			if(target.endsWith("/"))target+=new File(path).getName();
    			else{
    				try{
    					if(sc.stat(target).isDirectory()){
    						target=target+"/"+new File(path).getName();
    					}
    				}catch(Exception ignored){}
    			}
    		}
    		cmd.execute(path, target);
    	}
    	else{
    		if(init(policy)){
    			crawl(path, target, policy, false);
    		}
    	}
    }
    
    private void crawl(String source, String remoteDirectory, FileCrawler.RecursivePolicy policy, boolean all) 
			throws URISyntaxException, IOException {
    	File[] files = getFiles(source, all);
    	if(files == null || files.length == 0)return;
    	
		for(File localFile: files){
			String target = new File(remoteDirectory, localFile.getName()).getPath();
			if(localFile.isDirectory()){
				if(RecursivePolicy.RECURSIVE!=policy){
					String msg = "uftp: omitting directory '"+localFile+"'";
					System.err.println(msg);
					logger.debug(msg);
				}else{
					sc.mkdir(target);
					crawl(localFile.getPath(), target, policy, true);
				}
			}else{
				cmd.execute(localFile.getPath(),target);
			}
		}
    }


    /**
     * get matching files and directories
     * 
     * @param path - path to list from
     */
    private File[] getFiles(String path, boolean all){
    	return all ? new File(path).listFiles() : new File(path).listFiles(filter);
    }

    public boolean isSingleFile(String path) {
    	try{
    		if(new File(path).isDirectory())return false;
    	}catch(Exception ex){}
        String name = FilenameUtils.getName(path);
        if (name == null || name.isEmpty()) {
            return false;
        }
        return !name.contains("*") && !name.contains("?");
    }

}
