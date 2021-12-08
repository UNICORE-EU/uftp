package eu.unicore.uftp.standalone.commands;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import eu.unicore.uftp.authserver.authenticate.AuthData;
import eu.unicore.uftp.authserver.authenticate.UsernamePassword;
import eu.unicore.uftp.authserver.authenticate.sshkey.SSHKey;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.standalone.ClientDispatcher;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.ConnectionInfoManager;
import eu.unicore.uftp.standalone.UFTPClientFactory;
import eu.unicore.uftp.standalone.oidc.OIDCAgentAuth;
import eu.unicore.uftp.standalone.ssh.SSHAgent;
import eu.unicore.uftp.standalone.ssh.SshKeyHandler;
import eu.unicore.uftp.standalone.util.ConsoleUtils;
import eu.unicore.util.Log;

/***
 * handles options related to authentication
 * 
 * @author schuller
 */
public abstract class Command implements ICommand {

	/**
	 * environment variable defining the UFTP user name
	 */
	public static String UFTP_USER = "UFTP_USER";
	
	protected String[] fileArgs;

	protected CommandLine line;

	protected String username, password, group, authheader;
	protected boolean enableSSH = true;
	protected String sshIdentity = null;
	protected Integer agentIdentityIndex = null;

	protected boolean queryPassword = false;

	protected boolean verbose = false;

	protected String oidcAccount = null;

	protected String clientIP;

	@SuppressWarnings("static-access")
	protected Options getOptions() {
		Options options = new Options();

		options.addOption(
				OptionBuilder.withLongOpt("help")
				.withDescription("Show help / usage information")
				.isRequired(false)
				.create("h")
				);

		options.addOption(
				OptionBuilder.withLongOpt("user")
				.withDescription("Use username[:password] for authentication")
				.isRequired(false)
				.hasArg()
				.create("u")
				);

		options.addOption(
				OptionBuilder.withLongOpt("group")
				.withDescription("Requested group membership to be used")
				.isRequired(false)
				.hasArg()
				.create("g")
				);

		options.addOption(
				OptionBuilder.withLongOpt("auth")
				.withDescription("Authorization header value for authentication")
				.isRequired(false)
				.hasArg()
				.create("A")
				);

		options.addOption(
				OptionBuilder.withLongOpt("oidc-agent")
				.withDescription("Use oidc-agent with the specified account")
				.isRequired(false)
				.hasArg()
				.create("O")
				);

		
		options.addOption(
				OptionBuilder.withLongOpt("password")
				.withDescription("Interactively query for a missing password")
				.isRequired(false)
				.create("P")
				);

		options.addOption(
				OptionBuilder.withLongOpt("identity")
				.withDescription("Identity file (private key) to use with SSH auth")
				.isRequired(false)
				.hasArg()
				.create("i")
				);

		options.addOption(
				OptionBuilder.withLongOpt("client")
				.withDescription("Client IP address: AUTO|ALL|address-list")
				.withArgName("client")
				.hasArg()
				.isRequired(false)
				.create("I")
				);

		options.addOption(
				OptionBuilder.withLongOpt("verbose")
				.withDescription("Be verbose")
				.isRequired(false)
				.create("v")
				);
		return options;
	}

	@Override
	public void parseOptions(String[] args) throws ParseException {
		Options options = getOptions();
		CommandLineParser parser = new GnuParser();
		line = parser.parse(options, args);
		fileArgs = line.getArgs();
		if (line.hasOption('v')){
			verbose = true;
			System.err.println("Verbose mode");
		}
		if (line.hasOption('u')){
			String up = line.getOptionValue('u');
			String[] tokens = up!=null? up.split(":",2) : null;
			username = tokens!=null ? tokens[0] : null;
			password = tokens!=null && tokens.length>1 ? tokens[1] : null;
			if(password == null){
				if(line.hasOption('P')){
					password = ConsoleUtils.readPassword("Password:");
					enableSSH = false;
				}
			}
			if(password!=null)enableSSH=false;
		}
		if (line.hasOption('g')){
			group = line.getOptionValue('g');
		}
		if (line.hasOption('A')){
			if(line.hasOption('u') || line.hasOption('O')){
				throw new IllegalArgumentException("Only one of '-u', '-A' or 'O' can be used!");
			}
			authheader = line.getOptionValue('A');
			enableSSH = false;
		}
		if (line.hasOption('O')){
			if(line.hasOption('u') || line.hasOption('A')){
				throw new IllegalArgumentException("Only one of '-u', '-A' or 'O' can be used!");
			}
			oidcAccount = line.getOptionValue('O');
			enableSSH = false;
		}
		
		if(enableSSH){
			if(!line.hasOption('u')) {
				username = Utils.getProperty(UFTP_USER, null);
				if(username==null)username = System.getProperty("user.name");
			}
			if (line.hasOption('i')){
				sshIdentity = line.getOptionValue('i');
			}
		}

		if(line.hasOption('I')){
			setupClientIPMode(line.getOptionValue('I'));
		}
	}

