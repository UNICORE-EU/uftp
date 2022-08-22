package eu.unicore.uftp.standalone.commands;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;

import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.lists.FileCrawler.RecursivePolicy;

public class URM extends Command {

	private boolean quiet = false;

	private boolean recurse = false;

	protected Options getOptions() {
		Options options = super.getOptions();
		options.addOption(Option.builder("q").longOpt("quiet")
				.desc("Quiet mode, don't ask for confirmation")
				.required(false)
				.build());
		options.addOption(Option.builder("r").longOpt("recurse")
				.desc("Delete (sub)directories, if applicable")
				.required(false)
				.build());
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
		LineReader r = null;
		try{
			r = LineReaderBuilder.builder().build();
			String line = r.readLine("This will delete a remote file/directory '"
					+file+"', are you sure? [Y]");
			return line.length()==0  || line.startsWith("y") || line.startsWith("Y");
		}finally{
			try{
				if(r!=null) r.getTerminal().close();
			}catch(Exception e) {}
		}
	}
	
}
