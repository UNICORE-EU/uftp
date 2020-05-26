package eu.unicore.uftp.datashare;

/**
 * Represents a single user who shares files using her UFTP server login/group
 * 
 * @author schuller
 */
public class Owner {

	private final String name;
	
	private final String uftpUser;
	
	private final String uftpGroup;
	
	public Owner(String name, String uftpUser, String uftpGroup) {
		this.name = name;
		this.uftpUser = uftpUser;
		this.uftpGroup = uftpGroup;
	}

	public String getName() {
		return name;
	}

	public String toString() {
		return this.getName()+"["+uftpUser+":"+(uftpGroup!=null?uftpGroup:"<none>")+"]";
	}
	
	public String getUftpUser(){
		return uftpUser;
	}

	public String getUftpGroup(){
		return uftpGroup;
	}

	public boolean equals(Object other){
		return (other instanceof Owner) && String.valueOf(this).equals(String.valueOf(other));
	}
}