	protected void setOptions(ClientFacade client){
		client.setVerbose(verbose);
		client.setGroup(group);
		client.setClientIP(clientIP);
	}

	protected abstract void run(ClientFacade facade) throws Exception;

	public boolean runCommand() throws Exception {
		boolean OK = true;
		if(line.hasOption("h")) {
			printUsage();
			return OK;
		}
		try{
			ConnectionInfoManager cim = new ConnectionInfoManager(getAuthData());
			ClientFacade facade = new ClientFacade(cim, new UFTPClientFactory());
			setOptions(facade);
			run(facade);
			facade.disconnect();
		}catch(Exception ex) {
			if(verbose) {
				ex.printStackTrace();
				System.err.println();
			}
			System.err.println(Log.createFaultMessage("ERROR", ex));
			OK = false;
		}
		return OK;
	}
	
	/**
	 * print help
	 */
	@Override
	public void printUsage() {
		System.err.println("UFTP Client " + ClientDispatcher.getVersion());

		HelpFormatter formatter = new HelpFormatter();
		String newLine=System.getProperty("line.separator");

		StringBuilder s = new StringBuilder();
		s.append(getName()).append(" [OPTIONS] ").append(getArgumentDescription()).append(newLine);
		s.append(getSynopsis()).append(newLine);

		Options def=getOptions();
		formatter.setSyntaxPrefix("Usage: ");
		formatter.printHelp(s.toString(), def);
	}


	protected AuthData getAuthData() throws Exception {
		if(enableSSH){
			return getSSHAuthData();
		}
		
		if(authheader!=null){
			return new AuthData() {

				@Override
				public String getType() {
					return "Header";
				}

				@Override
				public Map<String, String> getHttpHeaders() {
					Map<String,String> m = new HashMap<>();
					m.put("Authorization", authheader);
					return m;
				}
			};
		}
		else if(oidcAccount!=null) {
			return new OIDCAgentAuth(oidcAccount);
		}
		else{
			return getUPAuthData();
		}

	}

	protected AuthData getUPAuthData(){
		return new UsernamePassword(username, password);
	}

	protected SSHKey getSSHAuthData() throws Exception {
		String token = String.valueOf(System.currentTimeMillis());
		return getSSHAuthData(token);
	}

	protected SSHKey getSSHAuthData(String token) throws Exception {
		File keyFile = null;
		boolean haveAgent = SSHAgent.isAgentAvailable();
		int numKeys = 0;
		
		File[] dirs = new File[] {
				 new File(System.getProperty("user.home"),".uftp"),
				 new File(System.getProperty("user.home"),".ssh")
		};
		
		if(sshIdentity==null){
			FilenameFilter ff = new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.startsWith("id_") && !name.endsWith(".pub");
				}
			};
			outer: for(File keysDir: dirs) {
				if(keysDir.exists()){
					File[] ops = keysDir.listFiles(ff);
					numKeys+=ops.length;
					for(File key: keysDir.listFiles(ff)) {
						if(!key.isDirectory() && key.canRead()) {
							keyFile = key;
							break outer;
						};
					}
				}
			}
			if(keyFile == null || !keyFile.exists()){
				throw new IOException("No useable private key found in "+Arrays.asList(dirs)+". Please use the --identity option!");
			}
			if(numKeys>1 && verbose) {
				System.err.println("NOTE: more than one useable key found -"
						+ " you might want to use '--identity <path_to_private_key>'");
			}
		}
		else {
			keyFile = new File(sshIdentity);
			if(!haveAgent && !keyFile.exists()){
				throw new IOException("Private key file " + sshIdentity + " does not exist.");
			}
		}
		if(verbose){
			if(haveAgent) {
				System.err.println("Using SSH agent");
			}
			if(keyFile!=null){
				System.err.println("Using SSH key <"+keyFile.getAbsolutePath()+">");
			}
		}
		SshKeyHandler ssh = new SshKeyHandler(keyFile, username, token);
		ssh.setVerbose(verbose);
		if(haveAgent && sshIdentity!=null) {
			ssh.selectIdentity();
		}
		return ssh.getAuthData();
	}

	private void setupClientIPMode(String ip){
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

}
