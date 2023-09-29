package eu.unicore.uftp.authserver;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.emi.security.authn.x509.X509Credential;
import eu.unicore.security.AuthenticationException;
import eu.unicore.security.SecurityException;
import eu.unicore.services.rest.jwt.JWTServerProperties;
import eu.unicore.services.rest.security.AuthNHandler;
import eu.unicore.services.rest.security.jwt.JWTUtils;
import eu.unicore.services.security.util.AuthZAttributeStore;
import eu.unicore.uftp.authserver.messages.AuthRequest;
import eu.unicore.uftp.authserver.messages.AuthResponse;
import eu.unicore.uftp.authserver.messages.CreateTunnelRequest;
import eu.unicore.uftp.authserver.share.ShareServiceProperties;
import eu.unicore.util.Log;

/**
 * @author mgolik
 * @author schuller
 */
@Path("/")
public class AuthServiceImpl extends ServiceBase {

	private static final Logger logger = Log.getLogger("authservice", AuthServiceImpl.class);

	@POST
	@Produces("application/json;charset=UTF-8")
	@Path("/{serverName}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response authenticateAndInitTransfer(@PathParam("serverName") String serverName, String json) {
		if(AuthZAttributeStore.getTokens()==null){
			throw new WebApplicationException(Status.UNAUTHORIZED);
		}
		String clientIP = AuthZAttributeStore.getTokens().getClientIP();
		logger.debug("Incoming request from: {} {}", clientIP, json);

		LogicalUFTPServer server = getLogicalServer(serverName);
		if(server == null){
			throw new WebApplicationException(404);
		}
		UserAttributes authData = null;
		AuthRequest authRequest = gson.fromJson(json, AuthRequest.class);
		
		try {
			UFTPDInstance uftpd = getUFTPD(serverName);
			try{
				authData = assembleAttributes(server, authRequest.group);
			}
			catch(SecurityException se){
				return handleError(400, "", se, logger);
			}
			if(authData.uid == null){
				throw new WebApplicationException(Status.UNAUTHORIZED);
			}
			// allow to override actual client IP with the one from the request
			if(authRequest.client!=null){
				clientIP = authRequest.client;
			}
			TransferRequest transferRequest = new TransferRequest(authRequest, authData, clientIP);
			AuthResponse response = new TransferInitializer().initTransfer(transferRequest,uftpd);            
			if(response.success) {
				response.secret = transferRequest.getSecret();
				return Response.ok().entity(response).build();
			}else {
				return handleError(500, response.reason, null, logger);
			}
		} catch (IOException e) {
			return handleError(500,"Cannot connect to UFTPD server",e,logger);
		}	
	}

	@GET
	@Path("/token")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getToken(@QueryParam("lifetime")String lifetimeParam,
			@QueryParam("renewable")String renewable,
			@QueryParam("limited")String limited)
			throws Exception {
		try {
			String method = (String)AuthZAttributeStore.getTokens().getContext().get(AuthNHandler.USER_AUTHN_METHOD);
			if("ETD".equals(method)) {
				if(!(Boolean)AuthZAttributeStore.getTokens().getContext().get(AuthNHandler.ETD_RENEWABLE)) {
					throw new AuthenticationException("Cannot create token when authenticating with a non-renewable token!");
				}
			}
			JWTServerProperties jwtProps = new JWTServerProperties(kernel.getContainerProperties().getRawProperties());
			String user = AuthZAttributeStore.getClient().getDistinguishedName();
			X509Credential issuerCred =  kernel.getContainerSecurityConfiguration().getCredential();
			long lifetime = lifetimeParam!=null? Long.valueOf(lifetimeParam): jwtProps.getTokenValidity();
			Map<String,String> claims = new HashMap<>();
			claims.put("etd", "true");
			if(Boolean.parseBoolean(renewable)) {
				claims.put("renewable", "true");
			}
			if(Boolean.parseBoolean(limited)) {
				claims.put("aud", issuerCred.getSubjectName());
			}
			String token = JWTUtils.createJWTToken(user, lifetime,
					issuerCred.getSubjectName(), issuerCred.getKey(),
					claims);
			return Response.ok().entity(token).build();
		}
		catch(Exception ex) {
			return handleError("Error creating token", ex, logger);
		}
	}

