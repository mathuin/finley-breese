package org.twilley.finleybreese;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.content.ContentValues;
import android.content.res.Resources;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Contacts;
import android.provider.Contacts.People;
import android.util.Log;

public class FinleyBreeseActivity extends Activity {
    private static final String TAG = "FinleyBreese";
	private static boolean logging = true;
	
	// resource access
	Resources res;
	// resource values
	int bitwidth;
	int samplerate;
	int channels;
	int pitch;
	int cspeed;
	int speed;
	int lenies;
	int lenics;
	int leniws;
	// hashes
	private HashMap<String, String> rthash = new HashMap<String, String>();
	private HashMap<Character, String> morse = new HashMap<Character, String>();
	// waveform data
	private byte[] dit;
	private byte[] dah;
	private byte[] ies;
	private byte[] ics;
	private byte[] iws;

	/** Called when the activity is first created. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        if (logging == true)
        	Log.v(TAG, "entered onCreate");

        // Populate a hash with keys of ringtone URIs and values of ringtone names
    	RingtoneManager rtm = new RingtoneManager(this);
    	rtm.setType(RingtoneManager.TYPE_RINGTONE);
    	Cursor rtc = rtm.getCursor();
    	if (rtc.moveToFirst()) {
    		do {
    			String rturi = rtc.getString(RingtoneManager.URI_COLUMN_INDEX);
    			String rtid = rtc.getString(RingtoneManager.ID_COLUMN_INDEX);
    			String key = rtc.getString(RingtoneManager.TITLE_COLUMN_INDEX);
    			String value = rturi + "/" + rtid;
    			if (logging == true) 
    				Log.d(TAG, "rthash: key = " + key + ", value = " + value);
    			rthash.put(key, value);
    		} while (rtc.moveToNext());
    	} else {
    		if (logging == true)
    			Log.w(TAG, "no ringtones found");
    	}
    	
    	// deal with resources
    	res = getResources();
    	int[] statsarr = res.getIntArray(R.array.stats);
    	bitwidth = statsarr[0];
    	samplerate = statsarr[1];
    	channels = statsarr[2];
    	pitch = statsarr[3];
    	cspeed = statsarr[4];
    	speed = statsarr[5];
    	lenies = statsarr[6];
    	lenics = statsarr[7];
    	leniws = statsarr[8];
    	
    	// Populate morse hashmap
    	morseSetup();
    	
    	// Construct lists
    	listSetup();
    }

    /** Called when the activity is becoming visible to the user. */
    @Override
    protected void onStart() {
    	super.onStart();
    	if (logging == true)
    		Log.v(TAG, "entered onStart");
    }
    
    /** Called after your activity has been stopped, prior to it being started again. */
    @Override
    protected void onRestart() {
    	super.onRestart();
    	if (logging == true)
    		Log.v(TAG, "entered onRestart");
    }

