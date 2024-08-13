package eu.unicore.uftp.authserver.messages;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;

/**
 *
 * @author jrybicki
 */
public class AuthResponseBodyProvider implements MessageBodyWriter<AuthResponse> {

	final static Gson GSON;

	static {
		GsonBuilder gsonBuilder = new GsonBuilder();
		GSON = gsonBuilder.create();
	}

	@Override
	public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
		return AuthResponse.class.isAssignableFrom(type);
	}

	@Override
	public long getSize(AuthResponse t, Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
		return -1;
	}

	@Override
	public void writeTo(AuthResponse t, Class<?> type, Type type1, Annotation[] antns, MediaType mt, MultivaluedMap<String, Object> mm, OutputStream out) throws IOException, WebApplicationException {
		try (OutputStreamWriter osw = new OutputStreamWriter(out, "UTF-8")){
			GSON.toJson(t, osw);
		}
	}

}
