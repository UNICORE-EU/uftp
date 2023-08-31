package eu.unicore.uftp.authserver.reservations;

import static org.junit.Assert.*;

import java.text.DateFormat;
import java.util.Date;

import org.json.JSONObject;
import org.junit.Test;

import eu.unicore.services.utils.UnitParser;
import eu.unicore.uftp.authserver.reservations.Reservation;
import eu.unicore.uftp.authserver.reservations.Reservations;

public class TestReservations {

	@Test
	public void testSingleReservation() throws Exception {
		DateFormat df = UnitParser.getSimpleDateFormat();
		String now = df.format(new Date());
		String oneHour = df.format(new Date(System.currentTimeMillis()+1000*3600));
		JSONObject o = new JSONObject();
		o.put("name", "test123");
		o.put("from", now);
		o.put("to", oneHour);
		o.put("uid", "me");
		o.put("rateLimit", "100");
		System.out.println(o);
		Reservation r = Reservation.fromJSON(o);
		System.out.println(r);
		assertTrue(r.isActive());
		assertTrue(r.isOwner("me"));
		assertFalse(r.isOwner("you"));
		assertEquals(0, r.getRateLimit("me"));
		assertEquals(100, r.getRateLimit("someone_else"));
	}
	
	@Test
	public void testReservations() throws Exception {
		Reservations rr = new Reservations(null);
		Reservation r1 = new Reservation(
				System.currentTimeMillis()-10,
				System.currentTimeMillis()+1000*300,
				1024*1024,
				"me","you");
		assertTrue(r1.isActive());
		Reservation r2 = new Reservation(
				System.currentTimeMillis()-10,
				System.currentTimeMillis()+1000*300,
				10*1024*1024,
				"user1","user2");
		assertTrue(r2.isActive());
		Reservation old = new Reservation(
				System.currentTimeMillis()-1000*3600,
				System.currentTimeMillis()-1000*300,
				1024*1024,
				"no1","no2");
		assertFalse(old.isActive());
		rr.getReservations().add(r1);
		rr.getReservations().add(r2);
		rr.getReservations().add(old);
		assertEquals(3, rr.getReservations().size());

		long limit = rr.getRateLimit("hpc1");
		assertEquals(1024*1024, limit);
		limit = rr.getRateLimit("me");
		assertEquals(0, limit);
		limit = rr.getRateLimit("no2");
		assertEquals(1024*1024, limit);

		rr.cleanup();
		assertEquals(2, rr.getReservations().size());
	}

}
