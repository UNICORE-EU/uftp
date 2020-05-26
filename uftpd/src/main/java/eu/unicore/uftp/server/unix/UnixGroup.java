package eu.unicore.uftp.server.unix;

/**
 * UNIX group
 * 
 * @author Valentina Huber, Forschungszentrum Jülich GmbH
 * @author schuller
 */

import java.util.List;

/**
 *
 * @author  huber
 */
public class UnixGroup {
    
    private int gid;
    private String name;
    private List<String> members;
    
    /** Looks up a user according to the group name. */
    public UnixGroup(String groupName)throws IllegalArgumentException{
        if (!lookupByName(groupName))
            throw new IllegalArgumentException("No such group: "+groupName);
    }
    
    
    /** Looks up a user according to the group's GID. */
    public UnixGroup(int gid) throws IllegalArgumentException {
        if (!lookupByGid(gid))
            throw new  IllegalArgumentException("No such group: "+gid);
    }
    
    /** Returns the real name of the group. */
    public String getName() { return name; }
    
    
    /** Returns the ID number for the group. */
    public int getGid() { return gid; }
    
    
    /** Returns the members for this group. */
    public List<String>getMembers() { return members; }
    
    
    /**
     * Native method that performs a getgrnam() call to fill the group's fields.
     * This is synchronized because getgrnam() is not thread-safe.
     */
    private synchronized native boolean lookupByName(String name);
    
    
    /**
     * Native method call that performs a getgrnam() call to fill the group's
     * fields. This is synchronized because getgrnam() is not thread-safe.
     */
    private synchronized native boolean lookupByGid(int gid);

    
    public String toString() {
        return ("UnixGroup: [name="+getName()+", gid="+getGid()+", members="+members);
    }

}