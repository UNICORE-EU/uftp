package eu.unicore.uftp.standalone.commands;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.util.UnitParser;
import eu.unicore.util.Log;

public abstract class BaseUFTPCommand extends BaseCommand {

	protected int streams = 1;
	
	protected long bandwithLimit = -1;

	protected boolean compress;
	protected boolean encrypt;
	protected byte[] key;
	protected String clientIP;
	
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
		options.addOption(
				OptionBuilder.withLongOpt("client")
				.withDescription("Client IP address: AUTO|ALL|address-list")
				.withArgName("client")
				.hasArg()
				.isRequired(false)
				.create("I")
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
		
		if(line.hasOption('I')){
			setupClientIPMode(line.getOptionValue('I'));
		}
	}
	
	protected void setupClientIPMode(String ip){
		if("AUTO".equals(ip))return;
		else if("ALL".equals(ip)){
			try{
				StringBuilder sb = new StringBuilder();
				Enumeration<NetworkInterface>iter = NetworkInterface.getNetworkInterfaces();
				while(iter.hasMoreElements()){
					NetworkInterface ni = iter.nextElement();
					Enumeration<InetAddress> addresses = ni.getInetAddresses();
					while(addresses.hasMoreElements()){
						InetAddress ia = addresses.nextElement();
						if(sb.length()>0)sb.append(",");
						sb.append(ia.getHostAddress());
					}
				}
				clientIP = sb.toString();
			}catch(Exception e){
				System.err.println(Log.createFaultMessage("WARNING:", e));
			}
			
		}
		else {
			clientIP=ip;
		}
		
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
	public void setOptions(ClientFacade client){
		super.setOptions(client);
		client.setStreams(streams);
		client.setClientIP(clientIP);
		if(encrypt)client.setEncryptionKey(key);
		client.setCompress(compress);
	}
	
}
