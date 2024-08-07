package eu.unicore.uftp.datashare.share;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.InetAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import eu.emi.security.authn.x509.impl.X500NameUtils;
import eu.unicore.services.Kernel;
import eu.unicore.services.rest.client.BaseClient;
import eu.unicore.services.rest.client.IAuthCallback;
import eu.unicore.services.rest.client.UsernamePassword;
import eu.unicore.services.server.JettyServer;
import eu.unicore.uftp.authserver.messages.AuthRequest;
import eu.unicore.uftp.authserver.share.ShareServiceProperties;
import eu.unicore.uftp.datashare.db.ACLStorage;
import eu.unicore.uftp.datashare.db.ShareDAO;
import eu.unicore.uftp.server.UFTPServer;

public class TestShareService {
	
	Kernel k;
	
	@BeforeEach
	public void startKernel() throws Exception {
		FileUtils.deleteQuietly(new File("target/data"));
		k = new Kernel("src/test/resources/container.properties");
		k.startSynchronous();
	}
	
	@AfterEach
	public void shutdownKernel() throws Exception {
		k.shutdown();
	}
	
	
	@Test
	public void testSetup() throws Exception {
		checkShareDBSetup();
		checkGETInfo();
	}

	@Test
	public void testService() throws Exception {
		checkPOST();
		checkAccessViaAttributes();
		checkGetInfo();
	}
	
	@Test
	public void testDownload() throws Exception {
		checkDownload();
		checkDownloadPartial();
	}
	
	@Test
	public void testUpload() throws Exception {
		checkUpload();
	}
	
	private void checkShareDBSetup() throws Exception{
		ShareServiceProperties ssp = k.getAttribute(ShareServiceProperties.class);
		assertNotNull(ssp);
		ACLStorage acl = ssp.getDB("TEST");
		assertNotNull(acl);	
	}

	// do a get to find the configured servers
	private void checkGETInfo() throws Exception {
		JettyServer server=k.getServer();
		String url = server.getUrls()[0].toExternalForm()+"/rest";
		BaseClient bc = new BaseClient(url, k.getClientConfiguration(), getSharingUserAuth());
		String resource  = url+"/share";
		bc.setURL(resource);
		JSONObject o = bc.getJSON();
		JSONObject s = o.getJSONObject("TEST");
		String authUrl = s.getString("href");
		assertNotNull(authUrl);
		System.out.println(s);
		// GET shares for the "TEST" UFTP server
		bc.setURL(authUrl);
		System.out.println(bc.getJSON());

	}

	private void checkPOST() throws Exception {
		JettyServer server=k.getServer();
		String url = server.getUrls()[0].toExternalForm()+"/rest";
		BaseClient bc = new BaseClient(url, k.getClientConfiguration(), getSharingUserAuth());
		ShareServiceProperties ssp = k.getAttribute(ShareServiceProperties.class);
		ssp.getDB("TEST").deleteAllData();
		
		String resource  = url+"/share/TEST";
		JSONObject o = new JSONObject();
		o.put("path","/tmp/");
		o.put("access","WRITE");
		o.put("user","CN=Other User, O=Testing");
		o.put("group","hpc1");
		bc.setURL(resource);
		String loc = bc.create(o);
		System.out.println(loc);
		// check we have an entry
		ACLStorage acl = k.getAttribute(ShareServiceProperties.class).getDB("TEST");
		Collection<ShareDAO>entries = acl.readAll("/tmp/");
		assertEquals(1,entries.size());
		ShareDAO dao = entries.iterator().next();
		assertEquals("/tmp/",dao.getPath());
		assertEquals("WRITE",dao.getAccess());
		assertEquals(X500NameUtils.getComparableForm("CN=Other User, O=Testing"),dao.getTargetID());
		assertEquals(X500NameUtils.getComparableForm("CN=Demo User, O=UNICORE, C=EU"),dao.getOwnerID());
		assertEquals("user1",dao.getUid());
		assertEquals("hpc1",dao.getGid());

		resource  = url+"/share/TEST";
		// get the entry via the service
		bc.setURL(resource);
		JSONObject jShares = bc.getJSON();
		System.out.println(jShares);
		JSONArray shares = jShares.getJSONArray("shares");
		assertEquals(1,shares.length());

		// delete share by setting access to NONE
		o.put("access","NONE");
		bc.postQuietly(o);
		jShares = bc.getJSON();
		System.out.println(jShares);
		shares = jShares.getJSONArray("shares");
		assertEquals(0,shares.length());

	}


