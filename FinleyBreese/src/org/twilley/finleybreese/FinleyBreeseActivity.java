package org.twilley.finleybreese;

import org.twilley.finleybreese.Morse;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.twilley.finleybreese.R;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.widget.Toast;

public class FinleyBreeseActivity extends FragmentActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "FinleyBreese";
	private static boolean logging = true;
	
	// constants
	private static final int LOADER_ID = 0x01;
	
	// preferences
	SharedPreferences sharedPref;
	SharedPreferences.Editor editor;

	//  - constants
	private static final int DEFAULT_PITCH = 880;
	private static final int DEFAULT_CSPEED = 20;
	private static final int DEFAULT_SPEED = 13;
	private static final int DEFAULT_BITWIDTH = 32;
	private static final int DEFAULT_SAMPLERATE = 44100;
	private static final int DEFAULT_CHANNELS = 2;
	
	// - parameters
	private int pitch;
	private int cspeed;
	private int speed;
	private int bitwidth;
	private int samplerate;
	private int channels;
	
	// object which does the actual text->Morse translation
	private Morse myMorse;
	
	// files
	File rtpath;
	
	/** Called when the activity is first created. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        if (logging == true)
        	Log.v(TAG, "entered onCreate");
   
    	sharedPref = this.getPreferences(Context.MODE_PRIVATE);
    	editor = sharedPref.edit();

        getSupportLoaderManager().initLoader(LOADER_ID, null, this);

        // thank you ringdroid
        rtpath = getRingtoneDir();
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

    	// Build Morse object
		// TODO: consider Toast if this takes noticeable amounts of time
		myMorse = buildMorseFromPreferences();
		
		// throw up the button here, but it won't do crap.
		
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
    	savePreferences();    	
    }

    /** The final call you receive before your activity is destroyed. */
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	if (logging == true)
    		Log.v(TAG, "entered onDestroy");
    }
    
    /** implementing onCreateLoader */
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
    	// TODO: figure out how to add notes logic here
    	// step one: include notes in projection
    	// step two: only match if notes match regular expression
    	String[] projection = {RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.CUSTOM_RINGTONE};
    	String selection = null;
    	String[] selectionargs = null;
    	String sortorder = null;
    	return new CursorLoader(this, RawContacts.CONTENT_URI, projection, selection, selectionargs, sortorder);
    }
    
    /** implementing onLoadFinished */
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
    	if (logging == true)
    		Log.v(TAG, "onLoadFinished entered");
    	
    	// This will be either:
    	// enabling the button
    	// or 
    	// doing the work
    	String rtstring;
        
    	// Create a regular expression pattern that matches the expected string
  		// \uFF1A is "full width colon".
  		// The space after the colon is optional.
  		// The character set in the group should include all Morse characters.
      	String NotesRegex = "[Rr]ingtone[:\uFF1A] ?(.*)";
      	Pattern pattern = Pattern.compile(NotesRegex);

    	if (cursor != null && cursor.moveToFirst()) {
    		while (!cursor.isAfterLast()) {
    			String rawContactId = cursor.getString(0);
    			String rawContactName = cursor.getString(1);
    			String rawContactCustomRingtone = cursor.getString(2);
    			// get notes if any -- remove if step one complete
    			String notes = null;
    			Cursor noteCursor = null;
    			try {
    				String [] projection = new String[] {Data._ID, Note.NOTE};
    				String selection = Data.RAW_CONTACT_ID + "=?" + " AND " + Data.MIMETYPE + "='" + Note.CONTENT_ITEM_TYPE + "'";
    				String[] selectionargs = new String[] {rawContactId};
    				String sortorder = null;
    				noteCursor = getContentResolver().query(Data.CONTENT_URI, projection, selection, selectionargs, sortorder); 
    				if (noteCursor != null && noteCursor.moveToFirst()) {
    					while (!noteCursor.isAfterLast()) {
    						notes += noteCursor.getString(noteCursor.getColumnIndex(Note.NOTE));
    						noteCursor.moveToNext();
    					}
    				}
    			} finally {
    				if (noteCursor != null)
    					noteCursor.close();
    			}
    			// no notes, don't bother
    			// TODO: learn how to jam this crap into initial search thing!
    			if (notes == null)
    				continue;
    			
    			// now that we have the notes, lowercase 'em.
    			notes = notes.toLowerCase(Locale.US);
    			
    			// do their notes contain the matching string?
    			// remove if step two complete
    			Matcher matcher = pattern.matcher(notes);
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
				File rtfile = new File(rtpath, rtstring + ".wav");
				String rtpath = rtfile.getAbsolutePath();
				
				// if file exists, we presume it is correct!
				if (rtfile.exists()) {
    				if (rawContactCustomRingtone.equals(rtpath))
    					continue;
    			} else {
    				// build ringtone
    				// TODO: confirm this is sane
    				try {
        				myMorse.createFile(rtfile, rtstring);
        				// TODO: identify it somehow as as a ringtone, of course!
					} catch (IOException e) {
	    				Log.e(TAG, " exiting before corrupting hash or assigning ringtone! ", e);
	    				continue;
					}
    			}
				
    			// assign ringtone here!
    			ContentValues values = new ContentValues();
    			values.put(RawContacts._ID, rawContactId);
    			values.put(RawContacts.CUSTOM_RINGTONE, rtpath);
    			getContentResolver().update(RawContacts.CONTENT_URI, values, null, null);
    			
    			Toast.makeText(this, "Ringtone set for " + rawContactName, Toast.LENGTH_SHORT).show();
    			cursor.moveToNext();
    		}
    	}
    }
    
    /** implementing onLoaderReset */
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
    	
    }
    
    /** show the final alert - thank you ringdroid */
    private void showFinalAlert(CharSequence message) {
    	new AlertDialog.Builder(FinleyBreeseActivity.this)
    		.setTitle(R.string.final_title)
    		.setMessage(message)
    		.setPositiveButton(R.string.ok, 
    				new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog,
    						int whichButton) {
    					finish();
    				}
    		})
    		.setCancelable(false)
    		.show();
    }
    
    /** select correct ringtone directory - see above */
    private File getRingtoneDir() {
    	if (Build.VERSION.SDK_INT >= 8) {
        	return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES);
    	} else {
    		return new File(Environment.getExternalStorageDirectory(), "Ringtones");
    	}
    }
    
    /** load preferences and build morse */
    private Morse buildMorseFromPreferences() {
		pitch = sharedPref.getInt(getString(R.string.saved_pitch), DEFAULT_PITCH);
		cspeed = sharedPref.getInt(getString(R.string.saved_cspeed), DEFAULT_CSPEED);
		speed = sharedPref.getInt(getString(R.string.saved_speed), DEFAULT_SPEED);
		bitwidth = sharedPref.getInt(getString(R.string.saved_bitwidth), DEFAULT_BITWIDTH);
		samplerate = sharedPref.getInt(getString(R.string.saved_samplerate), DEFAULT_SAMPLERATE);
		channels = sharedPref.getInt(getString(R.string.saved_channels), DEFAULT_CHANNELS);		

    	myMorse = new Morse(pitch, cspeed, speed, bitwidth, samplerate, channels);
		return myMorse;
    }
    
    /** save preferences */
    private void savePreferences() {
		editor.putInt(getString(R.string.saved_pitch), pitch);
		editor.putInt(getString(R.string.saved_cspeed), cspeed);
		editor.putInt(getString(R.string.saved_speed), speed);
		editor.putInt(getString(R.string.saved_bitwidth), bitwidth);
		editor.putInt(getString(R.string.saved_samplerate), samplerate);
		editor.putInt(getString(R.string.saved_channels), channels);		
    	editor.commit();
    }
}