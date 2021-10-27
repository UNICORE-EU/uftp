package eu.unicore.uftp.authserver;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import eu.unicore.security.Client;
import eu.unicore.security.Role;
import eu.unicore.security.SecurityException;
import eu.unicore.services.security.util.AuthZAttributeStore;
import eu.unicore.uftp.authserver.authenticate.UserAttributes;
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
		if(logger.isDebugEnabled())logger.debug("Incoming request from: "+clientIP+" "+json);

		AuthServiceProperties props = kernel.getAttribute(AuthServiceProperties.class);
		LogicalUFTPServer server = props.getServer(serverName);
		if(server == null){
			throw new WebApplicationException(404);
		}
		UserAttributes authData = null;
		AuthRequest authRequest = gson.fromJson(json, AuthRequest.class);
		
		try {
			UFTPDInstance uftpd = server.getUFTPDInstance();
			
			try{
				authData = assembleAttributes(uftpd.getServerName(), authRequest.group);
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
			response.secret = transferRequest.getSecret();
			return Response.ok().entity(response).build();
		} catch (IOException e) {
			return handleError(500,"Cannot connect to UFTPD server",e,logger);
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
		if(!haveServer(serverName)){
			throw new WebApplicationException(404);
		}

		String clientIP = AuthZAttributeStore.getTokens().getClientIP();
		if(logger.isDebugEnabled())logger.debug("Incoming tunnel request from: "+clientIP+" "+json);

		UserAttributes authData = null;
		CreateTunnelRequest tunnelRequest = gson.fromJson(json, CreateTunnelRequest.class);

		try {
			AuthServiceProperties props = kernel.getAttribute(AuthServiceProperties.class);
			LogicalUFTPServer server = props.getServer(serverName);
			UFTPDInstance uftpd = server.getUFTPDInstance();
			try{
				authData = assembleAttributes(uftpd.getServerName(), tunnelRequest.group);
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
			response.secret = transferRequest.getSecret();
			return Response.ok().entity(response).build();
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
				try{
					UserAttributes ua = assembleAttributes(i.getServerName(), null);
					if(ua.uid!=null)server.put("uid",ua.uid);
					if(ua.gid!=null)server.put("gid",ua.gid);
					if(ua.groups!=null)server.put("availableGroups",ua.groups);
					if(ua.rateLimit>0)server.put("rateLimit",ua.rateLimit);
					if(ua.includes!=null)server.put("allowed",ua.includes);
					if(ua.excludes!=null)server.put("forbidden",ua.excludes);
				}catch(Exception ex){}
				server.put("dataSharing",getShareServiceInfo(name));
				o.put(name, server);
			}
			JSONObject cl = new JSONObject();
			Client c = AuthZAttributeStore.getClient();
			cl.put("dn", c.getDistinguishedName());
			Role role = c.getRole();
			if(role!=null){
				Map<String,Object>rProps = new HashMap<String, Object>();
				rProps.put("selected",role.getName());
				rProps.put("availableRoles",role.getValidRoles());
				cl.put("role",rProps);
			}
			o.put("client", cl);
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
