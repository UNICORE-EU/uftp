package eu.unicore.uftp.standalone.commands;

import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.lists.FileCrawler.RecursivePolicy;

/**
 * tell the server to compute checksum(s) for remote file(s)
 * 
 * @author schuller
 */
public class Hash extends BaseUFTPCommand {

	protected boolean recurse = false;

	protected String hashAlgorithm = null;

	@Override
	public String getName() {
		return "checksum";
	}

	@Override
	public String getArgumentDescription() {
		return "https://<server-host>:<server-port>/rest/auth/SERVER-NAME:file"
				;
	}
	
	public String getSynopsis(){
		return "Compute checksums for remote file(s)";
	}
	
	@SuppressWarnings("static-access")
	protected Options getOptions() {
		Options options = super.getOptions();
		options.addOption(
				OptionBuilder.withLongOpt("recurse")
				.withDescription("Recurse into subdirectories, if applicable")
				.isRequired(false)
				.create("r")
				);
		options.addOption(
				OptionBuilder.withLongOpt("algorithm")
				.withDescription("Hash algorithm to use. One of: MD5(default), SHA-1, SHA-256, SHA-512")
				.isRequired(false)
				.hasArg()
				.create("a")
				);
		return options;
	}

	@Override
	public void parseOptions(String[] args) throws ParseException {
		super.parseOptions(args);
		recurse = line.hasOption('r');
		if (line.hasOption('a')) {
			hashAlgorithm = line.getOptionValue('a');
		}
	}
	
	@Override
	protected void run(ClientFacade client) throws Exception {
		client.setVerbose(verbose);
		String fileSpec = fileArgs[0];
		RecursivePolicy policy = recurse ? RecursivePolicy.RECURSIVE:RecursivePolicy.NONRECURSIVE;
		client.checksum(fileSpec, hashAlgorithm, policy);
	}
	

	
}
