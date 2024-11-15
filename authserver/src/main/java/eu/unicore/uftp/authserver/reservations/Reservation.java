package eu.unicore.uftp.authserver.reservations;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;

import eu.unicore.services.restclient.utils.UnitParser;
import eu.unicore.uftp.authserver.UserAttributes;

public class Reservation {

	private String name;

	private long from;

	private long to;
	
	private String rateLimitS = "10m";
	private long rateLimit;

	private final Set<String>uids = new HashSet<>();

	public Reservation(){}

	public Reservation(long from, long to, long rateLimit, String ... logins){
		this.from = from;
		this.to = to;
		this.rateLimit = rateLimit;
		for(String x: logins) {
			uids.add(x);
		}
	}

	public boolean isTerminated() {
		return System.currentTimeMillis()>to;
	}

	public boolean isActive() {
		long now = System.currentTimeMillis();
		return now >from && now < to;
	}

	/**
	 * return the rate limit for the given uid, or 0 if not limited by this reservation
	 */
	public long getRateLimit(String uid) {
		return uids.contains(uid) ? 0 : rateLimit;
	}

	public boolean isOwner(String uid) {
		return uids.contains(uid);
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("'").append(name).append("': users=").append(uids.toString());
		sb.append(" from=").append(new Date(from));
		sb.append(" to=").append(new Date(to));
		sb.append(" other_users_limit=").append(rateLimitS);
		return sb.toString();
	}

	public JSONObject toJSON() {
		JSONObject j = new JSONObject();
		j.put("name",name);
		j.put("from", UnitParser.getSimpleDateFormat().format(new Date(from)));
		j.put("to", UnitParser.getSimpleDateFormat().format(new Date(to)));
		j.put("rateLimit", UnitParser.getCapacitiesParser(0).getHumanReadable(rateLimit));
		JSONArray jUids = new JSONArray();
		uids.forEach( (x) -> jUids.put(x) );
		j.put("uids", jUids);
		return j;
	}

	public static Reservation fromJSON(JSONObject json) {
		Reservation r = new Reservation();
		r.name = json.optString("name", UUID.randomUUID().toString());
		r.from = UnitParser.extractDateTime(json.getString("from")).getTime();
		r.to = UnitParser.extractDateTime(json.getString("to")).getTime();
		JSONArray logins = json.optJSONArray("uids");
		if(logins!=null) {
			logins.forEach( (x) -> r.uids.add(String.valueOf(x)) );
		}
		String login = json.optString("uid", null);
		if(login!=null) {
			r.uids.add(login);
		}
		r.rateLimitS = json.optString("rateLimit", "10m");
		r.rateLimit = UserAttributes.parseRate(r.rateLimitS);
		if(r.from<=0 || r.to<=0 || r.to<r.from || r.rateLimit<0) {
			throw new IllegalArgumentException();
		}
		return r;
	}

}
