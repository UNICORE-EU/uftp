package eu.unicore.uftp.client;

import java.net.InetAddress;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.dpc.ProtocolViolationException;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.util.Log;

public class ClientFactory {

	public static Runnable create(String[] args)throws Exception {
		Options options=ClientFactory.createOptions();
		CommandLineParser parser = new DefaultParser();
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
	public static Options createOptions(){
		Options options=new Options();
		options.addOption(Option.builder("S").longOpt("startSession")
				.desc("Connect to a UFTP session")
				.required(false)
				.build());
		options.addOption(Option.builder("c").longOpt("commandFile")
				.desc("(Session mode only) Commands file name")
				.required(true)
				.hasArg()
				.build());
		options.addOption(Option.builder("s").longOpt("send")
				.desc("Send data")
				.required(false)
				.build());
		options.addOption(Option.builder("r").longOpt("receive")
				.desc("Receive data")
				.required(false)
				.build());
		options.addOption(Option.builder("l").longOpt("listen-host")
				.desc("Hostname of the server socket")
				.required(true)
				.hasArg().argName("Server host")
				.build());
		options.addOption(Option.builder("L").longOpt("listen-port")
				.desc("Post of the server socket")
				.required(true)
				.hasArg().argName("Server port")
				.build());
		options.addOption(Option.builder("x").longOpt("secret")
				.desc("Authorisation secret")
				.required(true)
				.hasArg().argName("Secret")
				.build());
		options.addOption(Option.builder("n").longOpt("streams")
				.desc("Number of streams")
				.required(false)
				.hasArg().argName("Streams")
				.build());
		options.addOption(Option.builder("E").longOpt("encryption-key")
				.desc("Encryption key, Base64-encoded")
				.required(false)
				.hasArg().argName("base64Key")
				.build());	
		options.addOption(Option.builder("b").longOpt("buffersize")
				.desc("Buffer size in kbytes for reading/writing files (default 128)")
				.required(false)
				.hasArg().argName("base64Key")
				.build());
		options.addOption(Option.builder("f").longOpt("file")
				.desc("Local file name")
				.required(false)
				.hasArg().argName("File name")
				.build());
		options.addOption(Option.builder("a").longOpt("append")
				.desc("Append to an existing file")
				.required(false)
				.build());
		options.addOption(Option.builder("z").longOpt("compress")
				.desc("Compress data for transfer")
				.required(false)
				.build());
		return options;
	}

	public static void printUsage(Options options){
		HelpFormatter formatter = new HelpFormatter();
		String syntax="UFTPClient [OPTIONS]"+System.getProperty("line.separator");
		formatter.printHelp(syntax, options);
	}

	 public static void main(String[] args) throws Exception {
		 try {
			 Runnable client = ClientFactory.create(args);
			 client.run();
			 System.exit(0);
		 } catch (Exception ex) {
			 System.err.println(Log.createFaultMessage("Error running UFTP client", ex));
			 if (ex.getCause() instanceof ProtocolViolationException) {
				 System.err.println();
				 System.err.println("Please check hostname and port of the remote server!");
			 }
			 System.exit(1);
		 }
	 }

}
