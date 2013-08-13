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
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.RawContacts;
import android.provider.MediaStore;
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

	// - constants
	private static final int DEFAULT_PITCH = 880;
	private static final int DEFAULT_CSPEED = 20;
	private static final int DEFAULT_SPEED = 13;
	
	// - parameters
	private int pitch;
	private int cspeed;
	private int speed;
	
	// ringtone hash
	private HashMap<String, String> rthash;
	
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

    /** The final call you receive before your activity is destroyed. */
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	if (logging == true)
    		Log.v(TAG, "entered onDestroy");
    }
    
    /** implementing onCreateLoader */				
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
    	Log.v(TAG, "onCreateLoader reached");
    	String[] projection = {	ContactsContract.Data.RAW_CONTACT_ID, ContactsContract.Data.CONTACT_ID,	ContactsContract.Data.DISPLAY_NAME,	ContactsContract.Data.CUSTOM_RINGTONE, ContactsContract.Data.DATA1 };
    	String selection = ContactsContract.Data.MIMETYPE + "='" + Note.CONTENT_ITEM_TYPE + "' AND " + ContactsContract.Data.DATA1 + " LIKE ?";
    	String[] selectionargs = { "%RINGTONE%" };
    	String sortorder = null;
    	return new CursorLoader(this, ContactsContract.Data.CONTENT_URI, projection, selection, selectionargs, sortorder);
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
    		while (cursor.moveToNext()) {
    			String rawContactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID));
    			String rawContactContactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID));
    			String rawContactDisplayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
    			String rawContactCustomRingtone = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.CUSTOM_RINGTONE));
    			String rawContactNotes = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.DATA1));
    			    			
    			// now that we have the notes, lowercase 'em.
    			String notes = rawContactNotes.toLowerCase(Locale.US);
    			
    			// do their notes contain the matching string?
    			// remove if step two complete
    			Matcher matcher = pattern.matcher(notes);
				if (matcher.matches()) 
    				rtstring = matcher.group(1);
    			else
    				continue;

   				if (logging == true)
					Log.v(TAG, " - ID: " + rawContactId + ", name: " + rawContactDisplayName + ", rtstring: " + rtstring + ", ringtone: " + rawContactCustomRingtone);			

    			// does a ringtone exist that matches the ringtone string?
				File rtfile = new File(rtpath, rtstring + ".wav");
				String rtabs = rtfile.getAbsolutePath();

				// if file exists, we presume it is correct!
				if (rtfile.exists()) {
					if (rtabs.equals(rthash.get(rtstring)))
    					continue;
				} else {
					try {
        				myMorse.createFile(rtfile, rtstring);
        				addRingtoneToMediaStore(rtabs, rtstring);
        				rthash.put(rtstring, rtabs);
					} catch (IOException e) {
	    				Log.e(TAG, " exiting before corrupting hash or assigning ringtone! ", e);
	    				continue;
					}
    			}
								
				// assign ringtone here!
				Uri contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, rawContactContactId);
    			ContentValues values = new ContentValues();
    			values.put(RawContacts._ID, rawContactId);
    			values.put(RawContacts.CUSTOM_RINGTONE, getRingtoneUri(rtabs));
    			getContentResolver().update(contactUri, values, null, null);

    			Toast.makeText(this, "Ringtone set for " + rawContactDisplayName, Toast.LENGTH_SHORT).show();
    		}
    	}
    	Toast.makeText(this, "All ringtones set!", Toast.LENGTH_SHORT).show();
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
		myMorse = new Morse(pitch, cspeed, speed);
		return myMorse;
    }
    
    /** save preferences */
    private void savePreferences() {
		editor.putInt(getString(R.string.saved_pitch), pitch);
		editor.putInt(getString(R.string.saved_cspeed), cspeed);
		editor.putInt(getString(R.string.saved_speed), speed);		
    	editor.commit();
    }

    /** build ringtone hash */
    private HashMap<String, String> buildRingtoneHash() {
    	HashMap<String, String> retval = new HashMap<String, String>();
    	RingtoneManager rtm = new RingtoneManager(this);
    	rtm.setType(RingtoneManager.TYPE_RINGTONE);
    	Cursor rtc = rtm.getCursor();
		if (rtc != null && rtc.getCount() > 0) {
			while (rtc.moveToNext()) {
				String key = rtc.getString(RingtoneManager.TITLE_COLUMN_INDEX);
				Uri uri = Uri.parse(rtc.getString(RingtoneManager.URI_COLUMN_INDEX));
				String id = rtc.getString(RingtoneManager.ID_COLUMN_INDEX);
				Uri uriplusid = Uri.withAppendedPath(uri, id);
				String value = getRingtoneFilename(uriplusid);
				retval.put(key, value);
			}
		}
		rtc.close();
		return retval;
    }
    
    /** given a ringtone URI, return the corresponding absolute path */
    private String getRingtoneFilename(Uri ringtone) {
		String[] proj = { MediaStore.Audio.Media.DATA };
		Cursor cursor = getContentResolver().query(ringtone, proj, null, null, null);
		int column_index = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
		cursor.moveToFirst();
		String value = cursor.getString(column_index);
		cursor.close();
		return value;
    }
    
    /** given an absolute path, return the corresponding content Uri */
	private String getRingtoneUri(String rtabs) {
		Uri rtUri = MediaStore.Audio.Media.getContentUriForPath(rtabs);
		String[] rtUriproj = { MediaStore.Audio.AudioColumns._ID };
		String rtUrisel = MediaStore.Audio.AudioColumns.DATA + " LIKE ?";
		String[] rtUriselargs = { rtabs };
		Cursor rtcursor = getContentResolver().query(rtUri, rtUriproj, rtUrisel, rtUriselargs, null);
		rtcursor.moveToFirst();
		String rtUriId = rtcursor.getString(rtcursor.getColumnIndex(MediaStore.Audio.AudioColumns._ID));
		rtcursor.close();
		Uri newrtUri = Uri.withAppendedPath(rtUri, rtUriId);
		String retval = newrtUri.toString();
		return retval;
	}

	/** given an absolute path and ringtone string, add that ringtone to the media store */
    private void addRingtoneToMediaStore(String rtabs, String rtstring) {
		Uri rtUri = MediaStore.Audio.Media.getContentUriForPath(rtabs);
		ContentValues rtvalues = new ContentValues();
		rtvalues.put(MediaStore.MediaColumns.DATA, rtabs);
		rtvalues.put(MediaStore.MediaColumns.TITLE, "FB - " + rtstring);
		rtvalues.put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav");
		rtvalues.put(MediaStore.Audio.Media.ARTIST, "Finley Breese");
		rtvalues.put(MediaStore.Audio.Media.IS_RINGTONE, true);
		getContentResolver().delete(rtUri, MediaStore.MediaColumns.DATA + "=\"" + rtabs + "\"", null);
		@SuppressWarnings("unused")
		Uri newrtUri = getContentResolver().insert(rtUri, rtvalues);
    }
}