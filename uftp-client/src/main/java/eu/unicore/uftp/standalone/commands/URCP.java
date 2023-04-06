package eu.unicore.uftp.standalone.commands;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import eu.unicore.uftp.standalone.ClientFacade;

/**
 * server-server copy (remote copy)
 *
 * @author schuller
 */
public class URCP extends Command {

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
	}

	@Override
	protected void run(ClientFacade client) throws Exception {
		client.setVerbose(verbose);
		int len = fileArgs.length-1;
		boolean receive = !Boolean.parseBoolean(System.getenv("UFTP_RCP_USE_SEND_FILE"));
		for(int i=0; i<len;i++){
			String source = fileArgs[i];
			String target = this.target;
			client.rcp(source, target, onetimePassword, uftpdAddress, receive);
		}
	}

}
