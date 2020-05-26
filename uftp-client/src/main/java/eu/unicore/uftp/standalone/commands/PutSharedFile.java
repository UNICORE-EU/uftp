package eu.unicore.uftp.standalone.commands;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.cli.ParseException;

import eu.unicore.uftp.authserver.messages.AuthResponse;
import eu.unicore.uftp.client.UFTPClient;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.ConnectionInfoManager;
import eu.unicore.uftp.standalone.UFTPClientFactory;
import eu.unicore.uftp.standalone.authclient.AuthClient;
import eu.unicore.uftp.standalone.util.ProgressBar;

/**
 * upload a file to a shared path
 * 
 * @author schuller
 */
public class PutSharedFile extends BaseUFTPCommand {
	
	protected String target;

	@Override
	public String getName() {
		return "put-share";
	}

	@Override
	public String getArgumentDescription() {
		return " <source> [<source> ...] https://<server-host>:<server-port>/rest/share/SERVER-NAME/auth:target"
				;
	}
	
	public String getSynopsis(){
		return "Upload file(s) to a shared path";
	}
	
	@Override
	public void parseOptions(String[] args) throws ParseException {
		super.parseOptions(args);
		if(fileArgs.length<2){
			throw new IllegalArgumentException("Must specify at least a local source and remote path!");
		}
		target = fileArgs[fileArgs.length-1];
	}
	
	@Override
	protected void run(ClientFacade client) throws Exception {
		int len = fileArgs.length-1;
		boolean directory = len>1;
		for(int i=0; i<len;i++){
			String source = fileArgs[i];
			File sourceFile = new File(source);
			if(sourceFile.isDirectory()){
				System.out.println(" ... skipping directory <"+sourceFile.getName()+">");
				continue;
			}
			ConnectionInfoManager cim = client.getConnectionManager();
			cim.init(target);
			AuthClient authClient = cim.getAuthClient(client);
			String path = cim.getPath();
			if(directory || path.endsWith("/")){
				path = path+"/"+sourceFile.getName();
			}
			AuthResponse response = authClient.connect(path,false,false);
			try(InputStream sourceStream = new FileInputStream(sourceFile)){
				UFTPClient uftp = new UFTPClientFactory().getClient(response, sourceStream);
				ProgressBar pb = new ProgressBar(source, -1);
				uftp.setProgressListener(pb);
				uftp.run();
				pb.close();
			}
		}
	}
	
	protected File makeTarget(String target) throws IOException {
		return new File(target);
	}
	
}
