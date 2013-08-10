package org.twilley.finleybreese;

import org.twilley.finleybreese.Morse;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
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
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
	
	// ringtone hash
	private HashMap<String, Uri> rthash;
	
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
		Toast.makeText(this, "Generating waveforms...", Toast.LENGTH_SHORT).show();
		myMorse = buildMorseFromPreferences();
		rthash = buildRingtoneHash();
		Toast.makeText(this, "Waveform generation complete!", Toast.LENGTH_SHORT).show();
		
		// Configure button!
		Button button = (Button) findViewById(R.id.button1);
	
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
		        getSupportLoaderManager().initLoader(LOADER_ID, null, FinleyBreeseActivity.this);
			}
		});
		
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

    /** The final call you receivandroid file path to ringtone namee before your activity is destroyed. */
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
    	Log.v(TAG, "onCreateLoader reached");
    	String[] projection = {RawContacts._ID, RawContacts.CONTACT_ID, RawContacts.CUSTOM_RINGTONE};
    	String selection = null;
    	String[] selectionargs = null;
    	String sortorder = null;
    	return new CursorLoader(this, RawContacts.CONTENT_URI, projection, selection, selectionargs, sortorder);
    }
    
    /** implementing onLoadFinished */
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
    	if (logging == true)
    		Log.v(TAG, "onLoadFinished entered");
    	String rtstring;
        
    	// Create a regular expression pattern that matches the expected string
  		// \uFF1A is "full width colon".
  		// The space after the colon is optional.
  		// The character set in the group should include all Morse characters.
      	String NotesRegex = "ringtone[:\uFF1A] ?(.*)";
      	Pattern pattern = Pattern.compile(NotesRegex);

    	if (cursor != null && cursor.getCount() > 0) {
    		for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
    			String rawContactId = cursor.getString(cursor.getColumnIndex(RawContacts._ID));
    			String rawContactContactId = cursor.getString(cursor.getColumnIndex(RawContacts.CONTACT_ID));
    			Uri rawContactCustomRingtone = Uri.parse(cursor.getString(cursor.getColumnIndex(RawContacts.CUSTOM_RINGTONE)));
    			// get display name based on contact ID
    			String rawContactDisplayName = "";
    			Cursor dnCursor = null;
    			try {
    				String[] projection = new String[] {ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME};
    				String selection = ContactsContract.Contacts._ID + "=?";
    				String[] selectionArgs = new String[] {rawContactContactId};
    				String sortOrder = "";
    				dnCursor = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI, projection, selection, selectionArgs, sortOrder);
    				if (dnCursor != null && dnCursor.getCount() > 0) {
    					for (dnCursor.moveToFirst(); !dnCursor.isAfterLast(); dnCursor.moveToNext())
    						rawContactDisplayName += dnCursor.getString(dnCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
    				}
    						
    			} finally {
    				if (dnCursor != null)
    					dnCursor.close();
    			}
    			// get notes if any -- remove if step one complete
    			String notes = "";
    			Cursor noteCursor = null;
    			try {
    				String[] projection = new String[] {Data._ID, Note.NOTE};
    				String selection = Data.RAW_CONTACT_ID + "=?" + " AND " + Data.MIMETYPE + "='" + Note.CONTENT_ITEM_TYPE + "'";
    				String[] selectionArgs = new String[] {rawContactId};
    				String sortOrder = "";
    				noteCursor = getContentResolver().query(Data.CONTENT_URI, projection, selection, selectionArgs, sortOrder); 
    				if (noteCursor != null && noteCursor.getCount() > 0) {
    					for (noteCursor.moveToFirst(); !noteCursor.isAfterLast(); noteCursor.moveToNext())
    						notes += noteCursor.getString(noteCursor.getColumnIndex(Note.NOTE));
    				}
    			} finally {
    				if (noteCursor != null)
    					noteCursor.close();
    			}
    			// no notes, don't bother
    			// TODO: learn how to jam this crap into initial search thing!
    			if (notes == "")
    				continue;
    			Log.v(TAG, " - ID: " + rawContactId + ", name: " + rawContactDisplayName + ", ringtone: " + rawContactCustomRingtone);
    			
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
    					Log.v(TAG, " - notes do not match RE, notes are " + notes);
    				continue;
    			}
				
    			// does a ringtone exist that matches the ringtone string?
				File rtfile = new File(rtpath, rtstring + ".wav");
				String rtpath = rtfile.getAbsolutePath();
				Log.v(TAG, "rtpath is " + rtpath);
				Log.v(TAG, "rawContactCustomRingtone is " + rawContactCustomRingtone);
				
				// if file exists, we presume it is correct!
				if (rtfile.exists()) {
    				if (rawContactCustomRingtone.equals(rthash.get(rtstring))) {
    					continue;
    				}
      				if (noteCursor != null && noteCursor.getCount() > 0) {
    					for (noteCursor.moveToFirst(); !noteCursor.isAfterLast(); noteCursor.moveToNext())
    						notes += noteCursor.getString(noteCursor.getColumnIndex(Note.NOTE));
    				}
 	} else {
    				// build ringtone
    				// TODO: confirm this is sane
    				try {
        				myMorse.createFile(rtfile, rtstring);
        				// TODO: identify it somehow as as a ringtone, of course!
        				rthash.put(rtstring, Uri.parse(rtpath));
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
    			
    			Toast.makeText(this, "Ringtone set for " + rawContactDisplayName, Toast.LENGTH_SHORT).show();
    		}
    	}
    	if (logging == true)
    		Log.v(TAG, "onLoadFinished exited");
    }
    
    /** implementing onLoaderReset */
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
    	Log.v(TAG, "onLoaderReset reached");
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
    
    /** build ringtone hash */
    private HashMap<String, Uri> buildRingtoneHash() {
    	/* for every ringtone, key = title, value = Uri */
    	HashMap<String, Uri> retval = new HashMap<String, Uri>();
    	
    	RingtoneManager rtm = new RingtoneManager(this);
    	rtm.setType(RingtoneManager.TYPE_RINGTONE);
    	Cursor rtc = rtm.getCursor();
		if (rtc != null && rtc.getCount() > 0) {
			for (rtc.moveToFirst(); !rtc.isAfterLast(); rtc.moveToNext()) {
				String key = rtc.getString(RingtoneManager.TITLE_COLUMN_INDEX);
				Log.v(TAG, "key is " + key);
				Uri uri = Uri.parse(rtc.getString(RingtoneManager.URI_COLUMN_INDEX));
				String id = rtc.getString(RingtoneManager.ID_COLUMN_INDEX);
				Uri value = Uri.withAppendedPath(uri, id);
				Log.v(TAG, "value is " + value);
				retval.put(key, value);
			}
		}
		return retval;
    }
}