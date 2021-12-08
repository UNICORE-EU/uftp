package eu.unicore.uftp.standalone.commands;

import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.ConnectionInfoManager;
import eu.unicore.uftp.standalone.lists.FileCrawler.RecursivePolicy;
import eu.unicore.uftp.standalone.util.RangeMode;
import eu.unicore.uftp.standalone.util.UnitParser;

public class UCP extends DataTransferCommand {

	//index of first byte to process
	protected Long startByte;

	//index of last byte to process
	protected Long endByte;

	protected RangeMode mode = RangeMode.READ;

	protected boolean resume = false;

	protected boolean isUpload = false;

	protected String target;

	// whether multiple files are to be transferred
	protected boolean multiple = false;

	protected boolean recurse = false;

	protected boolean preserve = false;

	protected int numClients = 1;

	protected long splitThreshold = ClientFacade.DEFAULT_THRESHOLD;

	protected boolean archiveMode = false;

	@Override
	public String getName() {
		return "cp";
	}

	@Override
	public String getArgumentDescription() {
		return "<source> [<source> ...] <target>";
	}

	@Override
	public String getSynopsis(){
		return "Copy file(s). Wildcards '*' are supported.";
	}

	@SuppressWarnings("static-access")
	protected Options getOptions() {
		Options options = super.getOptions();
		options.addOption(
				OptionBuilder.withLongOpt("bytes")
				.withDescription("Byte range")
				.withArgName("Range")
				.hasArg()
				.isRequired(false)
				.create("B")
				);
		options.addOption(
				OptionBuilder.withLongOpt("resume")
				.withDescription("Check existing target file(s) and try to resume")
				.isRequired(false)
				.create("R")
				);
		options.addOption(
				OptionBuilder.withLongOpt("recurse")
				.withDescription("Recurse into subdirectories, if applicable")
				.isRequired(false)
				.create("r")
				);
		options.addOption(
				OptionBuilder.withLongOpt("preserve")
				.withDescription("Preserve file modification timestamp")
				.isRequired(false)
				.create("p")
				);
		options.addOption(
				OptionBuilder.withLongOpt("threads")
				.withDescription("Use specified number of UFTP connections (threads)")
				.isRequired(false)
				.hasArg()
				.create("t")
				);
		options.addOption(
				OptionBuilder.withLongOpt("split-threshold")
				.withDescription("Minimum size for files to be transferred using multiple threads (with 't')")
				.isRequired(false)
				.hasArg()
				.create("T")
				);
		options.addOption(
				OptionBuilder.withLongOpt("archive")
				.withDescription("Tell server to interpret data as tar/zip stream and unpack it")
				.isRequired(false)
				.create("a")
				);
		return options;
	}

	@Override
	public void parseOptions(String[] args) throws ParseException {
		super.parseOptions(args);
		if(fileArgs.length<2){
			throw new IllegalArgumentException("Must specify source and target!");
		}
		target = fileArgs[fileArgs.length-1];
		isUpload = !ConnectionInfoManager.isLocal(target);
		multiple = fileArgs.length>2;
		if(multiple){
			if(!target.endsWith("/")){
				target = target + "/";
			}
		}
		recurse = line.hasOption('r');
		preserve = line.hasOption('p');
		resume = line.hasOption('R');
		archiveMode = line.hasOption('a');
		
		if (line.hasOption('t')) {
			numClients = Integer.parseInt(line.getOptionValue('t'));
			if(numClients<1){
				throw new ParseException("Number of threads must be larger than '1'!");
			}else if(numClients>1){
				preserve=true; // want to write chunks
			}
			if (line.hasOption('T')) {
				String thresh = line.getOptionValue('T');
				splitThreshold = (long)UnitParser.getCapacitiesParser(2).getDoubleValue(thresh);
			}
			if(verbose && !archiveMode){
				System.err.println("Using up to <"+numClients+"> client threads, file split threshold = "
						+UnitParser.getCapacitiesParser(0).getHumanReadable(splitThreshold));
			}
		}
		if (line.hasOption('B')) {
			String bytes = line.getOptionValue('B');
			if(bytes!=null)initRange(bytes);
		}
		
		if(resume && line.hasOption('B')){
			throw new ParseException("Resume mode is not supported in combination with a byte range!");
		}

		if(verbose && archiveMode){
			System.err.println("Archive mode ENABLED");
		}
	}

	@Override
	public void setOptions(ClientFacade client){
		super.setOptions(client);
		client.setNumClients(numClients);
		client.setSplitThreshold(splitThreshold);
		if(startByte!=null){
			client.setRange(startByte, endByte, mode);
		}
		client.setResume(resume);
		client.setPreserveAttributes(preserve);
		client.setArchiveMode(archiveMode);
	}

	@Override
	protected void run(ClientFacade client) throws Exception {
		client.setVerbose(verbose);
		int len = fileArgs.length-1;
		for(int i=0; i<len;i++){
			String source = fileArgs[i];
			String target = this.target;
			RecursivePolicy policy = recurse ? RecursivePolicy.RECURSIVE:RecursivePolicy.NONRECURSIVE;
			client.cp(source, target, policy);
		}
		client.resetRange();
	}

	protected void initRange(String bytes){
		String[]tokens=bytes.split("-");
		try{
			if(tokens.length>1) {
				String start=tokens[0];
				String end=tokens[1];
				if(start.length()>0){
					startByte = (long)(UnitParser.getCapacitiesParser(0).getDoubleValue(start));
					endByte=Long.MAX_VALUE;
				}
				if(end.length()>0){
					endByte=(long)(UnitParser.getCapacitiesParser(0).getDoubleValue(end));
					if(startByte==null){
						startByte=Long.valueOf(0l);
					}
				}
				// optional mode
				if(tokens.length>2){
					String m = tokens[2];
					if("p".equalsIgnoreCase(m)){
						mode = RangeMode.READ_WRITE;
					}
				}
			}
			else {
				String end=tokens[0];
				endByte=(long)(UnitParser.getCapacitiesParser(0).getDoubleValue(end));
				startByte=Long.valueOf(0l);
			}
		}catch(Exception e){
			throw new IllegalArgumentException("Could not parse byte range "+bytes);
		}
	}

}
