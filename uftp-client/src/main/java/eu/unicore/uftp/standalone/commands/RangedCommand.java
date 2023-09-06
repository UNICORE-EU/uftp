package eu.unicore.uftp.standalone.commands;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import eu.unicore.uftp.standalone.util.RangeMode;
import eu.unicore.uftp.standalone.util.UnitParser;

public abstract class RangedCommand extends Command {

	//index of first byte to process
	protected Long startByte;

	//index of last byte to process
	protected Long endByte;

	protected RangeMode rangeMode = RangeMode.READ;

	protected Options getOptions() {
		Options options = super.getOptions();
		options.addOption(Option.builder("B").longOpt("bytes")
				.desc("Byte range")
				.required(false)
				.hasArg()
				.build());
		return options;
	}
	
	protected void initRange(String rangeSpec) {
		String[]tokens = rangeSpec.split("-");
		try{
			if(tokens.length>1) {
				String start = tokens[0];
				String end = tokens[1];
				if(start.length()>0){
					startByte = (long)(UnitParser.getCapacitiesParser(0).getDoubleValue(start));
					endByte = Long.MAX_VALUE;
				}
				if(end.length()>0){
					endByte = (long)(UnitParser.getCapacitiesParser(0).getDoubleValue(end));
					if(startByte==null){
						startByte = Long.valueOf(0l);
					}
				}
				// optional mode
				if(tokens.length>2){
					String m = tokens[2];
					if("p".equalsIgnoreCase(m)){
						rangeMode = RangeMode.READ_WRITE;
					}
				}
			}
			else {
				String end=tokens[0];
				endByte=(long)(UnitParser.getCapacitiesParser(0).getDoubleValue(end));
				startByte=Long.valueOf(0l);
			}
		}catch(Exception e){
			throw new IllegalArgumentException("Could not parse byte range <"+rangeSpec+">");
		}
	}

	protected boolean haveRange(){
		return startByte!=null;
	}
	
	protected long getOffset(){
		long offset = 0;
		if(startByte!=null) {
			offset = startByte.longValue();
		}
		return offset;
	}

	protected long getLength(){
		long length = -1;
		if(startByte!=null) {
			length = endByte - startByte + 1;
		}
		return length;
	}
}
