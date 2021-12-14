import unittest
import os
import pwd
import time
from lib import Log, UserCache


class TestUserCache(unittest.TestCase):
    def setUp(self):
        self.LOG = Log.Logger(verbose=True, use_syslog=False)

    def getlogin(self):
        return pwd.getpwuid(os.getuid())[0]

    def test_user_cache(self):
        uc = UserCache.UserCache(2, self.LOG)
        user = self.getlogin()
        print("Getting info for the current user: %s" % user)
        gids = uc.get_gids_4user(user)
        uid = uc.get_uid_4user(user)
        home = uc.get_home_4user(user)
        print(" - uid %s" % uid)
        print(" - gids %s" % gids)
        print(" - home %s" % home)

        # check expiry
        time.sleep(2)
        self.assertTrue(uc.expired(uc.users_timestamps[user]))
        self.assertEqual(None, uc.groups_timestamps.get('root'))
        home = uc.get_home_4user(user)
        print(" - home %s" % home)
        self.assertFalse(uc.expired(uc.users_timestamps[user]))

        print("All GIDs of the 'root' user: %s" % uc.get_gids_4user('root'))
        print("GID of the 'root' group: %s" % uc.get_gid_4group('root'))
        self.assertFalse(uc.expired(uc.groups_timestamps['root']))

        # how to deal with non-existing group
        print("GID of the non-existing 'foobarspam' group: %s" %
              uc.get_gid_4group('foobarspam'))

        # how to deal with non-existing user
        print("Home for non-existing 'foobarspam' user: %s" %
              uc.get_home_4user('foobarspam'))


if __name__ == '__main__':
    unittest.main()
