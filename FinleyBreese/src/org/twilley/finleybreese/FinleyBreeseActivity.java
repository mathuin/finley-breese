package org.twilley.finleybreese;

import org.twilley.finleybreese.Morse;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Contacts;
import android.provider.Contacts.People;
import android.util.Log;

@SuppressWarnings("deprecation")
public class FinleyBreeseActivity extends Activity {
    private static final String TAG = "FinleyBreese";
	private static boolean logging = true;
	
	// hashes
	private HashMap<String, String> rthash = new HashMap<String, String>();
	private Morse myMorse;
	
	/** Called when the activity is first created. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        if (logging == true)
        	Log.v(TAG, "entered onCreate");

        // Populate a ringtone hash
        // key: name of ringtone
        // value: ringtone URI
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
    	
    	// Build Morse object
    	// myMorse = new Morse(pitch, cspeed, speed, bitwidth, samplerate, channels);
    	myMorse = new Morse();
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
    	File rtpath = getRingtoneDir();
		
        // Create a regular expression pattern that matches the expected string
		// \uFF1A is "full width colon".
		// The space after the colon is optional.
		// The character set in the group should include all Morse characters.
    	String NotesRegex = "[Rr]ingtone[:\uFF1A] ?([A-Za-z0-9 ]*)";
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
    				// Log.v(TAG, "has notes: " + pnotes);
    				pnotes = pnotes.toLowerCase(Locale.US);
    			}
    			// do their notes contain the matching string?
    			Matcher matcher = pattern.matcher(pnotes);
				if (matcher.matches()) { 
    				rtstring = matcher.group(1);
    				if (logging == true)
    					Log.d(TAG, " - notes match RE, suggested ringtone is " + rtstring);
    			} else {
    				if (logging == true)
    					Log.v(TAG, " - notes do not match RE");
    				continue;
    			}
				
    			// does a ringtone exist that matches the ringtone string?
    			if (rthash.containsKey(rtstring)) {
    				if (logging == true)
    					Log.d(TAG, "ringtone exists, does not need to be created");
    			} else {
    				if (logging == true)
    					Log.d(TAG, "ringtone does not exist, create one");
    				File rtfile = new File(rtpath, rtstring + ".wav");
    				// TODO: identify it somehow as as a ringtone, of course!
    				try {
        				myMorse.createFile(rtfile, rtstring);
					} catch (IOException e) {
	    				Log.e(TAG, " exiting before corrupting hash or assigning ringtone! ", e);
	    				continue;
					}
    				// add it to the hash
    				rthash.put(rtstring, rtfile.getAbsolutePath());
    			}
    			// do they already have a custom ringtone?
    			String pcrt = pc.getString(pc.getColumnIndex(Contacts.PeopleColumns.CUSTOM_RINGTONE));
    			if (pcrt == null) {
    				if (logging == true)
    					Log.v(TAG, " - has no custom ringtone");
        		} else {
        			if (logging == true)
        				Log.v(TAG, " - has a custom ringtone = " + pcrt);
        			// is the existing custom ringtone the correct ringtone?
        			if (pcrt.equals(rthash.get(rtstring))) {
        				if (logging == true)
        					Log.v(TAG, " - custom ringtone is correct ringtone");
        				continue;
        			} else {
        				if (logging == true)
        					Log.v(TAG, " - custom ringtone is not correct ringtone");
        			}
        		}
    			if (logging == true)
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
    
    /** Returns the location where ringtones should be saved. */
    protected File getRingtoneDir() {
    	File rtpath;
    	
		// boolean mExternalStorageAvailable = false;
		// boolean mExternalStorageWriteable = false;
    	String badDefault = "/sdcard/";
		String state = Environment.getExternalStorageState();
		
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			// We can read and write the media
			if (logging == true)
				Log.d(TAG, "media mounted");
			rtpath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES);
			// mExternalStorageAvailable = mExternalStorageWriteable = true;
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			// We can only read the media
			Log.w(TAG, "media mounted read only");
			rtpath = new File(badDefault);
			// mExternalStorageAvailable = true;
			// mExternalStorageWriteable = false;
		} else {
			// Something else is wrong
			Log.e(TAG, "external storage unavailable");
			rtpath = new File(badDefault);
			// mExternalStorageAvailable = mExternalStorageWriteable = false;
		}
		if (rtpath.exists()) {
			if (rtpath.isDirectory()) {
				if (logging == true)
					Log.v(TAG, "rtpath is a directory");
			} else {
				Log.e(TAG, "rtpath is not a directory!");
			}
		} else {
			rtpath.mkdirs();
			Log.w(TAG, "created rtpath directory");
		}
	
		return rtpath;
    }
}