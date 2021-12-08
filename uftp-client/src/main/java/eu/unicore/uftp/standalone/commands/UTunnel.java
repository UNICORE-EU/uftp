package eu.unicore.uftp.standalone.commands;

import java.net.InetAddress;

import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.http.HttpResponse;
import org.json.JSONException;
import org.json.JSONObject;

import eu.unicore.uftp.client.TunnelClient;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.util.BaseClient;

/**
 * tunneling client: establish a tunnel via UFTPD and start local listening socket
 * 
 * @author schuller
 */
public class UTunnel extends Command {
	
	String host = "";
	int remotePort;
	int localPort;
	
	@Override
	public String getName() {
		return "tunnel";
	}
	
	@SuppressWarnings("static-access")
	protected Options getOptions() {
		Options options = super.getOptions();

		options.addOption(
				OptionBuilder.withLongOpt("listen")
				.withDescription("port:remote_host:remote_port")
				.isRequired(true)
				.hasArg()
				.create("L")
				);
		return options;
	}

	@Override
	protected void run(ClientFacade client) throws Exception {
		String url = fileArgs[0];
		String[]tok = line.getOptionValue('L').split(":");
		localPort = Integer.parseInt(tok[0]);
		host = tok[1];
		remotePort = Integer.parseInt(tok[2]);
		BaseClient bc = getClient(url, client);
		JSONObject request = createRequest(host, remotePort);
		System.out.println("Sending tunneling request:" + request);
		HttpResponse httpResponse = bc.post(request,url);
		bc.checkError(httpResponse);
		JSONObject response = bc.asJSON(httpResponse);
		InetAddress localSrv = InetAddress.getByName("localhost");// TODO
		InetAddress[] servers = Utils.parseInetAddresses(response.getString("serverHost"), null);
		int uftpPort = Integer.parseInt(response.getString("serverPort"));
		String secret = response.getString("secret");
		
		try(TunnelClient tc = new TunnelClient(localSrv, localPort, servers, uftpPort)){
			tc.setSecret(secret);
			tc.connect();
			System.out.println("Listening on localhost:"+localPort);
			tc.run();
			while(true){
				Thread.sleep(2000);
			}
		}
	}
	
	@Override
	public String getArgumentDescription() {
		return "-L port:remote_host:remote_port https://<server-host>:<server-port>/rest/share/SERVER-NAME/tunnel"
				;
	}
	
	public String getSynopsis(){
		return "Forward connections to a local port to a remote socket via UFTPD";
	}
	
	protected BaseClient getClient(String url, ClientFacade client) throws Exception {	
		return new BaseClient(url, client.getConnectionManager().getAuthData());
	}
	
	protected JSONObject createRequest(String target, int port) throws JSONException {
		JSONObject o = new JSONObject();
		o.put("targetHost", target);
		o.put("targetPort", port);
		o.put("client", clientIP);
		return o;
	}
	
}
