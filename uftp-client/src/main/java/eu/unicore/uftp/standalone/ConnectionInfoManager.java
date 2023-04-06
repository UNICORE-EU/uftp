package eu.unicore.uftp.standalone;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import eu.unicore.services.rest.client.IAuthCallback;
import eu.unicore.uftp.standalone.authclient.AuthClient;
import eu.unicore.uftp.standalone.authclient.AuthserverClient;
import eu.unicore.uftp.standalone.authclient.UNICOREStorageAuthClient;

/**
 *
 * @author jj
 */
public class ConnectionInfoManager {

    public static final int defaultPort = 443;

    private String uri;
    
    private Map<String, String> extracted;

    private final IAuthCallback authData;

    public ConnectionInfoManager(IAuthCallback authData) {
    	this.authData = authData;
    }

    public void init(String uri) throws URISyntaxException {
	if(!isRemote(uri))throw new URISyntaxException(uri, "Not a valid server URL");
        this.uri = uri;
        this.extracted = extractConnectionParameters(uri);
    }

    public String getURI(){
    	return uri;
    }
    
    /**
     * full URI of the authentication server
     */
    public String getAuthURL() {
        return extracted.get("auth");
    }

    /**
     * remote resource
     */
    public String getPath() {
        return extracted.get("path");
    }
    
    public String getBasedir() {
        return extracted.get("basedir");
    }

    public int getPort() {
        return Integer.parseInt(extracted.get("port"));
    }

    public String getUserName() {
        return extracted.get("username");
    }

    public String getPassword() {
        return extracted.get("password");
    }

    public String getScheme() {
        return extracted.get("scheme");
    }

    public boolean isSameServer(String uri) {
        if (isLocal(uri)) {
            return false;
        }
        try {
            Map<String, String> params = extractConnectionParameters(uri);
            return (params.get("auth").equals(getAuthURL())
                    && params.get("port").equals(String.valueOf(getPort()))
                    && params.get("scheme").equals(getScheme())
                    && isIncluded(params.get("basedir"))
            		);
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private boolean isIncluded(String directory) {
    	return directory!=null && directory.startsWith(getBasedir());
    }

    Map<String,String> extractConnectionParameters(String uriString) throws URISyntaxException {
        Map<String, String> parameters = new HashMap<>();
        URI localUri = new URI(uriString);
        setCommonParameters(localUri, parameters);
        return parameters;
    }

    private static final String[]remote = new String[]{"https://", "http://"};
    
    private static boolean checkRemote(String what){
    	for(String r: remote){
    		if(what.startsWith(r))return true;
    	}
    	return false;
    }
    
    public static boolean isRemote(String argument) {
    	return argument!=null && checkRemote(argument.toLowerCase());
    }

    public static boolean isLocal(String argument) {
    	return argument!=null && !checkRemote(argument.toLowerCase());
    }

    
    void setCommonParameters(URI localUri, Map<String, String> parameters) {
        String scheme = localUri.getScheme();
    	parameters.put("scheme", scheme);
        parameters.put("host", localUri.getHost());
        int port = localUri.getPort() != -1 ? localUri.getPort() : defaultPort;
        parameters.put("port", String.valueOf(port));
        String path = localUri.getPath();
        String[]paths=(localUri.getPath().split("\\:",2));
        String auth = scheme+"://"+localUri.getHost()+":"+port;
        if(paths.length>1){
        	path=paths[1];
        	if(path.endsWith("/")) {
        		parameters.put("basedir", path);
    			parameters.put("filename", ".");
        	}
        	else {
        		Path p = Path.of(path);
        		Path dir = p.getParent();
        		if(dir!=null) {
        			parameters.put("basedir", dir.toString());
        			parameters.put("filename", p.getFileName().toString());
        		}
        	}
        }
        else {
            path = "";
        }
        auth = auth + paths[0];
        parameters.put("path", path);
        parameters.put("auth", auth);
    }
    
    public AuthClient getAuthClient(ClientFacade client) throws Exception {
        String url = getAuthURL();
        IAuthCallback auth = getAuthData();
        if(url.contains("/rest/core/storages/") && !url.contains("/rest/auth")){
        	if(!url.endsWith("/exports"))url+="/exports";
        	return new UNICOREStorageAuthClient(url, auth, client);
        }
        else {
        	return new AuthserverClient(url, auth, client);
        }
    }

    public IAuthCallback getAuthData() throws Exception {
    	return authData;
    }
    
}
