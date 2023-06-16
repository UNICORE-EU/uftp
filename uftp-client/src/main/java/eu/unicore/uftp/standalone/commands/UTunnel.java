package eu.unicore.uftp.standalone.commands;

import java.net.InetAddress;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.json.JSONException;
import org.json.JSONObject;

import eu.emi.security.authn.x509.helpers.BinaryCertChainValidator;
import eu.unicore.services.rest.client.BaseClient;
import eu.unicore.uftp.client.TunnelClient;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.util.httpclient.DefaultClientConfiguration;
import eu.unicore.util.httpclient.ServerHostnameCheckingMode;

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
	
	protected Options getOptions() {
		Options options = super.getOptions();
		options.addOption(Option.builder("L")
			.longOpt("listen")
			.desc("port:remote_host:remote_port")
			.required(true)
			.hasArg()
			.build());
		return options;
	}

	@Override
	protected void run(ClientFacade client) throws Exception {
		if(fileArgs.length==0) {
			throw new IllegalArgumentException("Missing argument: "+getArgumentDescription());
		}
		String url = fileArgs[0];
		String[]tok = line.getOptionValue('L').split(":");
		localPort = Integer.parseInt(tok[0]);
		host = tok[1];
		remotePort = Integer.parseInt(tok[2]);
		BaseClient bc = getClient(url, client);
		JSONObject request = createRequest(host, remotePort);
		System.out.println("<-- " + request);
		JSONObject response = null;
		try(ClassicHttpResponse httpResponse = bc.post(request)){
			response = bc.asJSON(httpResponse);
		}
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
		DefaultClientConfiguration cc = new DefaultClientConfiguration();
		cc.setServerHostnameCheckingMode(ServerHostnameCheckingMode.NONE);
		cc.setValidator(new BinaryCertChainValidator(true));
		cc.setSslEnabled(true);
		return new BaseClient(url, cc, client.getConnectionManager().getAuthData());
	}
	
	protected JSONObject createRequest(String target, int port) throws JSONException {
		JSONObject o = new JSONObject();
		o.put("targetHost", target);
		o.put("targetPort", port);
		o.put("client", clientIP);
		return o;
	}
	
}
