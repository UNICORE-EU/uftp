package eu.unicore.uftp.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;

import eu.unicore.uftp.dpc.Utils;

public class SessionCommands {

	/*
	 * first arg is command name, remaining args are command parameters
	 */
	public static Runnable createCMD(List<String> args, UFTPSessionClient client){
		String name=args.remove(0);
		CmdBase cmd = null;

		if("get".equalsIgnoreCase(name)){
			cmd=new Get(args);
		}
		if("put".equalsIgnoreCase(name)){
			cmd=new Put(args);
		}
		if("lcd".equalsIgnoreCase(name)){
			cmd=new LCD(args);
		}
		if("pwd".equalsIgnoreCase(name)){
			cmd=new PWD();
		}
		
		if(cmd!=null)cmd.setClient(client);
		return cmd;
	}

	public static abstract class CmdBase implements Runnable {

		protected UFTPSessionClient client;

		public void setClient(UFTPSessionClient client){
			this.client=client;

		}
	}

	/**
	 * download a remote file
	 */
	public static class Get extends CmdBase {

		private File remoteFile;
		String localName;

		public Get(List<String> args){
			remoteFile=new File(Utils.trim(args.get(0)));
			if(args.size()>1){
				localName=args.get(1);
			}
			else{
				localName=remoteFile.getName();
			}
		}

		public void run(){
			FileOutputStream localTarget=null;
			try{
				localTarget=new FileOutputStream(new File(client.getBaseDirectory(),localName));
				client.get(remoteFile.getPath(), localTarget);
			}catch(Exception ex){
				throw new RuntimeException(ex);
			}
			finally{
				if(localTarget!=null){
					try{
						localTarget.close();
					}catch(Exception ex){}
				}
			}
		}
	}
	
	/**
	 * upload a remote file
	 */
	public static class Put extends CmdBase {

		private String remoteName;
		String localFilename;
		long size=-1;
		
		public Put(List<String> args){
			localFilename=Utils.trim(args.get(0));
			if(args.size()>1){
				remoteName=args.get(1);
			}
			else{
				remoteName=new File(localFilename).getName();
			}
			if(args.size()>2){
				size=Long.valueOf(args.get(2));
			}
		}

		public void run(){
			FileInputStream localSource=null;
			File localFile=new File(client.getBaseDirectory(),localFilename);
			if(size<0){
				size=localFile.length();
			}
			try{
				localSource=new FileInputStream(localFile);
				client.put(remoteName, size, localSource);
			}catch(Exception ex){
				throw new RuntimeException(ex);
			}
			finally{
				if(localSource!=null){
					try{
						localSource.close();
					}catch(Exception ex){}
				}
			}
		}
	}
	
	/**
	 * change local base dir
	 */
	public static class LCD extends CmdBase {
		
		String base;
		
		public LCD(List<String> args){
			base=Utils.trim(args.get(0));
		}

		public void run(){
			client.setBaseDirectory(new File(base));
		}
	}

	/**
	 * change local base dir
	 */
	public static class PWD extends CmdBase {
		
		public PWD(){}

		public void run(){
			try{
				System.out.println(client.pwd());
			}catch(Exception ex){
				throw new RuntimeException(ex);
			}
		}
	}
}
