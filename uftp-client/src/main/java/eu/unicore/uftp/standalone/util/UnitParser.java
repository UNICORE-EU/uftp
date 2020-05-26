package eu.unicore.uftp.standalone.util;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * helper for working with common units and multipliers
 * 
 * @author schuller
 */
public class UnitParser {

	private static Pattern valueWithUnitsPattern=Pattern.compile("\\s*(\\d*[\\.\\,]?\\d*)\\s*(\\w*)\\s*");

	private final String[][]units;
	private final int[]conversionFactors;
	private final NumberFormat outputFormat;
	private final int decimalDigits;

	/**
	 * 
	 * @param units - unit names (with synonyms)
	 * @param conversionFactors - conversion factors
	 * @param decimalDigits - the number of decimal digits to use
	 */
	public UnitParser(String[][]units,int[]conversionFactors, int decimalDigits){
		this.units=units;
		this.conversionFactors=conversionFactors;
		this.outputFormat=NumberFormat.getNumberInstance();
		outputFormat.setMaximumFractionDigits(decimalDigits);
		outputFormat.setMinimumFractionDigits(decimalDigits);
		this.decimalDigits=decimalDigits;
	}

	public UnitParser(String[][]units,int[]conversionFactors){
		this(units,conversionFactors,0);
	}

	/**
	 * convert a value with units to the value in default units
	 * @param valueWithUnits
	 * @return String representation (without units!)
	 */
	public String getStringValue(String valueWithUnits){
		return outputFormat.format(getDoubleValue(valueWithUnits));
	}

	/**
	 * get the value (in default units)
	 * @param valueWithUnits
	 * @return double
	 */
	public double getDoubleValue(String valueWithUnits){
		String u=getUnits(valueWithUnits);
		int conversion=1;
		if(u!=null){
			conversion=getConversionFactor(u);
		}
		return Double.parseDouble(getNumber(valueWithUnits))*conversion;
	}

	/**
	 * return the "optimal" string representation of the given value
	 * 
	 * @param d - the value to represent
	 * @param forceUnit - to always print the unit, even if the conversion factor is "1"
	 */
	public String getHumanReadable(double d, boolean forceUnit){
		long factor=1;
		String unit="";
		double converted=d;
		for(int c=0;c<conversionFactors.length;c++){
			factor*=conversionFactors[c];
			if(c<conversionFactors.length && converted<conversionFactors[c]){
				break;
			}
			unit=units[c+1][0];
			converted=d/factor;
		}
		if("".equals(unit)){
			outputFormat.setMinimumFractionDigits(0);
			if(forceUnit){
				unit=units[0][0];
			}
		}
		
		String res=outputFormat.format(converted)+unit;
		outputFormat.setMinimumFractionDigits(decimalDigits);
		return res;
	}
	
	public String getHumanReadable(double d){
		return getHumanReadable(d,false);
	}

	public void setMinimumDigits(int digits){
		outputFormat.setMinimumFractionDigits(digits);
	}

	int getConversionFactor(String unit){
		int i=0;
		boolean found=false;
		outer: for(String[] u: units){
			for(String u1: u){
				if(u1.startsWith(unit)){
					found=true;
					break outer;
				}
			}
			i++;
		}
		if(found){
			int result=1;
			for(int j=0;j<i;j++)result*=conversionFactors[j];
			return result;
		}
		else{
			throw new IllegalArgumentException("No conversion for unit '"+unit+"'");
		}
	}

	/**
	 * extract the numerical part of the argument
	 * @param valueWithUnits
	 */
	String getNumber(String valueWithUnits){
		Matcher m=valueWithUnitsPattern.matcher(valueWithUnits);
		if(m.matches()){
			return m.group(1);
		}
		else throw new NumberFormatException("not a parsable value: "+valueWithUnits);
	}

	/**
	 * extract the unit part of the argument
	 * @param valueWithUnits
	 */
	String getUnits(String valueWithUnits){
		Matcher m=valueWithUnitsPattern.matcher(valueWithUnits);
		if(m.matches()){
			return m.group(2);
		}
		else return null;
	}

	static String[][] capacityUnits={
		{"","bytes"},
		{"K","kilobytes","kb"}, 
		{"M","megabytes","mb"}, 
		{"G","gigabytes","gb"}, 
		{"T","terabytes","tb"}
	};

	static int[] capacityFactors=new int[]{1024,1024,1024,1024};

	static String[][] timeUnits={
		{"sec","seconds",},
		{"min","minutes",}, 
		{"h","hours",}, 
		{"d","days",}, 
	};

	static int[] timeFactors=new int[]{60,60,24};

	/**
	 * get a new parser instance suitable for parsing capacity units (K, M, G etc)
	 * @param decimalDigits
	 */
	public static UnitParser getCapacitiesParser(int decimalDigits){
		return new UnitParser(capacityUnits,capacityFactors,decimalDigits);
	}

	/**
	 * get a new parser instance suitable for parsing time units (seconds, minutes, hours, days)
	 * @param decimalDigits
	 */
	public static UnitParser getTimeParser(int decimalDigits){
		return new UnitParser(timeUnits,timeFactors,decimalDigits);
	}

	/**
	 * parses a time from the given string<br/>
	 * 
	 * understands a number of date/time formats such as ISO8601, HH:mm, etc
	 * 
	 * @param spec - the date specification
	 */
	@SuppressWarnings("deprecation")
	public static synchronized Date extractDateTime(String spec){
		Date result=null;
		try{
			Date d1=getHHMMDate().parse(spec);
			Calendar c=Calendar.getInstance();
			c.set(Calendar.HOUR_OF_DAY, d1.getHours());
			c.set(Calendar.MINUTE, d1.getMinutes());
			if(c.compareTo(Calendar.getInstance())<0){
				//interpret time as "on the next day"
				c.add(Calendar.DATE, 1);
			}
			result=c.getTime();
		}catch(ParseException pe){}
		
		if(result==null){
			try{
				result=getSimpleDateFormat().parse(spec);
			}catch(ParseException pe){}
		}
		
		if(result==null){
			try{
				result=getISO8601().parse(spec);
			}catch(ParseException pe){}
		}
		if(result!=null)return result;//.getTime();
		else throw new IllegalArgumentException("Specified date string '"+spec+"'could not be parsed!");
	}

	public static String convertDateToISO8601(String dateSpec){
		return getISO8601().format(extractDateTime(dateSpec));
	}
	
	private static DateFormat hhmmDate;
	
	/**
	 * get the static DateFormat instance for the "HH:mm" format<br/>
	 * Not threadsafe!
	 */
	public static DateFormat getHHMMDate(){
		if(hhmmDate==null){
			hhmmDate=new SimpleDateFormat("HH:mm");
		}
		return hhmmDate;
	}
	
	private static DateFormat iso8601;
	
	/**
	 * gets the static DateFormat instance for the ISO8601 "yyyy-MM-dd'T'HH:mm:ssZ" format<br/>
	 * Not threadsafe!
	 */
	public static DateFormat getISO8601(){
		if(iso8601==null){
			iso8601=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		}
		return iso8601;
	}

	private static DateFormat simple;
	
	/**
	 * get the static DateFormat instance for the "yyyy-MM-dd HH:mm" format<br/>
	 * Not threadsafe!
	 */
	public static DateFormat getSimpleDateFormat(){
		if(simple==null){
			simple=new SimpleDateFormat("yyyy-MM-dd HH:mm");
		}
		return simple;
	}

}
