package eu.unicore.uftp.authserver.share;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.unicore.security.Client;

public interface IdentityExtractor {
	
	enum IdentityType {
		EMAIL,
		DN,
		CUSTOM
	}

	public String extractIdentity(Client client) throws Exception;

	public static class DNExtractor implements IdentityExtractor{
		public String extractIdentity(Client client) throws Exception {
			return client.getDistinguishedName();
		}
	}
	
	static DNExtractor dn_extractor = new DNExtractor(); 
	
	public static class EmailExtractor implements IdentityExtractor{

		private static final String emailRE = "([[\\w-]\\.]+)@([\\w\\.])+";
		
		private static final Pattern pattern = Pattern.compile(emailRE); 
		
		public static String extractEmail(String source) throws Exception {
			Matcher m = pattern.matcher(source);
			if(m.find()){
				return m.group();
			}
			return source;
		}
		
		public String extractIdentity(Client client) throws Exception {
			String dn = client.getDistinguishedName();
			return extractEmail(dn);
		}
	}
	
	public static class Factory {
		
		public static IdentityExtractor create(IdentityType type, Class<? extends IdentityExtractor>custom) throws Exception {
			if(IdentityType.EMAIL.equals(type)){
				return new EmailExtractor();
			}
			else if(IdentityType.DN.equals(type)){
				return dn_extractor;
			}
			else if(IdentityType.CUSTOM.equals(type)){
				return custom.getConstructor().newInstance();
			}else{
				throw new Exception("Extractor type is not known / not yet implemented");
			}
		}
	}
	
	
}