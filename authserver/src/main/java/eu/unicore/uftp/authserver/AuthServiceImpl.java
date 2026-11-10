package eu.unicore.uftp.authserver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import eu.unicore.security.SecurityException;
import eu.unicore.services.security.util.AuthZAttributeStore;
import eu.unicore.uftp.authserver.messages.AuthRequest;
import eu.unicore.uftp.authserver.messages.AuthResponse;
import eu.unicore.uftp.authserver.share.ShareServiceProperties;
import eu.unicore.util.Log;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * @author mgolik
 * @author schuller
 */
@Path("/")
public class AuthServiceImpl extends ServiceBase {

	private static final Logger logger = Log.getLogger("authservice", AuthServiceImpl.class);

	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	public Response listAll(@QueryParam("fields")String fields) throws Exception {
		try {
			parsePropertySpec(fields);
			return Response.ok().entity(getJSON().toString()).build();
		}catch(Exception ex) {
			return handleError("", ex, logger);
		}
	}

	@GET
	@Path("/")
	@Produces(MediaType.TEXT_HTML)
	public String listAllHtml() throws Exception {
		return getHTML();
	}

	@POST
	@Produces("application/json;charset=UTF-8")
	@Path("/{serverName}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response authenticateAndInitTransfer(@PathParam("serverName") String serverName, String json) {
		String clientIP = AuthZAttributeStore.getTokens().getClientIP();
		logger.debug("Incoming request from: {} {}", clientIP, json);
		try {
			UFTPBackend server = getLogicalServer(serverName);
			AuthRequest authRequest = gson.fromJson(json, AuthRequest.class);
			UFTPDInstance uftpd = getUFTPD(serverName);
			UserAttributes authData = null;
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
		} catch (Exception e) {
			return handleError(500,"Cannot connect to UFTPD server",e,logger);
		}	
	}

	@Override
	protected JSONObject getJSON() throws Exception {
		JSONObject o = super.getJSON();
		o.remove("_links"); // clients might choke on this, TBD
		return o;
	}

	@Override
	protected Map<String,Object> getProperties() throws Exception {
		Map<String,Object> o = super.getProperties();
		for(UFTPBackend i : getConfig().getServers()){
			String name = i.getServerName();
			String url = kernel.getContainerProperties().getContainerURL()+"/rest/auth/"+name;
			Map<String,Object> server = new HashMap<>();
			server.put("href",url);
			server.put("description",i.getDescription());
			server.put("status",i.getStatusDescription());
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
				List<String> jres = new ArrayList<>();
				reservationInfo.forEach( x-> jres.add(x));
				server.put("reservations", jres);
			}catch(Exception ex){}
			server.put("dataSharing",getShareServiceInfo(name));
			o.put(name, server);
		}
		return o;
	}

	protected Map<String,Object> getShareServiceInfo(String name) throws JSONException {
		Map<String,Object> info = new HashMap<>();
		ShareServiceProperties ssp = kernel.getAttribute(ShareServiceProperties.class);
		boolean enabled = ssp.getDB(name)!=null;
		info.put("enabled", enabled);
		if(enabled){
			String url = kernel.getContainerProperties().getContainerURL()+"/rest/share/"+name;
			info.put("href", url);
		}
		return info;
	}

}
