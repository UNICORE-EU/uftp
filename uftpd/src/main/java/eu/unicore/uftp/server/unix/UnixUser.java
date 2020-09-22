package eu.unicore.uftp.server.unix;

/**
 * UNIX user information
 * 
 * @author Valentina Huber, Forschungszentrum Jülich GmbH
 * @author schuller
 */
public class UnixUser {
	
    public static final int SUCCESS = 0;
    public static final int FAILURE = -1;
    
    private String loginName;
    //real user and group IDs
    private int uid=-1;
    private int gid=-1;
    
    //effective user and group IDs
    private int euid=-1;
    private int egid=-1;
    
    private String name;
    private String home;
    private String shell;
    
    
    /** Returns a user object for the current user. */
    public static native UnixUser whoami();
    
    
    /** Looks up a user according to the user name. */
    public UnixUser(String loginName) throws IllegalArgumentException {
        if (!lookupByLoginName(loginName))
            throw new IllegalArgumentException("No such user: "+loginName);
    }
    
    
    /** Looks up a user according to the user's UID. */
    public UnixUser(int uid) throws IllegalArgumentException  {
        if (!lookupByUid(uid))
            throw new IllegalArgumentException ("No such user: "+uid);
    }
    
    
    /** Returns the home directory of the user. */
    public String getHome() { return home; }
    
    
    /** Returns the login name of the user. */
    public String getLoginName() { return loginName; }
    
    
    /** Returns the real name of the user. */
    public String getName() { return name; }
    
    
    /** Returns the shell used by the user. */
    public String getShell() { return shell; }
    
    
    /** Returns the user ID number for the user. */
    public int getUid() { return uid; }
    
    /** Returns the group ID number for the user. */
    public int getGid() { return gid; }
    
    /** Returns the effective user ID number for the user. */
    public int getEUid() { return euid; }
    
    /** Returns the effective group ID number for the user. */
    public int getEGid() { return egid; }
    
    
    /**
     * Native method that performs a getpwnam() call to fill the user's fields.
     * This is synchronized because getpwnam() is not thread-safe.
     */
    private synchronized native boolean lookupByLoginName(String name);
    
    
    /**
     * Native method call that performs a getpwuid() call to fill the user's
     * fields. This is synchronized because getpwuid() is not thread-safe.
     */
    private synchronized native boolean lookupByUid(int uid);
    
    
    
    /** assigns secondary groups */
    public static native int initGroups(String name, int gid);
    
    public String toString() {
        return "[name=" + getName() + " loginName=" + getLoginName()
        + " uid=" + getUid() + " gid=" + getGid()
        + " euid=" + getEUid() + " egid=" + getEGid()
        +"]"; 
    }
    
    public String getEffective(){
    	return "[euid=" + getEUid() + " egid=" + getEGid()+"]"; 
    }

    

    /** Sets the real&effective user ID and stores the original one */
    public static native int changeIdentity(int uid, int originalUID);
    
    /** Sets the real user ID */
    public static native int setUid(int uid);
    
    /** Sets the effective user ID */
    public static native int setEUid(int uid);
    
    /** Sets the real and effective user ID */
    public static native int setReUid(int ruid, int euid);
    
    /** Sets the real group ID */
    public static native int setGid(int gid);
    
    /** Sets the effective group ID */
    public static native int setEGid(int gid);
    
    /** Sets the real and effective group ID */
    public static native int setReGid(int rgid, int egid);
    
}

