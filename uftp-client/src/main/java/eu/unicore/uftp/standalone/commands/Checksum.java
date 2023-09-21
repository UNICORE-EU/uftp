package eu.unicore.uftp.standalone.commands;

import java.io.IOException;
import java.util.Map;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;

import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.client.UFTPSessionClient.HashInfo;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.lists.FileCrawler.RecursivePolicy;
import eu.unicore.uftp.standalone.lists.RemoteFileCrawler;

/**
 * tell the server to compute checksum(s) for remote file(s)
 * 
 * @author schuller
 */
public class Checksum extends RangedCommand {

	protected boolean recurse = false;

	protected String hashAlgorithm = null;

	@Override
	public String getName() {
		return "checksum";
	}

	@Override
	public String getArgumentDescription() {
		return "https://<server-host>:<server-port>/rest/auth/SERVER-NAME:file";
	}
	
	public String getSynopsis(){
		return "Compute checksums for remote file(s)";
	}
	
	protected Options getOptions() {
		Options options = super.getOptions();
		options.addOption(Option.builder("r").longOpt("recurse")
				.desc("Recurse into subdirectories, if applicable")
				.required(false)
				.build());
		options.addOption(Option.builder("a").longOpt("algorithm")
				.desc("Hash algorithm to use. One of: MD5(default), SHA-1, SHA-256, SHA-512")
				.required(false)
				.hasArg()
				.build());
		return options;
	}

	@Override
	public void parseOptions(String[] args) throws ParseException {
		super.parseOptions(args);
		recurse = line.hasOption('r');
		if (line.hasOption('a')) {
			hashAlgorithm = line.getOptionValue('a');
		}
		if (line.hasOption('B')) {
			String bytes = line.getOptionValue('B');
			if(bytes!=null)initRange(bytes);
		}
		if(fileArgs.length==0) {
			throw new IllegalArgumentException("Missing argument: "+getArgumentDescription());
		}
	}
	
	@Override
	protected void run(ClientFacade client) throws Exception {
		UFTPSessionClient sc = null;
		try {
			for(String fileSpec: fileArgs) {
				sc = client.checkReInit(fileSpec, sc);
				Map<String, String> params = client.getConnectionManager().extractConnectionParameters(fileSpec);
				String path = params.get("path");
				checksum(path, sc);
			}
		} finally {
			IOUtils.closeQuietly(sc);
		}
	}

	protected void checksum(String path, UFTPSessionClient sc) throws Exception {
		RecursivePolicy policy = recurse ? RecursivePolicy.RECURSIVE:RecursivePolicy.NONRECURSIVE;
		RemoteFileCrawler fileList = new RemoteFileCrawler(path, "./dummy/", sc, policy);
		fileList.setCreateLocalDirs(false);
		if (fileList.isSingleFile(path)) {
			singleFileChecksum(path, sc);
		}
		else{
			fileList.crawl( (x, dummy) -> singleFileChecksum(x, sc));
		}
	}
	
	private boolean haveSetAlgorithm = false;
	
	private void singleFileChecksum(String path, UFTPSessionClient sc) throws IOException{
		if(hashAlgorithm!=null && !haveSetAlgorithm) {
			String reply = sc.setHashAlgorithm(hashAlgorithm);
			haveSetAlgorithm = true;
			verbose("Set hash algorithm: {}", reply);
		}
		HashInfo hi = sc.getHash(path, getOffset(), getLength());
		System.out.println(hi.hash+"  "+hi.path);
	}

}
