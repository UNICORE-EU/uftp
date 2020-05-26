package eu.unicore.uftp.datashare;

/**
 * Represents a single user with whom files are shared
 * 
 * @author Martin Lischewski
 */
public class SharingUser implements Target {

	private final String uniqueID;
	
	public SharingUser(String uniqueID) {
		if(uniqueID==null)throw new IllegalArgumentException("Unique ID cannot be null");
		this.uniqueID = uniqueID;
	}

	/**
	 * TODO other types of targets (e.g. groups)?
	 */
	@Override
	public boolean contains(Target user) {
		if (user == null) {
			return false;
		} else {
			return this.equals(user);
		}
	}

	@Override
	public String getID() {
		return this.uniqueID;
	}

	public String toString() {
		return "SingleUser["+uniqueID+"]";
	}

	@Override
	public boolean equals(Object object) {
		if (object == null || !(object instanceof SharingUser)) {
			return false;
		}
		return ((SharingUser) object).uniqueID.equals(this.uniqueID);
	}
	
	@Override
	public int hashCode(){
		return uniqueID.hashCode();
	}
	
}
