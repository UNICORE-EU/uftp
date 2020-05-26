package eu.unicore.uftp.standalone.commands;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.cli.ParseException;

import eu.unicore.uftp.authserver.messages.AuthResponse;
import eu.unicore.uftp.client.UFTPClient;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.ConnectionInfoManager;
import eu.unicore.uftp.standalone.UFTPClientFactory;
import eu.unicore.uftp.standalone.authclient.AuthClient;
import eu.unicore.uftp.standalone.util.ProgressBar;

/**
 * download a shared file
 * 
 * @author schuller
 */
public class GetSharedFile extends BaseUFTPCommand {
	
	protected File target;

	@Override
	public String getName() {
		return "get-share";
	}

	@Override
	public String getArgumentDescription() {
		return "https://<server-host>:<server-port>/rest/share/SERVER-NAME/auth:file [target]"
				;
	}
	
	public String getSynopsis(){
		return "Download a shared file";
	}
	
	@Override
	public void parseOptions(String[] args) throws ParseException {
		super.parseOptions(args);
		if(fileArgs.length<1){
			throw new IllegalArgumentException("Must specify at least a source!");
		}
		if(fileArgs.length>1) {
			target = new File(fileArgs[fileArgs.length-1]);
		}
		else {
			target = new File(new File(fileArgs[0]).getName());
		}
	}
	
	@Override
	protected void run(ClientFacade client) throws Exception {
		String source = fileArgs[0];
		ConnectionInfoManager cim = client.getConnectionManager();
		cim.init(source);
		AuthClient authClient = cim.getAuthClient(client);
		String path = cim.getPath();
		AuthResponse response = authClient.connect(path,true,false);
		File targetFile = makeTarget(source);
		try(OutputStream targetStream = new FileOutputStream(targetFile)){
			UFTPClient uftp = new UFTPClientFactory().getClient(response, targetStream);
			ProgressBar pb = new ProgressBar(targetFile.getName(), -1);
			uftp.setProgressListener(pb);
			uftp.run();
			pb.close();
		}
	}
	
	protected File makeTarget(String source) throws IOException {
		if(target.isDirectory()) {
			return new File(target, new File(source).getName());
		}
		return target;
	}
	
}
