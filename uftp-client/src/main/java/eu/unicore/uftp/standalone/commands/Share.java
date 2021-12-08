package eu.unicore.uftp.standalone.commands;

import java.io.File;

import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.http.HttpResponse;
import org.json.JSONException;
import org.json.JSONObject;

import eu.unicore.security.Client;
import eu.unicore.uftp.datashare.AccessType;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.util.BaseClient;

/**
 * create, update, delete shares
 * 
 * @author schuller
 */
public class Share extends Command {
	
	/**
	 * environment variable defining the server URL for sharing
	 */
	public static String UFTP_SHARE_URL = "UFTP_SHARE_URL";
	
	String url;
	
	@Override
	public String getName() {
		return "share";
	}
	
	@SuppressWarnings("static-access")
	protected Options getOptions() {
		Options options = super.getOptions();

		options.addOption(
				OptionBuilder.withLongOpt("list")
				.withDescription("List shares")
				.isRequired(false)
				.create("l")
				);
		options.addOption(
				OptionBuilder.withLongOpt("server")
				.withDescription("URL to the share service e.g. <https://host:port/SITE/rest/share/NAME> .")
				.isRequired(false)
				.hasArg()
				.create("s")
				);
		options.addOption(
				OptionBuilder.withLongOpt("access")
				.withDescription("Allow access for the specified user.")
				.isRequired(false)
				.hasArg()
				.create("a")
				);
		options.addOption(
				OptionBuilder.withLongOpt("write")
				.withDescription("Allow write access to the shared path.")
				.isRequired(false)
				.create("w")
				);
		options.addOption(
				OptionBuilder.withLongOpt("delete")
				.withDescription("Delete access to the shared path.")
				.isRequired(false)
				.create("d")
				);
		return options;
	}

	@Override
	protected void run(ClientFacade client) throws Exception {
		url = line.getOptionValue("s", Utils.getProperty(UFTP_SHARE_URL, null));
		if(url==null) {
			throw new IllegalArgumentException("Must specify share service via '--server <URL>' or environment variable 'UFTP_SHARE_URL'");
		}
		if(line.hasOption("l")){
			listShares(client);
			return;
		}
		
		share(client);
	}
	
	public void listShares(ClientFacade client) throws Exception {
		BaseClient bc = getClient(url, client);
		JSONObject shares = bc.getJSON(url);
		System.out.println(shares.toString(2));
	}
	
	public void share(ClientFacade client) throws Exception {
		boolean anonymous = !line.hasOption("a");
		boolean write = line.hasOption("w");
		boolean delete = line.hasOption("d");
		if(write && delete){
			throw new IllegalArgumentException("Cannot have both --write and --delete");
		}
		if(write && anonymous){
			throw new IllegalArgumentException("Cannot have --write without specifying --access. "
					+ "If you REALLY want anonymous write access, use: --access 'cn=anonymous,o=unknown,ou=unknown'");
		}
		
		String accessType = write? "WRITE" : "READ" ;
		if(delete)accessType = "NONE";
		String target = anonymous? Client.ANONYMOUS_CLIENT_DN : line.getOptionValue('a');
		String path = fileArgs[0];
		
		File file = new File(path);
		if(!file.isAbsolute()) {
			file = new File(System.getProperty("user.dir"), file.getPath());
		}
		path = file.getPath();
			
		JSONObject req = createRequest(accessType, target, path);
		BaseClient bc = getClient(url, client);
		HttpResponse res = bc.post(req, url);
		bc.checkError(res);
		if(!delete) {
			String location = res.getFirstHeader("Location").getValue();
			if(location!=null){
				showNewShareInfo(bc.getJSON(location));
			}
		}
	}
	
	protected void showNewShareInfo(JSONObject info) throws Exception {
		if(verbose)System.out.println(info.toString(2));
		System.out.println("Shared to: "+info.getJSONObject("share").getString("http"));
	}
	
	@Override
	public String getArgumentDescription() {
		return "<path>"
				+" OR --write --access <target-dn> <path>"
				+" OR --delete <path>"
				+" OR --delete --access <target-dn> <path>"
				;
	}
	
	public String getSynopsis(){
		return "Create, update and delete shares.";
	}
	
	protected BaseClient getClient(String url, ClientFacade client) throws Exception {	
		return new BaseClient(url, client.getConnectionManager().getAuthData());
	}
	
	protected JSONObject createRequest(String access, String target, String path) throws JSONException {
		JSONObject o = new JSONObject();
		AccessType t = AccessType.valueOf(access);
		o.put("path", path);
		o.put("user", target);
		o.put("access", t.toString());
		return o;
	}
	
}
