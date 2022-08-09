package eu.unicore.uftp.client;

import java.net.InetAddress;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.dpc.ProtocolViolationException;
import eu.unicore.uftp.dpc.Utils;

public class ClientFactory {

	public static AbstractUFTPClient create(String[] args)throws Exception {
		Options options=ClientFactory.createOptions();
		CommandLineParser parser = new GnuParser();
		CommandLine line;
		try{
			line = parser.parse(options, args);
			if(line.hasOption("S")){
				return UFTPSessionClient.create(args);
			}
		}catch(ParseException pe){
			System.out.println("Unable to parse client parameters "+pe.getLocalizedMessage());
			System.exit(1);
		}
		return null;
	}

	public static boolean isSend(CommandLine line){
		boolean send=true;
		if(line.hasOption("s")) {
			send = true;			//send = we are the sender
		} else if (line.hasOption("r")){
			send = false;
		}
		return send;
	}

	/**
	 * configures the client using the number of connections, secret, encryption 
	 * key and buffer size from the commandline
	 * 
	 * @param client - the UFTP client
	 * @param line - the commandline
	 * @param logger
	 */
	public static void configureClient(AbstractUFTPClient client, CommandLine line, Logger logger){
		int numcons = Integer.parseInt(line.getOptionValue("n"));
		client.setNumConnections(numcons);

		client.setSecret(line.getOptionValue("x"));

		String keyBase64=line.getOptionValue("E");
		if(keyBase64!=null){
			client.setKey(Utils.decodeBase64(keyBase64));
		}

	}

	public static InetAddress[] getServers(CommandLine line, Logger logger){
		return Utils.parseInetAddresses(line.getOptionValue("l"), logger);
	}

	/**
	 * helper to create commandline options understood by UFTP clients
	 */
	@SuppressWarnings("static-access")
	public static Options createOptions(){
		Options options=new Options();

		options.addOption(
				OptionBuilder.withLongOpt("startSession")
				.withDescription("Connect to a UFTP session")
				.hasArg(false)
				.isRequired(false)
				.create("S")
				);
		
		options.addOption(
				OptionBuilder.withLongOpt("commandFile")
				.withDescription("(Session mode only) Commands file name")
				.withArgName("Cmdfile")
				.hasArg()
				.isRequired(true)
				.create("c")
				);
		
		options.addOption(
				OptionBuilder.withLongOpt("send")
				.withDescription("Send data")
				.withArgName("Send")
				.isRequired(false)
				.create("s")
				);
		
		options.addOption(
				OptionBuilder.withLongOpt("receive")
				.withDescription("Receive data")
				.withArgName("Receive")
				.isRequired(false)
				.create("r")
				);
		options.addOption(
				OptionBuilder.withLongOpt("listen-host")
				.withDescription("Hostname of the server socket")
				.withArgName("Server host")
				.hasArg()
				.isRequired(true)
				.create("l")
				);
		
		options.addOption(
				OptionBuilder.withLongOpt("listen-port")
				.withDescription("Port of the server socket")
				.withArgName("Server port")
				.hasArg()
				.isRequired(true)
				.create("L")
				);
		
		options.addOption(
				OptionBuilder.withLongOpt("secret")
				.withDescription("Authorisation secret")
				.withArgName("Secret")
				.hasArg()
				.isRequired(true)
				.create("x")
				);
		
		options.addOption(
				OptionBuilder.withLongOpt("streams")
				.withDescription("Number of streams")
				.withArgName("Streams")
				.hasArg()
				.isRequired(false)
				.create("n")
				);
		
		options.addOption(
				OptionBuilder.withLongOpt("encryption-key")
				.withDescription("Encryption key, Base64-encoded")
				.isRequired(false)
				.withArgName("base64Key")
				.hasArg()
				.create("E")
				);
		
		options.addOption(
				OptionBuilder.withLongOpt("buffersize")
				.withDescription("Buffer size in kbytes for reading/writing files (default 128)")
				.withArgName("bufferSize")
				.hasArg()
				.isRequired(false)
				.create("b")
				);
		
		options.addOption(
				OptionBuilder.withLongOpt("file")
				.withDescription("Local file name")
				.withArgName("File name")
				.hasArg()
				.isRequired(false)
				.create("f")
				);
		
		options.addOption(
				OptionBuilder.withLongOpt("append")
				.withDescription("Append to an existing file")
				.isRequired(false)
				.create("a")
				);
		
		options.addOption(
				OptionBuilder.withLongOpt("compress")
				.withDescription("Compress data")
				.isRequired(false)
				.create("z")
				);
		
		return options;
	}

	public static void printUsage(Options options){
		HelpFormatter formatter = new HelpFormatter();
		String syntax="UFTPClient [OPTIONS]"+System.getProperty("line.separator");
		formatter.printHelp(syntax, options);
	}

	 public static void main(String[] args) throws Exception {
		 try {
			 AbstractUFTPClient client = ClientFactory.create(args);
			 client.run();
			 System.exit(0);
		 } catch (Exception ex) {
			 System.err.println(Utils.createFaultMessage("Error running UFTP client", ex));
			 if (ex.getCause() instanceof ProtocolViolationException) {
				 System.err.println();
				 System.err.println("Please check hostname and port of the remote server!");
			 }
			 System.exit(1);
		 }
	 }

}
