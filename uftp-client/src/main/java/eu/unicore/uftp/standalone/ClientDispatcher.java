package eu.unicore.uftp.standalone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import eu.unicore.uftp.standalone.commands.ICommand;

/**
 * Standalone client entry point
 */
public final class ClientDispatcher {

	private final Map<String, ICommand> cmds = new HashMap<String, ICommand>();

	public ClientDispatcher(){
		findCommands();
	}

	protected void findCommands(){
		for(ICommand c: ServiceLoader.load(ICommand.class)){
			cmds.put(c.getName().toLowerCase(), c);
		}
	}

	Collection<ICommand>getCommands(){
		return Collections.unmodifiableCollection(cmds.values());
	}


	/**
	 * main entry point for all commands
	 *
	 * @param args - first arg is expected to be the command name
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {        
		int returnCode = new ClientDispatcher().dispatch(args);
		System.exit(returnCode);
	}

	public int dispatch(String[] args) throws Exception {
		String cmdName = extractCommand(args);
		
		if(cmdName == null || isHelp(cmdName)){
			printUsage();
			return 0;
		}
		
		if(isVersion(cmdName)){
			printVersion();
			return 0;
		}
		ICommand cmd = null;
		cmdName = cmdName.toLowerCase();
		for(String name: cmds.keySet()) {
			if(name.startsWith(cmdName)) {
				cmd = cmds.get(name);
			}
		}
		if(cmd==null){
			System.err.println("Unknown command: "+cmdName);
			return 1;
		}

		String[] cmdArgs = Arrays.copyOfRange(args, 1, args.length);
		if(cmdArgs.length==0 || isHelp(cmdArgs[0])){
			cmd.printUsage();
			return 0;
		}

		cmd.parseOptions(cmdArgs);
		boolean OK = cmd.runCommand();
		
		return OK ? 0 : 1;
	}

	public void printUsage() {
		String version = getVersion();
		System.err.println("UFTP Client " + version);
		System.err.println("Usage: uftp <command> [OPTIONS] <args>");
		System.err.println("The following commands are available:");
		List<ICommand>c=new ArrayList<ICommand>();
		c.addAll(cmds.values());
		Collections.sort(c, new Comparator<ICommand>() {
			@Override
			public int compare(ICommand o1, ICommand o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});
		for (ICommand entry : c) {
			System.err.printf(" %-20s - %s", entry.getName(), entry.getSynopsis());
			System.err.println();
		}
		System.err.println("Enter 'uftp <command> -h' for help on a particular command.");
	}
	
	public static void printVersion() {
		System.err.println("UFTP Client " + getVersion());
		System.err.println(System.getProperty("java.vm.name")+" "+System.getProperty("java.vm.version"));
		System.err.println("OS: "+System.getProperty("os.name")+" "+System.getProperty("os.version"));
	}
	
	public static String getVersion(){
		String v=ClientDispatcher.class.getPackage().getImplementationVersion();
		if(v==null)v="(DEVELOPMENT version)";
		return v+", https://www.unicore.eu";
	}
	
	private static String[] help = {"-?","?","-h","--help","help"};

	private static String[] version = {"-V","--version","-version","version"};

	private boolean isHelp(String arg) {
		for(String h: help){
			if(arg.toLowerCase().startsWith(h))
				return true;
		}
		return false;
	}

	private boolean isVersion(String arg) {
		for(String v: version){
			if(arg.startsWith(v))
				return true;
		}
		return false;
	}
	
	private String extractCommand(String[] args) {
		if (args.length > 0 && !args[0].isEmpty()) {
			return args[0];
		}
		return null;
	}

}
