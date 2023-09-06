package eu.unicore.uftp.standalone.commands;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;

import eu.unicore.uftp.client.FileInfo;
import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.standalone.ClientFacade;

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

	@Override
	protected void run(ClientFacade client) throws Exception {
		if(fileArgs.length==0) {
			throw new IllegalArgumentException("Missing argument: "+getArgumentDescription());
		}
		UFTPSessionClient sc = null;
		for(String fileArg: fileArgs) {
			sc = client.checkReInit(fileArg, sc);
			String path = client.getConnectionManager().getPath();
			doRM(path, sc);
		}
	}
	
	private void doRM(String file, UFTPSessionClient sc) throws Exception {
		if(!quiet && !confirm(file)){
			return;
		}
		FileInfo stat = sc.stat(file);
		if(stat.isDirectory()){
			if(!recurse) {
				error("uftp rm: cannot remove '{}': Is a directory", file);
				return;
			}
			// TODO session client needs to use the 'RMD' FTP command
			sc.rm(file);
		}
		else sc.rm(file);
	}
		
	@Override
	public String getArgumentDescription() {
		return "<remote_file> ...";	
	}

	@Override
	public String getSynopsis() {
		return "Deletes remote files or directories.";
	}

	private boolean always = false;

	protected boolean confirm(String file){
		LineReader r = null;
		try {
			r = LineReaderBuilder.builder().build();
			String line = r.readLine("This will delete a remote file/directory '"
					+file+"', are you sure? [Y/N/A]");
			if(always || line.startsWith("A") || line.startsWith("a")){
				always = true;
				return true;
			}
			return line.length()==0  || line.startsWith("y") || line.startsWith("Y");
		}finally{
			try{
				if(r!=null) r.getTerminal().close();
			}catch(Exception e) {}
		}
	}

}
