/*********************************************************************************
 * Copyright (c) 2016 Forschungszentrum Juelich GmbH 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * (1) Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the disclaimer at the end. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution.
 * 
 * (2) Neither the name of Forschungszentrum Juelich GmbH nor the names of its 
 * contributors may be used to endorse or promote products derived from this 
 * software without specific prior written permission.
 * 
 * DISCLAIMER
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ********************************************************************************/
package eu.unicore.uftp.server.requests;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

import eu.unicore.uftp.dpc.Utils;

/**
 * @author bjoernh
 */
public class UFTPTunnelRequest extends UFTPBaseRequest {
	
	private static final String NL = "\n";

	static final String REQUEST_TYPE = "uftp-tunnel-request";
	
	private static final String PROP_TARGET_HOST = "target-host";
	private static final String PROP_TARGET_PORT = "target-port";
	private static final String PROP_SECRET = "secret";
	private static final String PROP_KEY = "key";
	private static final String PROP_RATE_LIMIT = "rate-limit";

	private static final String PROP_USER = "user";
	
	private final String targetHost;
	private final int    targetPort;
	private final String key;
	private final long ratelimit;
	
	public UFTPTunnelRequest(InetAddress[] clientHost, int targetPort, String targetHost, String user, String secret, String key,
			long ratelimit) {
		super(clientHost, user, secret);
		this.targetHost = targetHost;
		this.targetPort = targetPort;
		this.key        = key;
		this.ratelimit  = ratelimit;
	}

	public UFTPTunnelRequest(Properties props) throws NumberFormatException, UnknownHostException {
		this(Utils.parseInetAddresses( props.getProperty("client-ip"), logger),
				Integer.valueOf(props.getProperty(PROP_TARGET_PORT)),
				((String) props.getProperty(PROP_TARGET_HOST)),
				((String) props.getProperty(PROP_USER)),
				((String) props.getProperty(PROP_SECRET)),
				((String) props.getProperty(PROP_KEY)),
				Long.valueOf(props.getProperty(PROP_RATE_LIMIT,"0")));
	}

	@Override
	public void writeEncoded(OutputStream os) throws IOException {
		super.writeEncoded(os);
		StringBuilder sb = new StringBuilder();
		sb.append(PROP_TARGET_HOST).append("=").append(targetHost).append(NL);
		sb.append(PROP_TARGET_PORT).append("=").append(targetPort).append(NL);
		if(key!=null)sb.append(PROP_KEY).append("=").append(key).append(NL);
		if(ratelimit>0)sb.append(PROP_RATE_LIMIT).append("=").append(ratelimit).append(NL);
		sb.append("END").append(NL);
		os.write(sb.toString().getBytes());
		os.flush();
	}

	@Override
	protected String getRequestType() {
		return REQUEST_TYPE;
	}

	public String getTargetHost() {
		return targetHost;
	}

	public int getTargetPort() {
		return targetPort;
	}

}
