package org.twilley.finleybreese;

import org.twilley.finleybreese.Morse;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Contacts;
import android.provider.Contacts.People;
import android.util.Log;
import android.widget.Toast;

@SuppressWarnings("deprecation")
public class FinleyBreeseActivity extends Activity {
    private static final String TAG = "FinleyBreese";
	private static boolean logging = true;
	
	// hashes
	private HashMap<String, String> rthash = new HashMap<String, String>();
	private Morse myMorse;
	
	File rtpath;
	
	/** Called when the activity is first created. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        if (logging == true)
        	Log.v(TAG, "entered onCreate");

        // thank you ringdroid
    	rtpath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES);
		String status = Environment.getExternalStorageState();
		
		if (status.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
			showFinalAlert("Media mounted read only");
			return;
		}
		if (status.equals(Environment.MEDIA_SHARED)) {
			showFinalAlert("Media shared");
			return;
		}
		if (!status.equals(Environment.MEDIA_MOUNTED)) {
			showFinalAlert("No SD Card Found");
			return;
		}

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
		
        // Create a regular expression pattern that matches the expected string
		// \uFF1A is "full width colon".
		// The space after the colon is optional.
		// The character set in the group should include all Morse characters.
    	String NotesRegex = "[Rr]ingtone[:\uFF1A] ?(.*)";
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
    			Uri puri = Uri.withAppendedPath(getContactContentUri(), pc.getString(pc.getColumnIndex(People._ID)));
    			ContentValues pvalues = new ContentValues();
    			pvalues.put(People.CUSTOM_RINGTONE, rthash.get(rtstring));
    			getContentResolver().update(puri, pvalues, null, null);
    			
    			Toast.makeText(this, "Ringtone set for " + pc.getString(pc.getColumnIndexOrThrow(People._ID)), Toast.LENGTH_SHORT).show();
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
    
    /** show the final alert - thank you ringdroid */
    private void showFinalAlert(CharSequence message) {
    	// TODO: replace ANGRY and OK with proper values
    	new AlertDialog.Builder(FinleyBreeseActivity.this)
    		.setTitle("ANGRY")
    		.setMessage(message)
    		.setPositiveButton("OK", 
    				new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog,
    						int whichButton) {
    					finish();
    				}
    		})
    		.setCancelable(false)
    		.show();
    }
    
    /** select correct contact content uri - thank you ringdroid */
    private Uri getContactContentUri() {
    	if (Build.VERSION.SDK_INT >= 5) {
    		return Uri.parse("content://com.android.contacts/contacts");
    	} else {
    		return Contacts.People.CONTENT_URI;
    	}
    }
}