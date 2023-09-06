package eu.unicore.uftp.standalone.commands;

import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.standalone.ClientFacade;

public class UMKDIR extends Command {

	@Override
	public String getName() {
		return "mkdir";
	}

	@Override
	protected void run(ClientFacade client) throws Exception {
		if(fileArgs.length==0) {
			throw new IllegalArgumentException("Missing argument: "+getArgumentDescription());
		}
		UFTPSessionClient sc = null;
		for(String fileArg: fileArgs) {
			sc = client.checkReInit(fileArg, sc);
			String path = client.getConnectionManager().getPath();
			sc.mkdir(path);
		}
	}
		
	@Override
	public String getArgumentDescription() {
		return "<remote_directory> ...";	
	}

	@Override
	public String getSynopsis() {
		return "Creates remote directories.";
	}
	
}
