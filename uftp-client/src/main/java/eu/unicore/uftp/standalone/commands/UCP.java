package eu.unicore.uftp.standalone.commands;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.Calendar;
import java.util.Map;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import eu.unicore.uftp.client.FileInfo;
import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.ConnectionInfoManager;
import eu.unicore.uftp.standalone.lists.FileCrawler;
import eu.unicore.uftp.standalone.lists.FileCrawler.RecursivePolicy;
import eu.unicore.uftp.standalone.lists.LocalFileCrawler;
import eu.unicore.uftp.standalone.lists.Operation;
import eu.unicore.uftp.standalone.lists.RemoteFileCrawler;
import eu.unicore.uftp.standalone.util.ClientPool;
import eu.unicore.uftp.standalone.util.ClientPool.TransferTask;
import eu.unicore.uftp.standalone.util.RangeMode;
import eu.unicore.uftp.standalone.util.UnitParser;
import eu.unicore.util.Log;

public class UCP extends DataTransferCommand {

	protected boolean resume = false;

	protected boolean isUpload = false;

	protected String target;

	// whether multiple files are to be transferred
	protected boolean multiple = false;

	protected boolean recurse = false;

	protected boolean preserve = false;

	protected int numClients = 1;

	protected long splitThreshold = -1;

	protected boolean archiveMode = false;

	protected ClientFacade client;

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

	protected Options getOptions() {
		Options options = super.getOptions();
		options.addOption(Option.builder("R").longOpt("resume")
				.desc("Check existing target file(s) and try to resume")
				.required(false)
				.build());
		options.addOption(Option.builder("r").longOpt("recurse")
				.desc("Recurse into subdirectories, if applicable")
				.required(false)
				.build());
		options.addOption(Option.builder("p").longOpt("preserve")
				.desc("Preserve file modification timestamp")
				.required(false)
				.build());
		options.addOption(Option.builder("t").longOpt("threads")
				.desc("Use specified number of UFTP connections (threads)")
				.required(false)
				.hasArg()
				.build());
		options.addOption(Option.builder("T").longOpt("split-threshold")
				.desc("Minimum size for files to be transferred using multiple threads (with 't')")
				.required(false)
				.hasArg()
				.build());
		options.addOption(Option.builder("a").longOpt("archive")
				.desc("Tell server to interpret data as tar/zip stream and unpack it")
				.required(false)
				.build());
		return options;
	}

