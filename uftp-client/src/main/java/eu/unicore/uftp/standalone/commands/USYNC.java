package eu.unicore.uftp.standalone.commands;

import eu.unicore.uftp.standalone.ClientFacade;

public class USYNC extends DataTransferCommand {

	@Override
	public String getName() {
		return "sync";
	}

	@Override
	protected void run(ClientFacade client) throws Exception {
		client.sync(fileArgs[0], fileArgs[1]);
	}
	
	@Override
	public String getArgumentDescription() {
		return "<master> <slave>";
	}
	
	public String getSynopsis(){
		return "Sync a file (the slave) with a master file.";
	}

}
