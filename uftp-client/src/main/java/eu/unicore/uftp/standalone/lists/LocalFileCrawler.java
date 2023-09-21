package eu.unicore.uftp.standalone.lists;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.client.FileInfo;
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
    
	private final String localSeparator;

	private final UFTPSessionClient sc;
    
	private final RecursivePolicy policy;

    public LocalFileCrawler(String path, String target, UFTPSessionClient sc, RecursivePolicy policy) {
    	this.path = path;
    	this.sc = sc;
    	this.target = target;
    	this.policy = policy;
    	this.localSeparator = File.separator;
    }

    private boolean init() throws Exception {
    	File source = new File(path);
    	if(source.isDirectory()){
    		if(RecursivePolicy.RECURSIVE!=policy){
    			System.err.println("uftp: omitting directory '"+path+"'");
    			return false;
    		}
    		target = target + "/" + source.getName();
    		safeMkDir(target);
    		if(!path.endsWith(localSeparator))path=path+localSeparator;
    	}
    	String name = FilenameUtils.getName(path);
        if (name == null || name.isEmpty()) {
            name = "*";
        }
        filter = WildcardFileFilter.builder().setWildcards(name).get();
        path = FilenameUtils.getFullPath(path);
        if("".equals(path))path= "." + localSeparator;
        return true;
    }
    
    Operation cmd;
    
    public void crawl(Operation cmd) throws Exception {
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
    		if(init()){
    			crawl(path, target, false);
    		}
    	}
    }
    
    private void crawl(String source, String remoteDirectory, boolean all) 
			throws Exception {
    	File[] files = getFiles(source, all);
    	if(files == null || files.length == 0)return;
    	
		for(File localFile: files){
			String target = remoteDirectory + "/" + localFile.getName();
			if(localFile.isDirectory()){
				if(RecursivePolicy.RECURSIVE!=policy){
					String msg = "uftp: omitting directory '"+localFile+"'";
					System.err.println(msg);
					logger.debug(msg);
				}else{
					safeMkDir(target);
					crawl(localFile.getPath(), target, true);
				}
			}else{
				cmd.execute(localFile.getPath(),target);
			}
		}
    }

    private void safeMkDir(String target) throws IOException {
    	FileInfo i = null;
    	try{
    		i = sc.stat(target);
		}catch(IOException ie){
			sc.mkdir(target);
			return;
		}
    	if(!i.isDirectory()){
			throw new IOException("Remote file exists: <"+target+"> and is not a directory.");
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
