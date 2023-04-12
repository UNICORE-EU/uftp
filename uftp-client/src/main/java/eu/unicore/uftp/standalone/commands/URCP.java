package eu.unicore.uftp.standalone.commands;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.util.RangeMode;
import eu.unicore.uftp.standalone.util.UnitParser;

/**
 * server-server copy (remote copy)
 *
 * @author schuller
 */
public class URCP extends Command {

	protected String target;

	private String onetimePassword;
	private String uftpdAddress;

	//index of first byte to process
	protected Long startByte;
	//index of last byte to process
	protected Long endByte;

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
		client.setVerbose(verbose);
		int len = fileArgs.length-1;
		boolean receive = !Boolean.parseBoolean(System.getenv("UFTP_RCP_USE_SEND_FILE"));
		for(int i=0; i<len;i++){
			String source = fileArgs[i];
			String target = this.target;
			if(startByte!=null){
				client.setRange(startByte, endByte, RangeMode.READ_WRITE);
			}
			client.rcp(source, target, onetimePassword, uftpdAddress, receive);
			client.resetRange();
		}
	}

	protected void initRange(String bytes){
		String[]tokens=bytes.split("-");
		try{
			if(tokens.length>1) {
				String start=tokens[0];
				String end=tokens[1];
				if(start.length()>0){
					startByte = (long)(UnitParser.getCapacitiesParser(0).getDoubleValue(start));
					endByte=Long.MAX_VALUE;
				}
				if(end.length()>0){
					endByte=(long)(UnitParser.getCapacitiesParser(0).getDoubleValue(end));
					if(startByte==null){
						startByte=Long.valueOf(0l);
					}
				}
			}
			else {
				String end=tokens[0];
				endByte=(long)(UnitParser.getCapacitiesParser(0).getDoubleValue(end));
				startByte=Long.valueOf(0l);
			}
		}catch(Exception e){
			throw new IllegalArgumentException("Could not parse byte range "+bytes);
		}
	}

}
