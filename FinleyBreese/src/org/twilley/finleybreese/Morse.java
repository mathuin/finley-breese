package org.twilley.finleybreese;

import java.util.ArrayList;
import java.util.HashMap;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.Math;

/**
 * This class combines some audio file wizardry with Morse code skills.
 * @author jmt
 *
 */

public class Morse {
	// class implementation comment
	
	// public interface
	// public constants, class and instance variables
	// public constructors
	public Morse(int startPitch, int startCspeed, int startSpeed, int startBitwidth, int startSamplerate, int startChannels) {
		pitch = startPitch;
		if (startCspeed < startSpeed)
			startCspeed = startSpeed;
		cspeed = startCspeed;
		speed = startSpeed;
		if (bitwidth % 8 != 0)
			bitwidth = (int) (8 * Math.ceil(bitwidth/8.0));
		bitwidth = startBitwidth;
		samplerate = startSamplerate;
		channels = startChannels;

		elements = buildElements();
	}
	
	public Morse(int startPitch, int startCspeed, int startSpeed) {
		this(startPitch, startCspeed, startSpeed, DEFAULT_BITWIDTH, DEFAULT_SAMPLERATE, DEFAULT_CHANNELS);
	}

	public Morse() {
		this(DEFAULT_PITCH, DEFAULT_CSPEED, DEFAULT_SPEED, DEFAULT_BITWIDTH, DEFAULT_SAMPLERATE, DEFAULT_CHANNELS);
	}
	
	// public methods
	public void createFile(File file, String input) throws IOException {
		ArrayList<String> bits = makeSyllables(input);
		byte[] samples = buildSamples(bits);
		writeFile(file, samples);
	}
	
	// public nested classes and interfaces
	
	// package interface
	// package constants, class and instance variables
	// package constructors
	// package methods
	// package nested classes and interfaces
	
	// protected
	
	// private constants
	// default values for instance variables
	private static final int DEFAULT_PITCH = 800;
	private static final int DEFAULT_CSPEED = 20;
	private static final int DEFAULT_SPEED = 13;
	private static final int DEFAULT_BITWIDTH = 32;
	private static final int DEFAULT_SAMPLERATE = 44100;
	private static final int DEFAULT_CHANNELS = 2;
	
	// the length of the word 'paris' in dits
	private static final int PARIS_LENGTH = 50;
	
	// what fraction of full range is used for the tone
	private static final double MAX_AMPLITUDE = 0.85;
	// how many seconds from zero to max amplitude
	private static final double SLOPE = 0.005;

	// private class variable
	private static final HashMap<Character, String> chardict = initializeChardict();
		
	// private instance variables
	private int pitch;
	private int cspeed;
	private int speed;
	private int bitwidth;
	private int samplerate;
	private int channels;	
	private HashMap<String, byte[]> elements;

	// build byte arrays for different elements!
	private HashMap<String, byte[]> buildElements() {
		HashMap<String, byte[]> myelements = new HashMap<String, byte[]>();
		
		int lendit;
		int farndit;
		
		// calculate lengths
		lendit = samplerate * 60 / (PARIS_LENGTH * cspeed);
		
		if (cspeed < speed) {
			double ta = 60 / (double) speed - 37.2 / (double) cspeed;
			farndit = (int) (samplerate * ta) / 19;
		} else {
			farndit = samplerate * 60 / (PARIS_LENGTH * speed);
		}
		
		// create and populate arrays
		myelements.put("dit", tone(lendit, pitch));
		myelements.put("dah", tone(lendit * 3, pitch));
		myelements.put("ies", tone(lendit, 0));
		myelements.put("ics", tone(farndit * 3, 0));
		myelements.put("iws", tone(farndit * 7, 0));
		
		return myelements;
	}
	
