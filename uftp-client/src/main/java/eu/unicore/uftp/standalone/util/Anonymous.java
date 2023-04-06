package eu.unicore.uftp.standalone.util;

import org.apache.hc.core5.http.HttpMessage;

import eu.unicore.services.rest.client.IAuthCallback;

public class Anonymous implements IAuthCallback {

	@Override
	public void addAuthenticationHeaders(HttpMessage httpMessage) throws Exception {
		// NOP
	}

}
