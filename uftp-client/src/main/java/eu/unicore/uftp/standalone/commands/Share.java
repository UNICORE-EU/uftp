package eu.unicore.uftp.standalone.commands;

import java.io.File;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.http.HttpResponse;
import org.json.JSONException;
import org.json.JSONObject;

import eu.emi.security.authn.x509.helpers.BinaryCertChainValidator;
import eu.unicore.security.Client;
import eu.unicore.services.rest.client.BaseClient;
import eu.unicore.uftp.datashare.AccessType;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.util.httpclient.DefaultClientConfiguration;

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

	protected Options getOptions() {
		Options options = super.getOptions();
		options.addOption(Option.builder("l").longOpt("list")
				.desc("List shares")
				.required(false)
				.build());
		options.addOption(Option.builder("s").longOpt("server")
				.desc("URL to the share service e.g. <https://host:port/SITE/rest/share/NAME>")
				.required(false)
				.hasArg()
				.build());
		options.addOption(Option.builder("a").longOpt("access")
				.desc("Allow access for the specified user")
				.required(false)
				.hasArg()
				.build());
		options.addOption(Option.builder("w").longOpt("write")
				.desc("Allow write access to the shared path")
				.required(false)
				.build());
		options.addOption(Option.builder("d").longOpt("delete")
				.desc("Delete access to the shared path")
				.required(false)
				.build());
		options.addOption(Option.builder("1").longOpt("one-time")
				.desc("Allow only one access to a share (one-time share)")
				.required(false)
				.build());
		options.addOption(Option.builder("L").longOpt("lifetime")
				.desc("Limit lifetime of share (in seconds)")
				.required(false)
				.hasArg()
				.build());
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
		JSONObject shares = bc.getJSON();
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
		if(fileArgs.length==0) {
			throw new IllegalArgumentException("Missing argument: <path>");
		}
		String path = fileArgs[0];
		
		File file = new File(path);
		if(!file.isAbsolute()) {
			file = new File(System.getProperty("user.dir"), file.getPath());
		}
		path = file.getPath();
		boolean onetime = line.hasOption("1");
		long lifetime = Long.parseLong(line.getOptionValue("L", "0"));
		JSONObject req = createRequest(accessType, target, path, onetime, lifetime);
		BaseClient bc = getClient(url, client);
		HttpResponse res = bc.post(req);
		bc.checkError(res);
		if(!delete) {
			String location = res.getFirstHeader("Location").getValue();
			if(location!=null){
				bc.setURL(location);
				showNewShareInfo(bc.getJSON());
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
		DefaultClientConfiguration sec = new DefaultClientConfiguration();
		sec.setValidator(new BinaryCertChainValidator(true));
        sec.setSslAuthn(true);
        sec.setSslEnabled(true);
		return new BaseClient(url, sec, client.getConnectionManager().getAuthData());
	}
	
	protected JSONObject createRequest(String access, String target, String path, boolean onetime, long lifetime) throws JSONException {
		JSONObject o = new JSONObject();
		AccessType t = AccessType.valueOf(access);
		o.put("path", path);
		o.put("user", target);
		o.put("access", t.toString());
		if(onetime) {
			o.put("onetime", "true");
		}
		if(lifetime>0) {
			o.put("lifetime", String.valueOf(lifetime));
		}
		return o;
	}
	
}
