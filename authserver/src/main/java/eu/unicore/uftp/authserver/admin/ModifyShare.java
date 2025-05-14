package eu.unicore.uftp.authserver.admin;

import java.util.Map;

import eu.unicore.services.Kernel;
import eu.unicore.services.admin.AdminAction;
import eu.unicore.services.admin.AdminActionResult;
import eu.unicore.uftp.authserver.share.ShareServiceProperties;
import eu.unicore.uftp.datashare.db.ACLStorage;
import eu.unicore.uftp.datashare.db.ShareDAO;
import eu.unicore.util.Log;

/**
 * AdminAction showing the information the auth server has about a user (like ssh keys!)
 */
public class ModifyShare implements AdminAction {

	@Override
	public String getName() {
		return "ModifyShare";
	}

	@Override
	public AdminActionResult invoke(Map<String, String> params, Kernel kernel) {
		String shareID = params.remove("id");
		if(shareID==null) {
			return new AdminActionResult(false, "The 'id' parameter is required.");
		}
		String serverName = params.remove("serverName");
		if(serverName==null) {
			return new AdminActionResult(false, "The 'serverName' parameter is required.");
		}
		boolean success = true;
		String message = "";
		try {
			ACLStorage db = getDB(serverName, kernel);
			ShareDAO share = getDB(serverName, kernel).read(shareID);
			for(String k: params.keySet()) {
				if("uid".equals(k)) {
					share.setUid(params.get(k));
				}
				else if("gid".equals(k)) {
					share.setGid(params.get(k));
				}
				else if("path".equals(k)) {
					share.setPath(params.get(k));
				}
				else if("owner".equals(k)) {
					share.setOwnerID(params.get(k));
				}
				else if("target".equals(k)) {
					share.setTargetID(params.get(k));
				}
				else {
					throw new IllegalArgumentException("Unknown parameter: '"+k+"'");
				}
			}
			db.getPersist().write(share);
			message = share.toString();
		}
		catch(Exception ex) {
			success = false;
			message = Log.createFaultMessage("Error processing "+params, ex);
		}
		return new AdminActionResult(success, message);
	}

	private ACLStorage getDB(String serverName, Kernel kernel) {
		return kernel.getAttribute(ShareServiceProperties.class).getDB(serverName);
	}

	@Override
	public String getDescription() {
		return "parameters: id, serverName, [uid], [gid], [owner], [path], [target]";
	}
}