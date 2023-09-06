package eu.unicore.uftp.standalone.commands;

import java.util.Map;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.ConnectionInfoManager;
import eu.unicore.uftp.standalone.authclient.AuthResponse;
import eu.unicore.util.Log;

/**
 * server-server copy (remote copy)
 *
 * @author schuller
 */
public class URCP extends RangedCommand {

	protected String target;

	private String onetimePassword;
	private String uftpdAddress;

	@Override
	public String getName() {
		return "rcp";
	}

	@Override
	public String getArgumentDescription() {
		return "<source_1> ... <source_N> <target>";
	}

	@Override
	public String getSynopsis(){
		return "Server-to-server copy file(s).";
	}

	protected Options getOptions() {
		Options options = super.getOptions();
		options.addOption(Option.builder("p").longOpt("one-time-password")
				.desc("The one-time password for the source side")
				.required(false)
				.hasArg()
				.build());
		options.addOption(Option.builder("s").longOpt("server")
				.desc("UFTPD server address in the form host:port")
				.required(false)
				.hasArg()
				.build());
		options.addOption(Option.builder("B").longOpt("bytes")
				.desc("Byte range")
				.required(false)
				.hasArg()
				.build());
		return options;
	}

	@Override
	public void parseOptions(String[] args) throws ParseException {
		super.parseOptions(args);
		if(fileArgs.length<2){
			throw new IllegalArgumentException("Missing argument: "+getArgumentDescription());
		}
		onetimePassword = line.getOptionValue('p');
		uftpdAddress = line.getOptionValue('s');
		target = fileArgs[fileArgs.length-1];
		if (line.hasOption('B')) {
			String bytes = line.getOptionValue('B');
			if(bytes!=null)initRange(bytes);
		}
	}

	@Override
	protected void run(ClientFacade client) throws Exception {
		int len = fileArgs.length-1;
		for(int i=0; i<len;i++){
			String source = fileArgs[i];
			String target = this.target;
			try{
				rcp(source, target, client);
			}catch(Exception ex) {
				error(Log.createFaultMessage("", ex));
			}
		}
	}

	public void rcp(String source, String target, ClientFacade client) throws Exception {
		boolean remoteSource = ConnectionInfoManager.isRemote(source);
		boolean remoteTarget = ConnectionInfoManager.isRemote(target);
		boolean receive = !Boolean.parseBoolean(System.getenv("UFTP_RCP_USE_SEND_FILE"));
		if(!remoteSource && !remoteTarget){
			String error = String.format("Unable to handle [%s, %s] combination. "
					+ "At least one must be a URL.", source, target);
			throw new IllegalArgumentException(error);
		}
		if((onetimePassword==null || uftpdAddress==null) && !(remoteSource && remoteTarget)) {
			throw new IllegalArgumentException("One-time password and UFTPD address are required");
		}
		UFTPSessionClient sc = receive? client.doConnect(target) : client.doConnect(source);
		
		if(onetimePassword==null) {
			// authenticate the "other" UFTP side
			AuthResponse auth = remoteSource? client.authenticate(source) : client.authenticate(target);
			uftpdAddress = auth.serverHost+":"+auth.serverPort;
			onetimePassword = auth.secret;
		}
		if(remoteSource) {
			Map<String,String> sourceParams = client.getConnectionManager().extractConnectionParameters(source);
			source = sourceParams.get("path");
		}
		if(remoteTarget) {
			Map<String,String> targetParams = client.getConnectionManager().extractConnectionParameters(target);
			target = targetParams.get("path");
		}
		
		if(haveRange()){
			sc.sendRangeCommand(getOffset(), getLength());
		}
		String reply = receive ?
				sc.receiveFile(target, source, uftpdAddress, onetimePassword):
				sc.sendFile(source, target, uftpdAddress, onetimePassword);
		verbose(reply);
	}
	
}
