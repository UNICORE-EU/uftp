package eu.unicore.uftp.standalone.lists;

import java.io.IOException;

/**
 *
 * @author jj
 */
public interface Operation {

	/**
	 * perform some command
	 * 
	 * @param source
	 * @param destination
	 * @throws IOException
	 */
    public void execute(String source, String destination) throws IOException;

}
