package eu.unicore.uftp.standalone.commands;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;

import eu.unicore.uftp.client.FileInfo;
import eu.unicore.uftp.client.UFTPSessionClient;
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
		int len = fileArgs.length;
		UFTPSessionClient sc = null;
		try {
			for(String fileArg: fileArgs) {
				sc = client.checkReInit(fileArg, sc);
				String path = client.getConnectionManager().extractConnectionParameters(fileArg).get("path");
				FileInfo info = sc.stat(path);
				if(info.isDirectory()){
					if(len>1) {
						System.out.println(path+": ");
						System.out.println();
					}
					List<FileInfo> ls = sc.getFileInfoList(path);
					printDir(ls);
				}
				else{
					printSingle(info, -1);
				}
			}
		} finally {
			IOUtils.closeQuietly(sc);
		}
	}

	protected void printDir(List<FileInfo> ls) {
		int width = -1;
		for (FileInfo item : ls) {
			width = Math.max(width, String.valueOf(item.getSize()).length());
		}
		for (FileInfo item : ls) {
			printSingle(item, width);
		}
	}

	final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");

	final UnitParser up = UnitParser.getCapacitiesParser(2);

	protected void printSingle(FileInfo fi, int width) {
		StringBuilder info = new StringBuilder();
		info.append(fi.getIsDirectory());
		info.append(fi.getUnixPermissions("-"));
		if(humanReadable) {
			info.append(String.format(" %10s ", up.getHumanReadable(fi.getSize())));	
		}
		else {
			if(width>-1) {
				info.append(String.format(" %"+width+"d ", fi.getSize()));
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
