package eu.unicore.uftp.standalone.commands;

import org.apache.commons.cli.ParseException;

/**
 * Provide options and usage information for the various 
 * client commands
 * 
 * @author schuller
 */
public interface ICommand {

	public String getName();
	
	public void parseOptions(String[] args) throws ParseException;

	public void printUsage();

	public String getArgumentDescription();
	
	public String getSynopsis();
	
	public boolean runCommand() throws Exception;
}
