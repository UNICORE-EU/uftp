package eu.unicore.uftp.authserver.reservations;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.server.FileWatcher;
import eu.unicore.util.Log;

/**
 * manages reservations for one UFTPD instance
 *
 * @author schuller
 */
public class Reservations {

	private final static Logger log = Log.getLogger("authservice", Reservations.class);

	private List<Reservation>reservations = new CopyOnWriteArrayList<>();

	private final String sourceFileName;

	public Reservations(String sourceFileName) throws FileNotFoundException {
		this.sourceFileName = sourceFileName;
		if(sourceFileName!=null) {
			loadReservations();
			setupWatchDog();
		}
		Utils.getExecutor().scheduleWithFixedDelay( () -> cleanup(),
				30, 30, TimeUnit.SECONDS);
	}

	public void cleanup() {
		List<Reservation>updatedReservations = new CopyOnWriteArrayList<>();
		for(Reservation r: reservations) {
			if(r.isTerminated()) {
				log.info("Reservation expired: {}", r);
			}
			else {
				updatedReservations.add(r);
			}
		}
		reservations = updatedReservations;
	}
	
	private void setupWatchDog() throws FileNotFoundException {
		FileWatcher fw = new FileWatcher(new File(sourceFileName), ()->{
			loadReservations();
		});
		fw.schedule(10, TimeUnit.SECONDS);
	}
	
	public synchronized void loadReservations() {
		List<Reservation> newReservations = new CopyOnWriteArrayList<>();
		try (FileInputStream is = new FileInputStream(new File(sourceFileName))){
			JSONObject json = new JSONObject(new JSONTokener(is));
			JSONArray r = json.optJSONArray("reservations");
			if(r!=null) {
				r.forEach( x -> {
					try {
						Reservation res = Reservation.fromJSON((JSONObject)x);
						if(!res.isTerminated()) {
							log.info("Loaded reservation: {}",res);
							newReservations.add(res);
						}
					}catch(Exception ex) {
						log.warn("Error parsing: "+String.valueOf(x));
					}
				});
			}
			reservations = newReservations;
		}catch(Exception e) {
			log.warn(Log.createFaultMessage("Error loading reservations from "+
					sourceFileName, e));
		}
	}
	
	public List<Reservation> getReservations(){
		return reservations;
	}
	
	/**
	 * get the lowest active rate limit for the given user, or 0 if there is no limit
	 *
	 * @param uid
	 */
	public long getRateLimit(String uid) {
		long limit = 0;
		for(Reservation r: reservations) {
			if(r.isActive()) {
				long rLimit = r.getRateLimit(uid);
				if(rLimit==0)return 0;
				if(limit>0) {
					limit = Math.min(limit, rLimit);
				}
				else {
					limit = rLimit;
				}
			}
		}
		return limit;
	}
}