    /** Called when the activity will start interacting with the user. */
    @Override
    protected void onResume() {
    	super.onResume();
    	if (logging == true)
    		Log.v(TAG, "entered onResume");
    	// activity is currently on the top of the stack, with user activity
		String rtstring;
    	File rtpath;

		// Confirm availability of external storage.
		boolean mExternalStorageAvailable = false;
		boolean mExternalStorageWriteable = false;
		String state = Environment.getExternalStorageState();
		
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			// We can read and write the media
			Log.d(TAG, "media mounted");
			rtpath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES);
			mExternalStorageAvailable = mExternalStorageWriteable = true;
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			// We can only read the media
			Log.w(TAG, "media mounted read only");
			rtpath = new File("/sdcard/");
			mExternalStorageAvailable = true;
			mExternalStorageWriteable = false;
		} else {
			// Something else is wrong
			Log.e(TAG, "external storage unavailable");
			rtpath = new File("/sdcard/");
			mExternalStorageAvailable = mExternalStorageWriteable = false;
		}
		if (rtpath.exists()) {
			if (rtpath.isDirectory()) {
					Log.v(TAG, "rtpath is a directory");
			} else {
					Log.e(TAG, "rtpath is not a directory!");
			}
		} else {
			rtpath.mkdirs();
			Log.w(TAG, "created rtpath directory");
		}
		
        // Create a regular expression pattern that matches the expected string
		// \uFF1A is "full width colon".
		// The space after the colon is optional.
		// The character set in the group should include all Morse characters.
    	String NotesRegex = "ringtone[:\uFF1A][ ?]([A-Za-z0-9 ]*)";
    	Pattern pattern = Pattern.compile(NotesRegex);

        // query the contacts list
        // (someday use a projection with just what we want for performance reasons)
        Cursor pc = managedQuery(Contacts.People.CONTENT_URI, null, null, null, null);
        
        // iterate over the contacts list
        if (pc.moveToFirst()) {
        	do {
    			// do they have any notes?
        		String pnotes = pc.getString(pc.getColumnIndex(Contacts.PeopleColumns.NOTES));
    			if (pnotes == null) {
    				// Log.v(TAG, "has no notes");
    				continue;
    			} else {
    				Log.v(TAG, "has notes: " + pnotes);
    			}
    			// do their notes contain the matching string?
    			Matcher matcher = pattern.matcher(pnotes);
				if (matcher.matches()) { 
    				Log.v(TAG, " - notes match regular expression");
    				rtstring = matcher.group(1);
    				Log.v(TAG, " - suggested ringtone is " + rtstring);
    			} else {
    				Log.v(TAG, " - notes do not match regular expression");
    				continue;
    			}
    			// does a ringtone exist that matches the ringtone string?
    			if (rthash.containsKey(rtstring)) {
    				Log.v(TAG, "ringtone exists, does not need to be created");
    			} else {
    				Log.v(TAG, "ringtone does not exist, create one");
    				// generate the List<Byte> representing the waveform
    				String rtcode = tomorse(rtstring);
    				Log.v(TAG, " - created code string of length " + (rtcode.length()/4));
    				// TODO: save as .ogg/.wav/.mp3/*something*
    				// JMT - stolen from ringdroid
    				File rtfile = new File(rtpath, rtstring + ".wav");
    				// TODO: identify it somehow as as a ringtone, of course!
    				try {
						writeFile(rtfile, rtcode);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
	    				Log.v(TAG, " exiting before corrupting hash or assigning ringtone!");
	    				continue;
					}
    				// generate new rturi
					// add it to the hash
    				//rthash.put(rtstring, newrturi);
    			}
    			// do they already have a custom ringtone?
    			String pcrt = pc.getString(pc.getColumnIndex(Contacts.PeopleColumns.CUSTOM_RINGTONE));
    			if (pcrt == null) {
        			Log.v(TAG, " - has no custom ringtone");
        		} else {
        			Log.v(TAG, " - has a custom ringtone = " + pcrt);
        			// is the existing custom ringtone the correct ringtone?
        			if (pcrt.equals(rthash.get(rtstring))) {
        				Log.v(TAG, " - custom ringtone is correct ringtone");
        				continue;
        			} else {
        				Log.v(TAG, " - custom ringtone is not correct ringtone");
        			}
        		}
    			Log.v(TAG, "assign proper ringtone to contact");
    			// JMT - stolen from ringdroid
    			Uri puri = Uri.withAppendedPath(People.CONTENT_URI, pc.getString(pc.getColumnIndex(People._ID)));
    			ContentValues pvalues = new ContentValues();
    			pvalues.put(People.CUSTOM_RINGTONE, rthash.get(rtstring));
    			getContentResolver().update(puri, pvalues, null, null);
        	} while (pc.moveToNext());
        }
        finish();
    }
    
    /** Called when the system is about to start resuming a previous activity. */
    @Override
    protected void onPause() {
    	super.onPause();
    	if (logging == true)
    		Log.v(TAG, "entered onPause");
    	// commit unsaved changes to persistent data
    	// stop animations
    	// MUST BE VERY QUICK
    }

    /** Called when the activity is no longer visible to the user. */
    protected void onStop() {
    	super.onStop();
    	if (logging == true)
    		Log.v(TAG, "entered onStop");
    	
    }

    /** The final call you receive before your activity is destroyed. */
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	if (logging == true)
    		Log.v(TAG, "entered onDestroy");
    }
    
    /** Constructs the dit, dah, and space elements. */
    protected void listSetup() {
    	if (logging == true)
    		Log.v(TAG, "entered listSetup");
    	
    	int bytecounter;
    	int index;

    	int[] resdit = res.getIntArray(R.array.dit);
    	dit = new byte[resdit.length * 2];
    	bytecounter = 0;
    	for (int i: resdit) { 
    		// Log.v(TAG, "value is " + i);
    		dit[bytecounter++] = (byte)(i % 0xff);
    		dit[bytecounter++] = (byte)((i >> 8) & 0xff);
    	}
    	if (logging == true)
			Log.v(TAG, " - dit is " + dit.length + " elements long");
		
    	int[] resdah = res.getIntArray(R.array.dah);
    	dah = new byte[resdah.length * 2];
    	bytecounter = 0;
    	for (int i: resdah) {
    		dah[bytecounter++] = (byte)(i % 0xff);
    		dah[bytecounter++] = (byte)((i >> 8) & 0xff);
    	}
    	if (logging == true)
			Log.v(TAG, " - dah is " + dah.length + " elements long");
		
    	ies = new byte[lenies * 2];
    	bytecounter = 0;
    	for (index = 0; index < lenies; index++) {
    		ies[bytecounter++] = (byte)(0);
    		ies[bytecounter++] = (byte)(0);
    	}
		if (logging == true)
			Log.v(TAG, " - ies is " + ies.length + " elements long");
 
    	ics = new byte[lenics * 2];
    	bytecounter = 0;
    	for (index = 0; index < lenics; index++) {
    		ics[bytecounter++] = (byte)(0);
    		ics[bytecounter++] = (byte)(0);
    	}
		if (logging == true)
			Log.v(TAG, " - ics is " + ics.length + " elements long");

    	iws = new byte[leniws * 2];
    	bytecounter = 0;
    	for (index = 0; index < leniws; index++) {
    		iws[bytecounter++] = (byte)(0);
    		iws[bytecounter++] = (byte)(0);
    	}
		if (logging == true)
			Log.v(TAG, " - iws is " + iws.length + " elements long");
    }
    
    /** Constructs the morse hashmap. */
    protected void morseSetup() {
    	if (logging == true)
    		Log.v(TAG, "entered morseSetup");
    	
		// construct hashmap
		morse.put('a', ".-");
		morse.put('b', "-...");
		morse.put('c',"-.-."); 
		morse.put('d',"-..");
		morse.put('e',".");
		morse.put('f',"..-.");
		morse.put('g',"--.");
		morse.put('h',"....");
		morse.put('i',"..");
		morse.put('j',".---");
		morse.put('k',"-.-");
		morse.put('l',".-..");
		morse.put('m',"--");
		morse.put('n',"-.");
		morse.put('o',"---");
		morse.put('p',".--.");
		morse.put('q',"--.-");
		morse.put('r',".-.");
		morse.put('s',"...");
		morse.put('t',"-");
		morse.put('u',"..-");
		morse.put('v',"...-");
		morse.put('w',".--");
		morse.put('x',"-..-");
		morse.put('y',"-.--");
		morse.put('z',"--..");
		morse.put('0',"-----");
		morse.put('1',".----");
		morse.put('2',"..---");
		morse.put('3',"...--");
		morse.put('4',"....-");
		morse.put('5',".....");
		morse.put('6',"-....");
		morse.put('7',"--...");
		morse.put('8',"---..");
		morse.put('9',"----.");
		morse.put('.',".-.-.-");
		morse.put(',',"--..--");
		morse.put('?',"..--..");
		morse.put(':',"---...");
		morse.put(';',"-.-.-.");
		morse.put('-',"-....-");
		morse.put('/',"-..-.");
		morse.put('\"',".-..-.");
		morse.put('+',".-.-.");
		morse.put('|',".-...");
		morse.put('>',"-.--.");
		morse.put('~',"...-.-");
		morse.put('=',"-...-");
		morse.put('@',".--.-.");
    }
    
    /** Builds the list of bytes from the text. */
    protected String tomorse(String input) {
    	if (logging == true)
    		Log.v(TAG, "entered tomorse");
    	
		String output = "";
		String letter;
		int lastel;
		int spaceflag = 1;
		CharacterIterator it = new StringCharacterIterator(input.toLowerCase());
		CharacterIterator it2;
	    
	    for (char ch = it.first(); ch != CharacterIterator.DONE; ch = it.next()) {
	    	Log.v(TAG, " - character is " + ch);
	        if (Character.isWhitespace(ch) && spaceflag == 0) {
	        	// append iws to output
	        	Log.v(TAG, "append iws to output");
	        	output += "iws ";
	        	spaceflag = 1;
	        } else {
	        	if (morse.containsKey(ch)) {
	        		letter = morse.get(ch);
	        		Log.v(TAG, " - morse contains key " + ch + ", value is " + letter);
	        		if (spaceflag == 0) {
	        			// append ics to output
	    	        	Log.v(TAG, "append ics to output");
	        			output += "ics ";
	        		} else
	        			spaceflag = 0;
	        		lastel = letter.length() - 1;
	        		it2 = new StringCharacterIterator(letter);
	        		for (char el = it2.first(); el != CharacterIterator.DONE; el = it2.next()) {
	        			Log.v(TAG, " - morse character is " + el);
	        			if (el == '.') {
	        				// append dit to output
	        	        	Log.v(TAG, "append dit to output");
	        				output += "dit ";
	        			} else {
	        				// append dah to output
	        	        	Log.v(TAG, "append dah to output");
	        				output += "dah ";
	        			}
	        			Log.v(TAG, "it2.getIndex() is " + it2.getIndex() + ", lastel is " + lastel);
	        			if (it2.getIndex() != lastel) {
	        				// append ies to output
	        	        	Log.v(TAG, "append ies to output");
		        			output += "ies ";
	        			} 
	        		}
	        	}
	        }
	    }
		return output;
    }
    
    /** Write ringtone to a file. */
    protected void writeFile(File file, String code) 
    throws java.io.IOException {
    	if (logging == true)
    		Log.v(TAG, "entered writeFile");
    	
    	// split the code string up into elements
    	String[] splitcode = code.split(" ");
    	Log.v(TAG, "code split into " + splitcode.length + " elements");
    	
    	// JMT: stolen from ringdroid
    	// create the file
    	file.createNewFile();
    	Log.v(TAG, "create new file");
		FileOutputStream out = new FileOutputStream(file);
		Log.v(TAG, "create new fileoutputstream");
		
		// generate length values
		long totalAudioLen = 0;
		for (String s : splitcode) {
			if (s.contentEquals("dit")) {
				totalAudioLen += dit.length;
			} else if (s.contentEquals("dah")) {
				totalAudioLen += dah.length;
			} else if (s.contentEquals("ies")) {
				totalAudioLen += ies.length;
			} else if (s.contentEquals("ics")) {
				totalAudioLen += ics.length;
			} else if (s.contentEquals("iws")) {
				totalAudioLen += iws.length;
			} else{
				Log.e(TAG, "bad value in splitcode: " + s);
			}
		}
		Log.v(TAG, "calculated total audio length: " + totalAudioLen);
		
		// previous answer given in bytes, need samples
		totalAudioLen /= 2;
    	long totalDataLen = totalAudioLen + 36;
    	long longSampleRate = samplerate;
    	long byteRate = samplerate * 2 * channels;
    	
    	// write the header
        byte[] header = new byte[44];
        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
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
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * channels);  // block align
        header[33] = 0;
        header[34] = 16;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        out.write(header, 0, 44);
        Log.v(TAG, "out write header succeeded");
        
    	// write the data
		for (String s : splitcode) {
			if (s.contentEquals("dit")) {
				out.write(dit);
			} else if (s.contentEquals("dah")) {
				out.write(dah);
			} else if (s.contentEquals("ies")) {
				out.write(ies);
			} else if (s.contentEquals("ics")) {
				out.write(ics);
			} else if (s.contentEquals("iws")) {
				out.write(iws);
			} else{
				Log.e(TAG, "bad value in splitcode: " + s);
			}
		}
        out.close();
        Log.v(TAG, "out close succeeded");
    }
}