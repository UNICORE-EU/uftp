package eu.unicore.uftp.authserver.admin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import eu.unicore.services.Kernel;
import eu.unicore.services.admin.AdminAction;
import eu.unicore.services.admin.AdminActionResult;
import eu.unicore.uftp.authserver.AuthServiceConfig;
import eu.unicore.uftp.authserver.UFTPBackend;
import eu.unicore.uftp.authserver.UFTPDInstance;
import eu.unicore.uftp.server.requests.UFTPGetUserInfoRequest;
import eu.unicore.util.Log;

/**
 * AdminAction showing the information the auth server has about a user (like ssh keys!)
 */
public class ShowUserInfo implements AdminAction {

	@Override
	public String getName() {
		return "ShowUserInfo";
	}

	@Override
	public AdminActionResult invoke(Map<String, String> params, Kernel kernel) {
		String userID = params.get("uid");
		String serverName = params.get("serverName");
		
		boolean success = true;
		String message = "Getting info for <" + userID + ">"
				 + (serverName!=null? " on server "+serverName : "");
		AdminActionResult res = new AdminActionResult(success, message);
		
		Collection<UFTPBackend>servers = new ArrayList<>();
		try {
			if(serverName!=null) {
				UFTPBackend server = getServer(serverName, kernel);
				if(server!=null)servers.add(server);
			}
			else {
				servers = getConfig(kernel).getServers();
			}
		}catch(Exception ex) {
			 return new AdminActionResult(false, Log.createFaultMessage("Error accessing uftpd server(s)", ex));
		}
		for(UFTPBackend s: servers) {
			String key = s.getServerName();
			String response = null;
			try {
				UFTPDInstance uftpd = s.getUFTPDInstance();
				UFTPGetUserInfoRequest req = new UFTPGetUserInfoRequest(userID);
				response = uftpd.sendRequest(req);
			}catch(Exception ex) {
				response = Log.createFaultMessage("Error getting info for <"+userID+">", ex);
			}
			res.addResult(key, response);
		}
		return res;
	}

	protected AuthServiceConfig getConfig(Kernel kernel){
		return kernel.getAttribute(AuthServiceConfig.class);
	}

	protected boolean haveServer(String serverName, Kernel kernel){
		return getConfig(kernel).getServer(serverName)!=null;
	}
	
	protected UFTPBackend getServer(String serverName, Kernel kernel) throws IOException {
		return getConfig(kernel).getServer(serverName);
	}
	
	@Override
	public String getDescription() {
		return "parameters: uid, [serverName]";
	}
}