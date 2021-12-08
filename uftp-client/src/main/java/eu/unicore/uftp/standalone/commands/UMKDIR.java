package eu.unicore.uftp.standalone.commands;

import eu.unicore.uftp.standalone.ClientFacade;

public class UMKDIR extends Command {

	@Override
	public String getName() {
		return "mkdir";
	}

	@Override
	protected void run(ClientFacade client) throws Exception {
		int len = fileArgs.length;
		for(int i=0; i<len;i++){
			client.mkdir(fileArgs[i]);
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
