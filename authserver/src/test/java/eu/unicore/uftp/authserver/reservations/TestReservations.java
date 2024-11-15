package eu.unicore.uftp.authserver.reservations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import eu.unicore.services.restclient.utils.UnitParser;

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
		rr.reservations.add(r1);
		rr.reservations.add(r2);
		rr.reservations.add(old);
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

	@Test
	public void testLoadFromFile() throws Exception {
		Reservation r1 = new Reservation(
				System.currentTimeMillis()-10,
				System.currentTimeMillis()+1000*300,
				1024*1024,
				"me","you");
		assertTrue(r1.isActive());
		System.out.println(r1.toJSON().toString(2));
		JSONObject jRes = new JSONObject();
		JSONArray jArr = new JSONArray();
		jArr.put(r1.toJSON());
		String invalid = "{'from': '2024-01-01 12:00', 'to': '2023-01-01 12:00',"
				+ "'rateLimit': '1m', 'uid':'noone'}";
		jArr.put(new JSONObject(invalid));
		jRes.put("reservations", jArr);
		File resFile = new File("target", "test_reservations");
		FileUtils.writeStringToFile(resFile, jRes.toString(2), "UTF-8");
		Reservations r = new Reservations(resFile.getPath());
		assertEquals(1, r.getReservations().size());
		long limit = r.getRateLimit("someone");
		assertEquals(1024*1024, limit);
	}

}
