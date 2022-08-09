import ssl, unittest

from lib import Server, SSL, Log

class TestSSLUtils(unittest.TestCase):

    def setUp(self):
        self.LOG = Log.Logger(verbose=True, use_syslog=False)

    def read_acl_file(self):
        config = {"ACL": "tests/uftpd.acl"}
        Server.update_acl(config, self.LOG)
        return config['uftpd.acl']

    def test_read_acl_file(self):
        acl = self.read_acl_file()
        self.assertTrue( [('commonName', 'UNICOREX'), ('organizationName', 'UNICORE'), ('countryName', 'EU')] in acl)
        self.assertTrue( [('commonName', 'AUTH'), ('organizationName', 'UNICORE'), ('countryName', 'EU')] in acl)
        self.assertTrue( [('commonName', 'Another server'), ('countryName', 'de')] in acl)
        self.assertTrue( [('commonName', 'some.host'), 
                         ('organizationalUnitName', 'Unit1'), 
                         ('organizationalUnitName', 'Unit2')] in acl)

    def test_match(self):
        acl = self.read_acl_file()
        s1 = ( (("commonName","Another Server"),),
               (("countryName", "DE"),),
               ) 
        self.assertTrue(SSL.match(s1, acl))
        self.assertEqual("CN=Another Server", SSL.get_common_name(s1))
        
        s2 = ( (("commonName","some.host"),),
               (("organizationalUnitName", "Unit1"),),
               (("organizationalUnitName", "Unit2"),),
               ) 
        self.assertTrue(SSL.match(s2, acl))

   
if __name__ == '__main__':
    unittest.main()
