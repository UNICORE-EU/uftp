package eu.unicore.uftp.standalone.commands;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.rsync.RsyncStats;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.ConnectionInfoManager;

public class USYNC extends DataTransferCommand {

	@Override
	public String getName() {
		return "sync";
	}

	@Override
	protected void run(ClientFacade client) throws Exception {
		if(fileArgs.length<2) {
			throw new IllegalArgumentException("Missing argument: "+getArgumentDescription());
		}
		RsyncStats stats = null;
		UFTPSessionClient sc = null;
		String master = fileArgs[0];
		String slave = fileArgs[1];
		verbose("sync {} (MASTER) -> {} (SLAVE)", master, slave);	
		try {
			if (ConnectionInfoManager.isRemote(master) && ConnectionInfoManager.isLocal(slave)) {
				sc = client.doConnect(master);
				Map<String, String> params = client.getConnectionManager().extractConnectionParameters(master);
				String path = params.get("path");
				stats = rsyncLocalFile(slave, path, sc);
			}
			else if (ConnectionInfoManager.isLocal(master) && ConnectionInfoManager.isRemote(slave)) {
				sc = client.doConnect(slave);
				Map<String, String> params = client.getConnectionManager().extractConnectionParameters(slave);
				String path = params.get("path");
				stats =  rsyncRemoteFile(master, path, sc);
			}
			else {
				throw new IOException("Need one remote and one local file for sync.");
			}
			verbose("Statistics : {}", stats);
		}
		finally{
			IOUtils.closeQuietly(sc);
		}
	}

	private RsyncStats rsyncRemoteFile(String local, String remote, UFTPSessionClient sc) throws Exception {
		File localMaster = new File(local);
		if (!localMaster.isFile()) {
			throw new IllegalArgumentException(local + " is not a file");
		}
		return sc.syncRemoteFile(localMaster, remote);
	}

	private RsyncStats rsyncLocalFile(String local, String remote, UFTPSessionClient sc) throws Exception {
		File localSlave = new File(local);
		if (!localSlave.isFile()) {
			throw new IllegalArgumentException(local + " is not a file");
		}
		return sc.syncLocalFile(remote, localSlave);
	}


	@Override
	public String getArgumentDescription() {
		return "<master> <slave>";
	}
	
	public String getSynopsis(){
		return "Sync a file (the slave) with a master file.";
	}

}