	@POST
	@Produces("application/json;charset=UTF-8")
	@Path("/{serverName}/tunnel")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response startTunnel(@PathParam("serverName") String serverName, String json) {
		if(AuthZAttributeStore.getTokens()==null){
			throw new WebApplicationException(Status.UNAUTHORIZED);
		}
		LogicalUFTPServer server = getLogicalServer(serverName);
		if(server==null){
			throw new WebApplicationException(404);
		}
		String clientIP = AuthZAttributeStore.getTokens().getClientIP();
		logger.debug("Incoming tunnel request from: {} {}", clientIP, json);
		UserAttributes authData = null;
		CreateTunnelRequest tunnelRequest = gson.fromJson(json, CreateTunnelRequest.class);
		try {
			UFTPDInstance uftpd = getUFTPD(serverName);
			try{
				authData = assembleAttributes(server, tunnelRequest.group);
			}
			catch(SecurityException se){
				return handleError(400, "", se, logger);
			}
			if(authData.uid == null){
				throw new WebApplicationException(Status.UNAUTHORIZED);
			}
			// allow to override actual client IP with the one from the request
			if(tunnelRequest.client!=null){
				clientIP = tunnelRequest.client;
			}
			TunnelRequest transferRequest = new TunnelRequest(tunnelRequest, authData, clientIP);
			AuthResponse response = new TransferInitializer().initTransfer(transferRequest,uftpd);            
			if(response.success) {
				response.secret = transferRequest.getSecret();
				return Response.ok().entity(response).build();
			}else {
				return handleError(500, response.reason, null, logger);
			}
		} catch (IOException e) {
			return handleError(500,"Cannot connect to UFTPD server",e,logger);
		}	
	}
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/")
	public Response getCatchAll() {
		Response r = null;
		JSONObject o = new JSONObject();
		try{
			for(LogicalUFTPServer i : getAuthServiceProperties().getServers()){
				String name = i.getServerName();
				String url = kernel.getContainerProperties().getContainerURL()+"/rest/auth/"+name;
				JSONObject server = new JSONObject();
				server.put("href",url);
				server.put("description",i.getDescription());
				server.put("status",i.getConnectionStatusMessage());
				server.put("uftpdServerVersion",i.getVersion());
				int sessionLimit = i.getSessionLimit();
				if(sessionLimit>0) {
					server.put("sessionLimit", sessionLimit);
				}
				try{
					UserAttributes ua = assembleAttributes(i, null);
					if(ua.uid!=null)server.put("uid",ua.uid);
					if(ua.gid!=null)server.put("gid",ua.gid);
					if(ua.groups!=null)server.put("availableGroups",ua.groups);
					if(ua.rateLimit>0)server.put("rateLimit",ua.rateLimit);
					if(ua.includes!=null)server.put("allowed",ua.includes);
					if(ua.excludes!=null)server.put("forbidden",ua.excludes);
					List<String> reservationInfo = i.getActiveReservationInfo(ua.uid);
					if(reservationInfo.size()>0) {
						JSONArray jres = new JSONArray();
						reservationInfo.forEach( x-> jres.put(x));
						server.put("reservations", jres);
					}
				}catch(Exception ex){}
				server.put("dataSharing",getShareServiceInfo(name));
				o.put(name, server);
			}
			o.put("client", renderClientProperties());
			o.put("server", renderServerProperties());
			r = Response.ok().entity(o.toString(2)).build();
		}catch(Exception ex){
			r = handleError(500, "", ex, logger);
		}
		return r;
	}

	protected JSONObject getShareServiceInfo(String name) throws JSONException {
		JSONObject info = new JSONObject();
		ShareServiceProperties ssp = kernel.getAttribute(ShareServiceProperties.class);
		boolean enabled = ssp!=null && ssp.getDB(name)!=null;
		info.put("enabled", enabled);
		if(enabled){
			String url = kernel.getContainerProperties().getContainerURL()+"/rest/share/"+name;
			info.put("href", url);
		}
		return info;
	}


}
