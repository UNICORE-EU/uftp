package eu.unicore.uftp.standalone.commands;

import java.io.File;
import java.io.IOException;
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
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.ConnectionInfoManager;
import eu.unicore.uftp.standalone.UFTPClientFactory;
import eu.unicore.uftp.standalone.oidc.OIDCAgentAuth;
import eu.unicore.uftp.standalone.ssh.SSHAgent;
import eu.unicore.uftp.standalone.ssh.SshKeyHandler;
import eu.unicore.uftp.standalone.util.ConsoleUtils;

public abstract class BaseCommand implements ICommand {

	
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
	
	@SuppressWarnings("static-access")
	protected Options getOptions() {
		Options options = new Options();

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
				username = System.getenv(UFTP_USER);
				if(username==null)username = System.getProperty("user.name");
			}
			if (line.hasOption('i')){
				sshIdentity = line.getOptionValue('i');
			}
		}
	}

	protected void setOptions(ClientFacade client){
		client.setGroup(group);
	}

	protected abstract void run(ClientFacade facade) throws Exception;

	public void runCommand() throws Exception {
		ConnectionInfoManager cim = new ConnectionInfoManager(getAuthData());
		ClientFacade facade = new ClientFacade(cim, new UFTPClientFactory());
		setOptions(facade);
		run(facade);
		facade.disconnect();
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

		if(sshIdentity==null){
			File sshDir = new File(System.getProperty("user.home"),".ssh");
			if(sshDir.exists()){
				String[] opts = new String[] {"id_rsa", "id_ed25519", "id_dsa"};
				for(String o: opts) {
					keyFile = new File(sshDir, o);
					if(keyFile.exists())break;
				}
				if(!keyFile.exists() && !haveAgent){
					throw new IOException("No private key recognised in "+sshDir.getAbsolutePath()
					+" and no SSH agent available. Please use the --identity option!");
				}
			}
			else {
				if(!haveAgent){
					throw new IOException("No ssh key found in "+sshDir.getAbsolutePath());
				}
			}
		}
		else {
			keyFile = new File(sshIdentity);
			if(!haveAgent && !keyFile.exists()){
				throw new IOException("Private key file " + sshIdentity + " does not exist.");
			}
		}
		if(verbose){
			if(keyFile!=null){
				System.err.println("Using SSH key <"+keyFile.getAbsolutePath()+">");
			}
			if(haveAgent) {
				System.err.println("Using SSH agent");
			}
		}
		SshKeyHandler ssh = new SshKeyHandler(keyFile, username, token);
		ssh.setVerbose(verbose);
		if(haveAgent && sshIdentity!=null) {
			ssh.selectIdentity();
		}
		return ssh.getAuthData();
	}


}
