
package eu.unicore.uftp.standalone.lists;

import java.io.IOException;

/**
 *
 * @author jj
 */
public abstract class Command {

	/**
	 * perform some command
	 * 
	 * @param source
	 * @param destination
	 * @throws IOException
	 */
    public abstract void execute(String source, String destination) throws IOException;
 
}
