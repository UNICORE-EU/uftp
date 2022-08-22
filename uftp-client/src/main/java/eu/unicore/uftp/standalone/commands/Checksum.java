package eu.unicore.uftp.standalone.commands;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.lists.FileCrawler.RecursivePolicy;
import eu.unicore.uftp.standalone.util.UnitParser;

/**
 * tell the server to compute checksum(s) for remote file(s)
 * 
 * @author schuller
 */
public class Checksum extends Command {

	protected boolean recurse = false;

	protected String hashAlgorithm = null;

	//index of first byte to process
	protected Long startByte;

	//index of last byte to process
	protected Long endByte;

	@Override
	public String getName() {
		return "checksum";
	}

	@Override
	public String getArgumentDescription() {
		return "https://<server-host>:<server-port>/rest/auth/SERVER-NAME:file";
	}
	
	public String getSynopsis(){
		return "Compute checksums for remote file(s)";
	}
	
	protected Options getOptions() {
		Options options = super.getOptions();
		options.addOption(Option.builder("r").longOpt("recurse")
				.desc("Recurse into subdirectories, if applicable")
				.required(false)
				.build());
		options.addOption(Option.builder("a").longOpt("algorithm")
				.desc("Hash algorithm to use. One of: MD5(default), SHA-1, SHA-256, SHA-512")
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
		recurse = line.hasOption('r');
		if (line.hasOption('a')) {
			hashAlgorithm = line.getOptionValue('a');
		}
		if (line.hasOption('B')) {
			String bytes = line.getOptionValue('B');
			if(bytes!=null)initRange(bytes);
		}
		if(fileArgs.length==0) {
			throw new IllegalArgumentException("Missing argument: "+getArgumentDescription());
		}
	}
	
	@Override
	protected void run(ClientFacade client) throws Exception {
		String fileSpec = fileArgs[0];
		RecursivePolicy policy = recurse ? RecursivePolicy.RECURSIVE:RecursivePolicy.NONRECURSIVE;
		client.checksum(fileSpec, hashAlgorithm, policy);
	}

	private void initRange(String bytes){
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