	private void checkAccessViaAttributes() throws Exception {
		JettyServer server=k.getServer();
		String url = server.getUrls()[0].toExternalForm()+"/rest";

		// as sharing user, create a share
		BaseClient bc = new BaseClient(url, k.getClientConfiguration(), getSharingUserAuth());
		String resource  = url+"/share/TEST";
		JSONObject o = new JSONObject();
		o.put("path","/tmp/");
		o.put("access","READ");
		o.put("user","CN=Other User, O=Testing");
		o.put("group","hpc1");
		bc.setURL(resource);
		bc.postQuietly(o);

		// as target user, authenticate a transfer
		bc = new BaseClient(url, k.getClientConfiguration(), getTargetUserAuth());
		resource  = url+"/access/TEST";
		AuthRequest req = new AuthRequest();
		req.serverPath="/tmp/foo";
		req.send = true;
		Gson gson = new GsonBuilder().create();
		JSONObject transferReq = new JSONObject(gson.toJson(req));
		System.out.println(transferReq);
		bc.setURL(resource);
		bc.postQuietly(transferReq);
		
		// as original sharing user, delete share by setting access to NONE
		bc = new BaseClient(url, k.getClientConfiguration(), getSharingUserAuth());
		resource  = url+"/share/TEST";
		o.put("access","NONE");
		bc.setURL(resource);
		bc.postQuietly(o);
	}
	
	private JSONObject createShare(File file, String access, String user) throws Exception {
		JettyServer server=k.getServer();
		String url = server.getUrls()[0].toExternalForm()+"/rest";
		BaseClient bc = new BaseClient(url, k.getClientConfiguration(), getSharingUserAuth());
		String resource  = url+"/share/TEST";
		JSONObject o = new JSONObject();
		o.put("path",file.getAbsolutePath());
		o.put("access",access);
		o.put("user",user);
		bc.setURL(resource);
		String shareLink = bc.create(o);
		System.out.println(shareLink);
		bc.setURL(shareLink);
		return bc.getJSON();
	}
	
	private void checkGetInfo() throws Exception {
		JSONObject share = createShare(new File("pom.xml"), "READ", "CN=Demo User, O=UNICORE, C=EU");
		System.out.println(share.toString(2));
	}
	
	private void checkDownload() throws Exception {
		String s = doDownload(0, -1);
		assertTrue(s.contains("modelVersion"));
	}

	private void checkDownloadPartial() throws Exception {
		String s = doDownload(2, 11);
		assertEquals("xml version",s);
	}

	private String doDownload(long offset, long length) throws Exception {
		File f = new File("./pom.xml");
		JSONObject share = createShare(f, "READ", "CN=Demo User, O=UNICORE, C=EU");
		String shareLink = share.getJSONObject("share").getString("http");
		BaseClient bc = new BaseClient(shareLink, k.getClientConfiguration(), getSharingUserAuth());
		Map<String,String>headers = new HashMap<>();

		if(length > 0) {
			String range = "bytes="+offset+"-"+(offset+length-1);
			headers.put("range", range);
		}
		// download the file
		bc.setURL(shareLink);
		try(ClassicHttpResponse r = bc.get(null, headers)){
			return EntityUtils.toString(r.getEntity());
		}
	}

	private void checkUpload() throws Exception {
		File f = new File("target/test-uploads");
		FileUtils.deleteQuietly(f);
		f.mkdirs();
		JSONObject share = createShare(f, "WRITE", "CN=Demo User, O=UNICORE, C=EU");
		System.out.println(share.toString(2));
		String shareLink = share.getJSONObject("share").getString("http");
		BaseClient bc = new BaseClient(shareLink, k.getClientConfiguration(), getSharingUserAuth());
		
		// upload file
		ByteArrayInputStream content = new ByteArrayInputStream("some content".getBytes());
		bc.setURL(shareLink+"/test.txt");
		bc.putQuietly(content, ContentType.APPLICATION_OCTET_STREAM);
		// check it
		assertTrue(FileUtils.readFileToString(new File(f, "test.txt"), "UTF-8").contains("some content"));
		// delete share via URL
		// bc.delete(shareLink);
	}

	
	private IAuthCallback getSharingUserAuth(){
		String userName = "demouser";
		String password = "test123";
		return new UsernamePassword(userName, password) ;
	}
	

	private IAuthCallback getTargetUserAuth(){
		String userName = "otheruser";
		String password = "test123";
		return new UsernamePassword(userName, password) ;
	}

	protected static UFTPServer server;
	static int cmdPort = 63321;
	static int listenPort = 63320;

	@BeforeAll
	public static void startUFTPD() throws Exception {
		InetAddress host = InetAddress.getByName("localhost");
		server = new UFTPServer(host, cmdPort, host, listenPort);
		Thread serverThread = new Thread(server);
		serverThread.start();
	}

	@AfterAll
	public static void stopUFTPD() throws Exception {
		server.stop();
	}
}
