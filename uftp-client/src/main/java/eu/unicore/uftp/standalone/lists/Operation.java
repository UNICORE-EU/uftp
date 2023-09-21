package eu.unicore.uftp.standalone.lists;

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
	 * @throws Exception
	 */
    public void execute(String source, String destination) throws Exception;

}
