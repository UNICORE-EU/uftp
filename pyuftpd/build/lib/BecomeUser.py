"""This module contains the user-switching logic"""

import os

import Log
import UserCache


def initialize(config, LOG: Log):
    """ Setup the user cache."""
    (_x, euid, _y) = os.getresuid()
    config['uftpd.effective_uid'] = euid
    switch_uid = config.get('uftpd.switch_uid', True)
    if switch_uid or euid == 0:
        LOG.info("Running privileged, will launch sessions as the current user.")
        config['uftpd.switch_uid'] = True
    else:
        LOG.info("Running unprivileged")
        config['uftpd.switch_uid'] = False

    if config.get('uftpd.enforce_os_gids', True):
        LOG.info(
            "Groups of the user will be limited to those available for the "
            "Xlogin in the operating system.")
    else:
        LOG.info("UFTPD will be free to assign any groups for the Xlogin "
                 "regardless of the operating system settings.")

    cache_ttl = config.get('uftpd.userCacheTtl', 600)
    use_id = config.get('uftpd.use_id_to_resolve_gids', False)
    if use_id:
        LOG.info("Groups will be resolved via 'id -G <username>'")

    user_cache = UserCache.UserCache(cache_ttl, LOG, use_id)
    config['uftpd.user_cache'] = user_cache


# if requested group is the primary group or if checking is disabled return OK
# otherwise check that this user is a member of the requested group
def check_membership(group, group_gid, user, config):
    enforce_os_gids = config.get('uftpd.enforce_os_gids', True)
    user_cache = config['uftpd.user_cache']
    if enforce_os_gids and group_gid != user_cache.get_gid_4user(user):
        mem_list = user_cache.get_members_4group(group)
        if user not in mem_list:
            return False
    return True


def get_primary_group(primary, user, user_cache, fail_on_invalid_gids, config, LOG: Log):
    if primary == "DEFAULT_GID":
        new_gid = user_cache.get_gid_4user(user)
    else:
        new_gid = user_cache.get_gid_4group(primary)
        if new_gid == -1:
            if fail_on_invalid_gids:
                raise RuntimeError("Attempt to run a task with an unknown "
                                   "primary group: %s" % primary)
            else:
                LOG.debug("Requested primary group is %s, but it "
                          "is not available on the OS. Using default "
                          "for the user %s" % (primary, user))
                new_gid = user_cache.get_gid_4user(user)

        if not check_membership(primary, new_gid, user, config):
            if fail_on_invalid_gids:
                raise RuntimeError(
                    "The user %s is not a member of the group %s" % (
                        user, primary))
            else:
                LOG.debug("The user %s is not a member of the "
                          "group %s, default group will be used." % (
                              user, primary))
                new_gid = user_cache.get_gid_4user(user)

    return new_gid


def get_supplementary_groups(requested_groups, primary, user, config, LOG):
    LOG.debug("Supplementary groups for: request = %s primary = %s "
              "user = %s" % (requested_groups, primary, user))
    user_cache = config['uftpd.user_cache']
    fail_on_invalid_gids = config.get('uftpd.fail_on_invalid_gids', True)
    sup_gids = {}
    added_default = False
    sup_gids[primary] = True
    gids = []
    for g in requested_groups:
        if g == "DEFAULT_GID":
            if not added_default:
                added_default = True
                default_gids = user_cache.get_gids_4user(user)
                for d in default_gids:
                    sup_gids[d] = True
        else:
            tmp = user_cache.get_gid_4group(g)
            if tmp == -1:
                if fail_on_invalid_gids:
                    raise RuntimeError("Attempt to run a task with an unknown "
                                       "supplementary group %s" % g)
                else:
                    LOG.debug("Requested supplementary "
                              "group %s, but it is not available on the OS. "
                              "Ignoring." % g)
                    continue
            if not check_membership(g, tmp, user, config):
                if fail_on_invalid_gids:
                    raise RuntimeError("The user %s is not a member of the "
                                       "group %s" % (user, g))
                else:
                    LOG.debug("The user %s is not a member of the "
                              "group %s, skipping it." % (user, g))

            # alright, so add the supplementary group!
            sup_gids[tmp] = True

    # return only those gids the user is a member of
    for g in sup_gids:
        if sup_gids[g]:
            gids.append(g)
    return gids


def become_user(user, requested_groups, config, LOG: Log):
    """
    Change the process' identity (real and effective) to a user's (if
    process was started with sufficient privileges to allow this,
    does nothing otherwise)
    Arguments:
      user = Name of the user
      requested_groups = list of group names
      config - configuration
      LOG - logger

    Returns: True if successful, an error string otherwise

    Side effects: modifies the ENV array, setting values for USER, LOGNAME and
    HOME
    """

    euid = config['uftpd.effective_uid']
    setting_uids = config['uftpd.switch_uid']
    user_cache = config['uftpd.user_cache']
    fail_on_invalid_gids = config.get('uftpd.fail_on_invalid_gids', True)
    primary = requested_groups[0]

    if not setting_uids:
        if euid == 0:
            # make sure to prevent running things as root
            return "Running as root and not setting uids --- this is not " \
                   "allowed. Please check your UFTPD installation!"
        else:
            return True

    new_uid = user_cache.get_uid_4user(user)

    if new_uid == -1:
        return "Attempted to select unknown user '%s'" % user

    if new_uid == 0:
        return "Attempted to select 'root'"

    # Do project (group) mapping, new_gid stores a new primary gid,
    # new_gids stores the new_gid and all supplementary gids (numbers)

    if primary == "NONE":
        # Nothing selected by user, use system defaults
        new_gid = user_cache.get_gid_4user(user)
        new_gids = user_cache.get_gids_4user(user)
    else:
        try:
            new_gid = get_primary_group(primary, user, user_cache,
                                        fail_on_invalid_gids, config, LOG)
            new_gids = get_supplementary_groups(requested_groups, new_gid,
                                                user, config, LOG)
        except RuntimeError as err:
            return str(err)

    # Change identity/ drop privileges
    #
    # Impl note: yes, the primary gid will appear twice in the list, however
    # when there is no supplementary groups and only one gid (the primary gid)
    # was given then the function would result in leaving the current
    # process supplementary groups (i.e. root's). So don't change it!
    LOG.debug("Groups: primary %s supplementary %s"%(new_gid, new_gids))
    os.setgid(new_gid)
    os.setgroups(new_gids)
    os.setegid(new_gid)
    os.setresuid(new_uid, new_uid, euid)

    if (os.getuid(), os.geteuid()) != (new_uid, new_uid):
        raise RuntimeError("Could not set UFTPD identity (real,effective) for %s to %s"% (user, new_uid))
    if (os.getgid(),os.getegid()) != (new_gid, new_gid):
        raise RuntimeError("Could not set UFTPD gid (real, effective) for %s to %s" % (user, new_gid))

    set_groups = set(os.getgroups())
    if set_groups != set(new_gids):
        return "Could not set UFTPD identity (supplementary groups) to %s %s, " \
               "got %s" % (user, new_gids, set_groups)

    # set environment
    os.environ['HOME'] = user_cache.get_home_4user(user)
    os.environ['USER'] = user
    os.environ['LOGNAME'] = user

    return True
