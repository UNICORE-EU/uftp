
package eu.unicore.uftp.standalone.authclient;

import org.apache.http.client.HttpClient;
import org.apache.log4j.Logger;

import eu.emi.security.authn.x509.helpers.BinaryCertChainValidator;
import eu.unicore.util.httpclient.DefaultClientConfiguration;
import eu.unicore.util.httpclient.HttpUtils;

/**
 *
 * @author jj
 */
public class HttpClientFactory {
    
    private static final Logger LOG = Logger.getLogger(HttpClientFactory.class.getName());
    
    public static HttpClient getClient(String url) {
    	// TODO should have "-k" option to choose non-validation of TLS certs 
        return buiLdClient(url);
    }

    private static HttpClient buiLdClient(String url){
        LOG.info("Not verifying any TLS certs.");
        DefaultClientConfiguration security = new DefaultClientConfiguration();
        security.setValidator(new BinaryCertChainValidator(true));
        security.setSslAuthn(true);
        security.setSslEnabled(true);
        return HttpUtils.createClient(url, security);
    }
    
}