	// initializes class variable
	private static HashMap<Character, String> initializeChardict() {
		HashMap<Character, String> mydict = new HashMap<Character, String>();
		
		mydict.put('a', ".-");
		mydict.put('b', "-...");
		mydict.put('c', "-.-."); 
		mydict.put('d', "-..");
		mydict.put('e', ".");
		mydict.put('f', "..-.");
		mydict.put('g', "--.");
		mydict.put('h', "....");
		mydict.put('i', "..");
		mydict.put('j', ".---");
		mydict.put('k', "-.-");
		mydict.put('l', ".-..");
		mydict.put('m', "--");
		mydict.put('n', "-.");
		mydict.put('o', "---");
		mydict.put('p', ".--.");
		mydict.put('q', "--.-");
		mydict.put('r', ".-.");
		mydict.put('s', "...");
		mydict.put('t', "-");
		mydict.put('u', "..-");
		mydict.put('v', "...-");
		mydict.put('w', ".--");
		mydict.put('x', "-..-");
		mydict.put('y', "-.--");
		mydict.put('z', "--..");
		mydict.put('0', "-----");
		mydict.put('1', ".----");
		mydict.put('2', "..---");
		mydict.put('3', "...--");
		mydict.put('4', "....-");
		mydict.put('5', ".....");
		mydict.put('6', "-....");
		mydict.put('7', "--...");
		mydict.put('8', "---..");
		mydict.put('9', "----.");
		mydict.put('.', ".-.-.-");
		mydict.put(',', "--..--");
		mydict.put('?', "..--..");
		mydict.put(':', "---...");
		mydict.put(';', "-.-.-.");
		mydict.put('-', "-....-");
		mydict.put('/', "-..-.");
		mydict.put('\"', ".-..-.");
		mydict.put('+', ".-.-.");
		mydict.put('|', ".-...");
		mydict.put('>', "-.--.");
		mydict.put('~', "...-.-");
		mydict.put('=', "-...-");
		mydict.put('@', ".--.-.");
		
		return mydict;
	}
	
	// generate tone based on frequency and length
	private byte[] tone(int length, int frequency) {
		int samplewidth = bitwidth/8;
		byte[] mytone = new byte[length * channels * samplewidth];
		double grade = SLOPE * MAX_AMPLITUDE * samplerate;
		double maxvalue = MAX_AMPLITUDE * Math.pow(2, bitwidth-1); 

		for (int index = 0; index < length; index++) {
			for (int c = 0; c < channels; c++) {
				double elem = maxvalue * Math.sin(frequency * Math.PI * 2 * index / samplerate);
				if (index < grade)
					elem *= (index / grade);
				else if ((length - index) < grade)
					elem *= ((length - index) / grade);
				for (int s = 0; s < samplewidth; s++)
					mytone[(channels * index + c) * samplewidth + s] = (byte) (((int) elem >> (8 * s)) & 0xff);
			}
		}
		return mytone;
	}
	
	private ArrayList<String> makeSyllables(String input) {
		ArrayList<String> retval = new ArrayList<String>();
		String letter;
		boolean spaceflag = true;
		
		for (char c: input.toCharArray()) {
			if (Character.isWhitespace(c) && !spaceflag) {
				retval.add("iws");
				spaceflag = true;
			} else {
				if (chardict.containsKey(c)) {
					letter = chardict.get(c);
					if (!spaceflag)
						retval.add("ics");
					else
						spaceflag = false;
					for (char el: letter.toCharArray()) {
						if (el == '.')
							retval.add("dit");
						else
							retval.add("dah");
						retval.add("ies");
					}
					retval.remove(retval.lastIndexOf("ies"));
				}
			}
		}
		return retval;
	}
	
	// construct sample array from syllables
	private byte[] buildSamples(ArrayList<String> bits) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		
		for (String s: bits) {
			output.write(elements.get(s));
		}
		
		return output.toByteArray();
	}
	
	// construct WAV file 
	private void writeFile(File file, byte[] samples) throws IOException {
		file.getParentFile().mkdirs();
		file.createNewFile();
		FileOutputStream out = new FileOutputStream(file);
				
		byte[] header = new byte[44];
        int blockalign = channels * bitwidth/8;
        int byterate = samplerate * blockalign;
        
        // may be worth writing int->4byte, string->byte, etc

        int audiolen = samples.length;
        // 36 is everything but the data, 'RIFF' and 'WAVE'.
        int datalen = audiolen + 36;
        
        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (datalen & 0xff);
        header[5] = (byte) ((datalen >> 8) & 0xff);
        header[6] = (byte) ((datalen >> 16) & 0xff);
        header[7] = (byte) ((datalen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (samplerate & 0xff);
        header[25] = (byte) ((samplerate >> 8) & 0xff);
        header[26] = (byte) ((samplerate >> 16) & 0xff);
        header[27] = (byte) ((samplerate >> 24) & 0xff);
        header[28] = (byte) (byterate & 0xff);
        header[29] = (byte) ((byterate >> 8) & 0xff);
        header[30] = (byte) ((byterate >> 16) & 0xff);
        header[31] = (byte) ((byterate >> 24) & 0xff);
        header[32] = (byte) blockalign;  // block align
        header[33] = 0;
        header[34] = (byte) bitwidth;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (audiolen & 0xff);
        header[41] = (byte) ((audiolen >> 8) & 0xff);
        header[42] = (byte) ((audiolen >> 16) & 0xff);
        header[43] = (byte) ((audiolen >> 24) & 0xff);
        out.write(header);
        
        // now write the samples
        out.write(samples); 
        
        out.close();
	}
}
