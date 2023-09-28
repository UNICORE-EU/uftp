
package eu.unicore.uftp.standalone.authclient;

import org.apache.hc.client5.http.classic.HttpClient;

import eu.emi.security.authn.x509.helpers.BinaryCertChainValidator;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.util.httpclient.DefaultClientConfiguration;
import eu.unicore.util.httpclient.HttpUtils;

/**
 *
 * @author jj
 */
public class HttpClientFactory {

    public static HttpClient getClient(String url) {
    	// TODO should have "-k" option to choose non-validation of TLS certs 
        return buildClient(url);
    }

    private static HttpClient buildClient(String url){
        DefaultClientConfiguration security = new DefaultClientConfiguration();
        security.setValidator(new BinaryCertChainValidator(true));
        security.setSslAuthn(true);
        security.setSslEnabled(true);
        checkHttpProxy();
        return HttpUtils.createClient(url, security);
    }
    
    // temporary solution for a unified way of defining http and ftp proxy settings
    private static void checkHttpProxy() {
    	String httpProxy = Utils.getProperty("UFTP_HTTP_PROXY", null);
    	if(httpProxy==null)return;
    	System.setProperty("proxyHost", httpProxy);
    	String httpProxyPort = Utils.getProperty("UFTP_HTTP_PROXY_PORT", null);
    	if(httpProxyPort!=null) {
    		System.setProperty("proxyPort", httpProxyPort);
        }
    }
    
}
