#
# Retrieves and caches various info about a user
# (groups, home dir, ...)
#
# The cache time is configurable
#
import time
import pwd
import grp

import Utils

class UserCache(object):
    def __init__(self, cache_ttl, LOG, use_id_to_resolve_groups = False):
        self.cache_ttl = cache_ttl
        self.LOG = LOG
        self.all_groups = {}
        self.groups_cache = {}
        self.uids = {}
        self.gids = {}
        self.homes = {}
        self.groups = {}
        self.members = {}
        self.users_timestamps = {}
        self.groups_timestamps = {}
        self.use_id_to_resolve_groups = use_id_to_resolve_groups
            
    def prepare_users(self, user):
        timestamp = self.users_timestamps.get(user)
        if self.expired(timestamp):
            self.update_user_info(user)
            if self.users_timestamps.get(user) is None:
                self.LOG.debug("Unknown user name requested: %s" % user)

    def prepare_groups(self, group):
        timestamp = self.groups_timestamps.get(group)
        if self.expired(timestamp):
            self.update_group_info(group)
        if self.groups_timestamps.get(group) is None:
            self.LOG.debug("Unknown group name requested: %s" % group)

    # checks if cache TTL is expired
    def expired(self, timestamp):
        if timestamp is None or timestamp + self.cache_ttl < time.time():
            return True
        else:
            return False

    # retrieves all gids the username is member of
    def get_gids_4user(self, user):
        self.prepare_users(user)
        gids = self.all_groups.get(user)
        if gids is None:
            return []
        else:
            return gids

    # resolves the group name
    def get_gid_4group(self, group):
        self.prepare_groups(group)
        gid = self.groups.get(group)
        if gid is None:
            return -1
        else:
            return gid

    # returns all members of a given group name
    def get_members_4group(self, group):
        self.prepare_groups(group)
        members = self.members.get(group)
        if members is None:
            return []
        else:
            return members

    # returns primary gid for a username
    def get_gid_4user(self, user):
        self.prepare_users(user)
        gid = self.gids.get(user)
        if gid is None:
            return -1
        else:
            return gid

    # returns uid for a username
    def get_uid_4user(self, user):
        self.prepare_users(user)
        uid = self.uids.get(user)
        if uid is None:
            return -1
        else:
            return uid

    # returns home for a username
    def get_home_4user(self, user):
        self.prepare_users(user)
        return self.homes.get(user)

    # Establish the list of all (including supplementary) groups the user
    # is member of.
    # Arguments: user name and primary group id.
    def get_gids_4user_nc(self, user, gid):
        if self.use_id_to_resolve_groups:
            all_groups = self.get_gids_4user_via_id(user, gid)
        else:
            all_groups = self.get_gids_4user_via_getgrall(user, gid)
        
        self.LOG.debug("Established groups list for the user %s : %s" % (
            user, str(all_groups)))
        return all_groups
    
    # implementation using grp.getgrall()
    def get_gids_4user_via_getgrall(self, user, gid):
        all_groups = [g.gr_gid for g in
                      filter(lambda g: user in g.gr_mem, grp.getgrall())]
        all_groups.append(gid)
        return all_groups

    # alternative implementation using 'id -G <user>'
    def get_gids_4user_via_id(self, user, gid):
        success, out = Utils.run_command("id -G %s" % user)
        if not success:
            return []
        all_groups = [int(g) for g in out.split(" ")]
        if gid not in all_groups:
            all_groups.append(gid)
        return all_groups

    # Fills up all per group caches with a freshly obtained information
    # Argument: group name
    def update_group_info(self, group):
        self.groups[group] = None
        self.members[group] = None
        self.groups_timestamps[group] = None
        try:
            g = grp.getgrnam(group)
        except KeyError:
            self.LOG.debug("Unknown group requested: %s" % group)
            return []  # TODO is this the correct behaviour?

        self.groups[group] = g.gr_gid
        self.members[group] = g.gr_mem
        self.groups_timestamps[group] = time.time()
        self.LOG.debug("New group information obtained for %s (%s %s)" % (
            group, g.gr_gid, g.gr_mem))

    # Fills up all per user caches with freshly obtained information
    # Argument: user name
    def update_user_info(self, user):
        self.uids[user] = None
        self.gids[user] = None
        self.homes[user] = None
        self.all_groups[user] = None
        self.users_timestamps[user] = None
        try:
            (_x, _x, uid, gid, _x, home, _x) = pwd.getpwnam(user)
        except KeyError:
            self.LOG.debug("No such user: %s" % user)
            return

        self.LOG.debug(
            "New user information obtained for %s (%s %s)" % (user, uid, gid))
        self.uids[user] = uid
        self.gids[user] = gid
        self.homes[user] = home
        self.all_groups[user] = self.get_gids_4user_nc(user, gid)
        self.users_timestamps[user] = time.time()
