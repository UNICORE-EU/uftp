package eu.unicore.uftp.standalone.commands;

import java.io.IOException;

import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.lists.FileCrawler.RecursivePolicy;
import jline.console.ConsoleReader;

public class URM extends Command {

	private boolean quiet = false;

	private boolean recurse = false;

	@SuppressWarnings("static-access")
	protected Options getOptions() {
		Options options = super.getOptions();
		options.addOption(OptionBuilder.withLongOpt("quiet")
				.withDescription("do not ask for confirmation")
				.isRequired(false)
				.create("q")
				);
		options.addOption(
				OptionBuilder.withLongOpt("recurse")
				.withDescription("Delete (sub)directories, if applicable")
				.isRequired(false)
				.create("r")
				);
		return options;
	}
	
	@Override
	public void parseOptions(String[] args) throws ParseException {
		super.parseOptions(args);
		quiet = line.hasOption('q');
		recurse = line.hasOption('r');
	}
	
	@Override
	public String getName() {
		return "rm";
	}
	
	ClientFacade client;

	@Override
	protected void run(ClientFacade client) throws Exception {
		this.client = client;
		int len = fileArgs.length;
		if(len==0) {
			throw new IllegalArgumentException("Missing argument: "+getArgumentDescription());
		}
		for(int i=0; i<len;i++){
			doRM(fileArgs[i]);
		}
	}
	
	private void doRM(String file) throws Exception {
		if(!quiet && !confirm(file)){
			return;
		}
		RecursivePolicy policy = recurse ? RecursivePolicy.RECURSIVE : RecursivePolicy.NONRECURSIVE;
		client.rm(file, policy);
	}
		
	@Override
	public String getArgumentDescription() {
		return "<remote_file> ...";	
	}

	@Override
	public String getSynopsis() {
		return "Deletes remote files or directories.";
	}

	protected boolean confirm(String file){
		ConsoleReader r = null;
		try{
			r = new ConsoleReader();
			String line=r.readLine("This will delete a remote file '"+file+"', are you sure? [Y]");
			return line.length()==0  || line.startsWith("y") || line.startsWith("Y");
		}catch(IOException igored){}
		finally{
			if(r!=null) r.shutdown();
		}
		
		return false;
	}
}
