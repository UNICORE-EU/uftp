package eu.unicore.uftp.standalone;

import eu.unicore.uftp.standalone.authclient.AuthClient;

/**
 *
 * @author jj
 */
public class FakeConnectionInfoManager extends ConnectionInfoManager {

	public FakeConnectionInfoManager(){
		super(null);
	}

    @Override
    public AuthClient getAuthClient(ClientFacade c) {
        return new FakeAuthClient();
    }

    @Override
    public boolean isSameServer(String uri) {
        System.out.println("Testing if " + uri + " is equal to " + super.getURI() + " : " + super.isSameServer(uri));

        return super.isSameServer(uri);
    }

}
