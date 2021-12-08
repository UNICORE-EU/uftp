package eu.unicore.uftp.standalone.commands;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.util.UnitParser;

/**
 * handles options related to data connections 
 * (like number of TCP streams per data connection)
 * 
 * @author schuller
 */
public abstract class DataTransferCommand extends Command {

	protected int streams = 1;
	
	protected long bandwithLimit = -1;

	protected boolean compress;
	protected boolean encrypt;
	protected byte[] key;

	@SuppressWarnings("static-access")
	protected Options getOptions() {
		Options options = super.getOptions();
		options.addOption(
				OptionBuilder.withLongOpt("streams")
				.withDescription("Number of tcp streams per connection/thread")
				.withArgName("Streams")
				.hasArg()
				.isRequired(false)
				.create("n")
				);
		options.addOption(
				OptionBuilder.withLongOpt("encrypt")
				.withDescription("Encrypt data connections")
				.isRequired(false)
				.create("E")
				);
		options.addOption(
				OptionBuilder.withLongOpt("compress")
				.withDescription("Compress data for transfer")
				.isRequired(false)
				.create("C")
				);
		options.addOption(
				OptionBuilder.withLongOpt("bandwithlimit")
				.withDescription("Limit bandwith per FTP connection (bytes per second)")
				.withArgName("bandwithLimit")
				.hasArg()
				.isRequired(false)
				.create("K")
				);
		return options;
	}
	
	@Override
	public void parseOptions(String[] args) throws ParseException {
		super.parseOptions(args);

		if (line.hasOption('n')) {
			streams = Integer.parseInt(line.getOptionValue('n'));
		}

		if (line.hasOption('K')) {
			UnitParser up = UnitParser.getCapacitiesParser(0);
			bandwithLimit = (long)up.getDoubleValue(line.getOptionValue('K'));
			if(verbose) {
				System.err.println("LIMITING bandwidth per thread to "
						+up.getHumanReadable(bandwithLimit)+"B/s");
			}
		}

		if (line.hasOption('E')) {
			encrypt = true;
			try{
				key=Utils.createKey();
			}catch(Exception ex){
				encrypt = false;
				System.err.println("WARN: cannot setup encryption: "+ex);
			}
		}
		
		compress = line.hasOption('C');

	}
	
	protected String getRemoteURLExample1(){
		return "* https://<auth_addr>/rest/auth/<SERVER>:<file_path>";
	}
	protected String getRemoteURLExample2(){
		return "* https://<ux_addr>/rest/core/storages/<STORAGE>:<file_path>";
	}

	/**
	 * print help
	 */
	@Override
	public void printUsage() {
		HelpFormatter formatter = new HelpFormatter();
		String newLine=System.getProperty("line.separator");
		
		StringBuilder s = new StringBuilder();
		s.append(getName()).append(" [OPTIONS] ").append(getArgumentDescription()).append(newLine);
		s.append(getSynopsis()).append(newLine);
		s.append("Remote URLs are built as follows:").append(newLine);
		s.append(getRemoteURLExample1()).append(newLine);
		s.append(getRemoteURLExample2());
		
		Options def=getOptions();
		formatter.setSyntaxPrefix("Usage: ");
		formatter.printHelp(s.toString(), def);
	}

	@Override
	protected void setOptions(ClientFacade client){
		super.setOptions(client);
		client.setStreams(streams);
		if(encrypt)client.setEncryptionKey(key);
		client.setCompress(compress);
		client.setBandwithLimit(bandwithLimit);
	}
	
}
