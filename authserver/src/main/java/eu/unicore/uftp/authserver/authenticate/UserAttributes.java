package eu.unicore.uftp.authserver.authenticate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author mgolik
 */
public final class UserAttributes {
	
	public String uid;
	public String gid;
	public String[] groups;
	
	public UserAttributes(String uid, String gid, String[] groups) {
		this.uid = uid;
		this.gid = gid;
		this.groups = groups;
	}
	
	// Other attributes
	
	/**
	 * rate limit in bytes per second, 0 = no limit
	 */
	public long rateLimit = 0;
	
	/**
	 * includes/excludes: path fragments / shell patterns separated bx ":"
	 */
	public String includes = null;
	public String excludes = null;
	
	private final static String rateRegExp = "\\s*(\\d+)\\s*([kKmMgG]).*";
	private final static Pattern ratePattern = Pattern.compile(rateRegExp);
	
	public static long parseRate(String rate){
		Matcher m = ratePattern.matcher(rate);
		String number;
		int multiplier = 1;
		if(m.matches()){
			number = m.group(1);
			String unit = m.group(2);
			if(unit!=null){
				if(unit.equalsIgnoreCase("k")){
					multiplier = 1024;
				}
				else if(unit.equalsIgnoreCase("m")){
					multiplier = 1024*1024;
				}
				else if(unit.equalsIgnoreCase("g")){
					multiplier = 1024*1024*1024;
				}
				else throw new IllegalArgumentException("Cannot parse: "+rate);
			}
		}
		else{
			number = rate;
		}
		return multiplier * Long.parseLong(number);
	}
	
}