	@Override
	public void parseOptions(String[] args) throws ParseException {
		super.parseOptions(args);
		if(fileArgs.length<2){
			throw new IllegalArgumentException("Missing argument: "+getArgumentDescription());
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
				System.err.println("Using up to <"+numClients+"> client threads.");
				if(splitThreshold>0) {
					System.err.println("Splitting files larger than " +
							UnitParser.getCapacitiesParser(0).getHumanReadable(splitThreshold));
				}
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
	protected void run(ClientFacade client) throws Exception {
		this.client = client;
		int len = fileArgs.length-1;
		for(int i=0; i<len;i++){
			String source = fileArgs[i];
			String target = this.target;
			cp(source, target);
		}
	}

	/**
	 * Entry to the Copy feature
	 */
	public void cp(String source, String destination)
			throws Exception {
		if (ConnectionInfoManager.isLocal(source)) {
			startUpload(source, destination);
			return;
		}
		if (ConnectionInfoManager.isLocal(destination)) {
			startDownload(source, destination);
			return;
		}
		String error = String.format("Unable to handle [%s, %s] combination. "
				+ "It is neither upload nor download", source, destination);
		throw new IllegalArgumentException(error);
	}


	private void startUpload(String localSource, String destinationURL) 
			throws Exception {
		UFTPSessionClient sc = client.doConnect(destinationURL);
		String remotePath = client.getConnectionManager().getPath();
		RecursivePolicy policy = recurse ? RecursivePolicy.RECURSIVE : RecursivePolicy.NONRECURSIVE;
		try(ClientPool pool = new ClientPool(numClients, client, destinationURL, verbose)){
			LocalFileCrawler fileList = new LocalFileCrawler(localSource, remotePath, sc);
			fileList.crawl(getUploadCommand(preserve, pool, sc), policy);
		}
	}

	private Operation getUploadCommand(final boolean preserveAttributes, final ClientPool pool, UFTPSessionClient sc) {
		return (source, destination)-> {
			try {
				executeSingleFileUpload(source, destination, pool, sc);
			} catch (URISyntaxException|IOException ex) {
				throw new IOException("Error uploading '"+source+"' -> '"+destination+"'", ex);
			}			
		};
	}

	private void executeSingleFileUpload(String local, String remotePath, ClientPool pool, UFTPSessionClient sc)
			throws FileNotFoundException, URISyntaxException, IOException {
		String dest = getFullRemoteDestination(local, remotePath);
		if(archiveMode) {
			sc.setType(UFTPSessionClient.TYPE_ARCHIVE);
		}
		if("-".equals(local)){
			try(InputStream is = System.in){
				is.skip(getOffset());
				sc.put(dest, getLength(), is);
			}
		}
		else{
			File file = new File(local);
			long offset = 0;
			if(resume && !haveRange()){
				try{
					offset = sc.getFileSize(dest);
				}catch(IOException ioe) {
					// does not exist
				}
				long size = file.length() - offset;
				if(size>0){
					verbose("Resuming transfer, already have <{}> bytes", offset);
					TransferTask task = getUploadChunkTask(dest, local, offset, file.length()-1, null);
					pool.submit(task);
				}
				else{
					verbose("Nothing to do for <{]>", dest);
				}
			}
			else {
				long total = getLength()>-1? getLength() : file.length();
				doUpload(pool, file, dest, getOffset(), total);
			}
		}	
	}

	private void doUpload(ClientPool pool, final File local, final String remotePath,
			final long start, final long total) throws IOException {
		int numChunks = computeNumChunks(total);
		long chunkSize = total / numChunks;
		long last = total-1;
		logger.debug("Uploading: '{}' --> '{}', length={} numChunks={} chunkSize={}", 
				local.getPath(), remotePath, total, numChunks, chunkSize);
		for(int i = 0; i<numChunks; i++){
			final long end = last;
			final long first =  i<numChunks-1 ? end - chunkSize : 0;
			TransferTask task = getUploadChunkTask(remotePath, local.getPath(), first, end, null);
			task.setDataSize(end-first);
			pool.submit(task);
			last = first - 1;
		}
	}

	private TransferTask getUploadChunkTask(String remote, String local, long start, long end, UFTPSessionClient sc)
			throws IOException {
		TransferTask task = new TransferTask(sc) {
			@Override
			public void doCall()throws Exception {
				final File file = new File(local);
				try(RandomAccessFile raf = new RandomAccessFile(file, "r");
						InputStream fis = Channels.newInputStream(raf.getChannel()))
				{
					UFTPSessionClient sc = getSessionClient();
					raf.seek(start);
					sc.put(remote, end-start+1, start, fis);
					if(preserve && !remote.startsWith("/dev/")){
						Calendar to = Calendar.getInstance();
						to.setTimeInMillis(file.lastModified());
						sc.setModificationTime(remote, to);
					}
				}
			}};
			return task;
	}

	private void startDownload(String remote, String destination) 
			throws Exception {
		UFTPSessionClient sc = client.doConnect(remote);
		Map<String, String> params = client.getConnectionManager().extractConnectionParameters(remote);
		String path = params.get("path");
		RecursivePolicy policy = recurse ? RecursivePolicy.RECURSIVE : RecursivePolicy.NONRECURSIVE;
		try(ClientPool pool = new ClientPool(numClients, client, remote, verbose)){
			FileCrawler fileList = new RemoteFileCrawler(path, destination, sc);
			fileList.crawl(getDownloadCommand(pool, sc), policy);
		}
	}

	private Operation getDownloadCommand(final ClientPool pool, UFTPSessionClient sc) {
		return (source, destination) -> {
			try {
				executeSingleFileDownload(source, destination, pool, sc);
			} catch (URISyntaxException|IOException ex) {
				throw new IOException("Error downloading: '"+source+"' -> '"+destination+"'", ex);
			}
		};
	}

	private void executeSingleFileDownload(String remotePath, String local, ClientPool pool, UFTPSessionClient sc)
			throws FileNotFoundException, URISyntaxException, IOException {
		String dest = getFullLocalDestination(remotePath, local);
		File file = new File(dest);
		OutputStream fos = null;
		RandomAccessFile raf = null;
		try{
			if("-".equals(local)){
				fos = System.out;
				sc.get(remotePath, getOffset(), getLength(), fos);
				sc.resetDataConnections();
			}else{
				FileInfo fi = sc.stat(remotePath);
				if(haveRange()){
					long size = getLength();
					doDownload(pool, remotePath, dest, fi, startByte, size);
				}
				else{
					if(resume && file.exists()){
						// check if we have local data already
						long start = file.length();
						long length = fi.getSize() - start;
						raf = new RandomAccessFile(file, "rw");
						fos = Channels.newOutputStream(raf.getChannel());

						if(length>0){
							verbose("Resuming transfer, already have <{}> bytes", start);
							TransferTask task = getDownloadChunkTask(remotePath, local, start,
									fi.getSize()-1, sc, fi, RangeMode.APPEND);
							pool.submit(task);
						}
						else{
							verbose("Nothing to do for {}",remotePath);
						}
					}
					else{
						long size = fi.getSize();
						if(file.exists()) {
							// truncate it now to make sure we don't overwrite 
							// only part of the file 
							raf = new RandomAccessFile(file, "rw");
							try {
								raf.setLength(0);
							}catch(Exception e) {}
						}
						doDownload(pool, remotePath, dest, fi, 0, size);
					}
				}
			}
		}
		finally{
			IOUtils.closeQuietly(fos);
			IOUtils.closeQuietly(raf);
		}
	}

	private void doDownload(ClientPool pool, final String remotePath, final String local, final FileInfo remoteInfo, long start, long total)
			throws URISyntaxException, IOException {
		int numChunks = computeNumChunks(total);
		long chunkSize = total / numChunks;
		logger.debug("Downloading: '{}' --> '{}', length={} numChunks={} chunkSize={}",
				remotePath, local, total, numChunks, chunkSize);
		int width = String.valueOf(numChunks).length();
		for(int i = 0; i<numChunks; i++){
			final long first = start;
			final long end = i<numChunks-1 ? first + chunkSize : total-1;
			TransferTask task = getDownloadChunkTask(remotePath, local, start, end, null, remoteInfo, rangeMode);
			String id = numChunks>1 ? 
					String.format("%s->%s [%0"+width+"d/%d]", remotePath, local, i+1, numChunks):
						remotePath+"->"+local;
			task.setId(id);
			task.setDataSize(end-first);
			pool.submit(task);
			start = end + 1;
		}
	}

	private TransferTask getDownloadChunkTask(String remotePath, String dest, long start, long end, 
			UFTPSessionClient sc, FileInfo fi, RangeMode rangeMode)
					throws FileNotFoundException, URISyntaxException, IOException{
		TransferTask task = new TransferTask(sc) {
			public void doCall() throws Exception {
				UFTPSessionClient sc = getSessionClient();
				File file = new File(dest);
				OutputStream fos = null;
				try(RandomAccessFile raf = new RandomAccessFile(file, "rw")){
					if(rangeMode==RangeMode.READ_WRITE){
						raf.seek(start);
					}
					else if(rangeMode==RangeMode.APPEND) {
						raf.seek(file.length());
					}
					fos = Channels.newOutputStream(raf.getChannel());
					sc.get(remotePath, start, end-start+1, fos);
					if(preserve && !dest.startsWith("/dev/")){
						try{
							Files.setLastModifiedTime(file.toPath(),FileTime.fromMillis(fi.getLastModified()));
						}catch(Exception ex){
							Log.logException("Error updating modification time", ex, logger);
						}
					}
				}finally{
					IOUtils.closeQuietly(fos);
				}

			}
		};
		return task;
	}

	/**
	 * if user specifies a remote directory, append the source file name
	 */
	String getFullRemoteDestination(String source, String destination) {
		if(destination.endsWith("/") && !"-".equals(source)){
			return FilenameUtils.concat(destination, FilenameUtils.getName(source));
		}
		else return destination;
	}

	/**
	 * Get the final target file name. If the local destination is a directory,
	 * append the source file name
	 */
	String getFullLocalDestination(String source, String destination) {
		String destName = FilenameUtils.getName(destination);
		File destFile = new File(destination);
		if (destName == null || destName.isEmpty() || destFile.isDirectory()) {
			destName = FilenameUtils.getName(source);
			//verify not null?
		}
		return destFile.isDirectory() ?
				new File(destFile, destName).getPath() :
					FilenameUtils.concat(FilenameUtils.getFullPath(destination), destName);
	}

	// we don't want chunks smaller than half of the split threshold
	// otherwise create a few more chunks than we have threads to try 
	// and avoid idle threads
	int computeNumChunks(long dataSize) {
		if(splitThreshold<0 || dataSize<splitThreshold || numClients<2){
			return 1;
		}
		long numChunks = 2 * dataSize / splitThreshold;
		return (int)Math.min(numChunks, (long)(1.25*numClients));
	}

}
