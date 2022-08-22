package eu.unicore.uftp.standalone.commands;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import eu.unicore.uftp.client.FileInfo;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.util.UnitParser;

public class ULS extends Command {

	boolean humanReadable = false;

	protected Options getOptions() {
		Options options = super.getOptions();
		options.addOption(Option.builder("H").longOpt("human-readable")
				.desc("Show file sizes with units like k, M, G, ...")
				.required(false)
				.build());
		return options;
	}

	@Override
	public void parseOptions(String[] args) throws ParseException {
		super.parseOptions(args);
		humanReadable = line.hasOption('H');
	}

	@Override
	public String getName() {
		return "ls";
	}

	@Override
	protected void run(ClientFacade client) throws Exception {
		if(fileArgs.length==0) {
			throw new IllegalArgumentException("Missing argument: "+getArgumentDescription());
		}
		FileInfo info = client.stat(fileArgs[0]);
		if(info.isDirectory()){
			List<FileInfo> ls = client.ls(fileArgs[0]);
			printDir(ls);
		}
		else{
			printSingle(info, -1);
		}
	}

	protected void printDir(List<FileInfo> ls) {
		int size = -1;
		for (FileInfo item : ls) {
			size = Math.max(size, String.valueOf(item.getSize()).length());
		}

		for (FileInfo item : ls) {
			printSingle(item, size);
		}
	}

	final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");

	final UnitParser up = UnitParser.getCapacitiesParser(2);

	protected void printSingle(FileInfo fi, int size) {
		StringBuilder info = new StringBuilder();
		info.append(fi.getIsDirectory());
		info.append(fi.getUnixPermissions("-"));
		if(humanReadable) {
			info.append(String.format(" %10s ", up.getHumanReadable(fi.getSize())));	
		}
		else {
			if(size>-1) {
				info.append(String.format(" %"+size+"d ", fi.getSize()));
			}
			else {
				info.append(String.format(" %d ", fi.getSize()));
			}
		}
		info.append(format.format(new Date(fi.getLastModified())));	
		info.append(" ").append(fi.getPath());
		System.out.println(info.toString());
	}

	@Override
	public String getArgumentDescription() {
		return "<remote_directory>";
	}

	@Override
	public String getSynopsis() {
		return "Lists a remote directory.";
	}

}
