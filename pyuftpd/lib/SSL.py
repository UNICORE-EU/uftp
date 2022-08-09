#
# SSL-related functions
#

import ssl
import re

def setup_ssl(config, socket, LOG, server_mode=False):
    """ Wraps the given socket with an SSL context """
    keypass = config.get('credential.password', None)
    try:
        # Python 3.6 and later
        protocol_version = ssl.PROTOCOL_TLS
    except:
        protocol_version = ssl.PROTOCOL_TLSv1_2
        LOG.info("Fallback to SSL protocol TLS v1.2 - consider updating your OS or python3 version")
    context = ssl.SSLContext(protocol_version)
    context.verify_mode = ssl.CERT_REQUIRED
    context.check_hostname = False
    cert = config.get('credential.path', None)
    if cert is not None:
        LOG.info("Loading uftpd credential from '%s'" % cert )
        context.load_cert_chain(certfile=cert, password=keypass)
    else:
        key = config.get('credential.key');
        cert = config.get('credential.certificate')
        LOG.info("Loading uftpd key from '%s', certificate from '%s'" % (key, cert))
        context.load_cert_chain(certfile=cert, keyfile=key, password=keypass)
    truststore = config.get('truststore', None)
    if truststore:
        context.load_verify_locations(cafile=truststore)
    else:
        context.load_default_certs(purpose=ssl.Purpose.SERVER_AUTH)
    return context.wrap_socket(socket, server_side=server_mode)


rdn_map = {"C": "countryName",
           "CN": "commonName",
           "O": "organizationName",
           "OU": "organizationalUnitName",
           "L": "localityName",
           "ST": "stateOrProvinceName",
           "DC": "domainComponent",
           }


def convert_rdn(rdn):
    split = rdn.split("=")
    translated = rdn_map.get(split[0].upper())
    if translated is None:
        raise Exception("Unknown element in DN: '%s'" % split[0])
    return translated, split[1]


def convert_dn(dn):
    """ Convert X500 DN in RFC 2253 format to a tuple """
    converted = []
    # split dn and strip leading/trailing whitespace
    elements = [x.strip() for x in re.split(r"[,]", dn)]
    for element in elements:
        if element != '':
            converted.append(convert_rdn(element))
    return converted


def match_rdn(rdn, subject):
    for x in subject:
        for y in x:
            if str(y[0]).lower() == str(rdn[0]).lower() and str(y[1]).lower() == str(rdn[1]).lower():
                return True
    return False


def match(subject, acl):
    """ matches the given cert subject to the ACL. The subject must be 
        in the format as returned by ssl.getpeercert()['subject']
    """
    for dn in acl:
        accept = True
        # every RDN of the ACL entry has to be in the subject
        for rdn in dn:
            accept = accept and match_rdn(rdn, subject)
            if not accept:
                break
        if accept:
            return True
    return False

def get_common_name(subject):
    """ returns the CN / commonName part of the subject """
    try:
        for x in subject:
            for y in x:
                if str(y[0]) == "commonName":
                    return "CN="+str(y[1])
    except:
        pass
    return "n/a"

def verify_peer(config, socket, LOG):
    """ check that the peer is OK by comparing the DN to our ACL """
    acl = config.get('uftpd.acl', [])
    subject = socket.getpeercert()['subject']
    LOG.debug("Verify Auth server certificate with subject %s" % str(subject))
    if not match(subject, acl):
        raise EnvironmentError("Connection (from: %s) not allowed by ACL" % get_common_name(subject))
