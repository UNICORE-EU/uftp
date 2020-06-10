package eu.unicore.uftp.standalone.util;

import java.io.IOException;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import eu.unicore.uftp.authserver.authenticate.AuthData;
import eu.unicore.uftp.standalone.authclient.HttpClientFactory;

/**
 * Support for working with REST, JSON and all that
 * 
 * TODO should use a common base client class for all things REST
 *
 * @author schuller
 */
public class BaseClient {

	protected final HttpClient client;

	protected StatusLine status;

	protected AuthData authCallback;

	public BaseClient(String url, AuthData authCallback){
		HttpClient client = HttpClientFactory.getClient(url);
		this.client = client;
		this.authCallback = authCallback;
	}

	protected void addAuth(HttpMessage message) throws Exception {
		if(authCallback!=null){
			for(Map.Entry<String, String> e : authCallback.getHttpHeaders().entrySet()){
				message.addHeader(e.getKey(), e.getValue());
			}
		}
	}

	/**
	 * get the JSON representation of the given resource
	 * 
	 * @param resource - the URL of the resource to get 
	 * @return {@link JSONObject}
	 * @throws Exception
	 */
	public JSONObject getJSON(String resource) throws Exception {
		HttpResponse response = get(resource);
		checkError(response);
		return asJSON(response);
	}

	public HttpResponse get(String resource) throws Exception {
		HttpGet get=new HttpGet(resource);
		get.setHeader("Accept", "application/json");
		return execute(get);
	}

	/**
	 * post the JSON to the given resource and return the response
	 * 
	 * @param resource - the URL of the resource to get 
	 * @throws Exception
	 */
	public HttpResponse post(JSONObject content, String resource) throws Exception {
		HttpPost post=new HttpPost(resource);
		post.setHeader("Content-Type", "application/json");
		if(content!=null)post.setEntity(new StringEntity(content.toString(), ContentType.APPLICATION_JSON));
		HttpResponse response = execute(post);
		return response;
	}

	/**
	 * post (discarding any response)
	 * @param content
	 * @param resource
	 * @throws Exception
	 */
	public void postQuietly(JSONObject content, String resource) throws Exception {
		HttpResponse response = post(content, resource);
		checkError(response);
		EntityUtils.consume(response.getEntity());
		close(response);
	}

	public void delete(String resource) throws Exception {
		HttpDelete d=new HttpDelete(resource);
		HttpResponse response = execute(d);
		EntityUtils.consume(response.getEntity());
		checkError(response);
		close(response);
	}

	/**
	 * get the HTTP {@link StatusLine} of the last invocation
	 */
	public StatusLine getLastStatus(){
		return status;
	}

	/**
	 * get the HTTP status code of the last invocation
	 */
	public int getLastHttpStatus(){
		return status!=null? status.getStatusCode() : -1;
	}

	public JSONObject asJSON(HttpResponse response) throws IOException, JSONException {
		try{
			String reply = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
			return new JSONObject(reply);
		}
		finally{
			close(response);
		}
	}

	protected HttpResponse execute(HttpRequestBase method) throws Exception {
		addAuth(method);
		HttpResponse response = client.execute(method);
		status = response.getStatusLine();
		return response;
	}

	protected void close(HttpResponse response) throws IOException {
		if(response instanceof CloseableHttpResponse){
			((CloseableHttpResponse)response).close();
		}
	}

	public void checkError(HttpResponse response) throws Exception {
		if(response.getStatusLine().getStatusCode()>399){
			String message = buildErrorMessage(response);
			close(response);			
			throw new Exception(message);
		}
	}

	protected String buildErrorMessage(HttpResponse response) {
		String body = null;
		if(response.getEntity()!=null){
			try{
				body = EntityUtils.toString(response.getEntity());
			}catch(IOException ex){
				body = "n/a";
			}
		}
		return "Error: "+response.getStatusLine()+(body!=null?body:"");
	}

}
