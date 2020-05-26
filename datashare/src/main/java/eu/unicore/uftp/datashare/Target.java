package eu.unicore.uftp.datashare;

/**
 * Files are shared with a target, which can be a user, a 
 * group or something else that has a unique name.
 * 
 * @author Martin Lischewski
 */
public interface Target {
	
	static final String ANONYMOUS = "cn=anonymous,o=unknown,ou=unknown";

	/**
	 * Does the given Target include this Target?
	 */
	public boolean contains(Target user);
	
	/**
	 * Returns the unique identifier of the target
	 */
	public String getID();
	
}
