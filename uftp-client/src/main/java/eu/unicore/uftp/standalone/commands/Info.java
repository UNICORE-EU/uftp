package eu.unicore.uftp.standalone.commands;

import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.ConnectionInfoManager;
import eu.unicore.uftp.standalone.authclient.AuthClient;

public class Info extends Command {
	
	boolean raw = false;
	
	boolean unicoreXStyle = false;
	
	@Override
	public String getName() {
		return "info";
	}

	@SuppressWarnings("static-access")
	protected Options getOptions() {
		Options options = super.getOptions();
		options.addOption(
				OptionBuilder.withLongOpt("raw")
				.withDescription("Show raw response from server")
				.isRequired(false)
				.create("R")
				);
		
		return options;
	}
	
	@Override
	public void parseOptions(String[] args) throws ParseException {
		super.parseOptions(args);
		raw = line.hasOption('R');
	}

	@Override
	protected void run(ClientFacade client) throws Exception {	
		ConnectionInfoManager mgr = client.getConnectionManager();
		String uri = fileArgs[0];
		mgr.init(uri+":/");
		AuthClient auth = mgr.getAuthClient(client);
		HttpResponse res = auth.getInfo();
		if(res.getStatusLine().getStatusCode()!=200){
			System.out.println("Error: "+res.getStatusLine());
		}
		else{
			String s = EntityUtils.toString(res.getEntity());
			if(raw) {
				System.out.println(s);
				System.out.println();
			}
			JSONObject j = new JSONObject(s);
			System.out.println(auth.parseInfo(j));
		}
	}
	
	@Override
	public String getArgumentDescription() {
		return "<UFTP-Auth-URL>";
	}
	
	public String getSynopsis(){
		return "Gets info about the remote server";
	}
	
}
