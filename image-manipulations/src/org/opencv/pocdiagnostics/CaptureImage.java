package org.opencv.pocdiagnostics;

import java.io.File;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;

/*
 * Launches the Android camera app and collects the picture taken.
 */

public class CaptureImage extends Activity 
{
    private static final String ODK_CAMERA_TAKE_PICTURE_INTENT_COMPONENT = "org.opendatakit.camera.TakePicture";
	private static final String ODK_CAMERA_INTENT_PACKAGE = "org.opendatakit.camera";
	private static final int CAMERA_PIC_REQUEST = 1337;  
	
	private static final int TAKE_PICTURE = 12346789;
	private String photoName;
	private String testType;
	private String patientID;
	private String label;
	private JSONObject description = null;
	
    private double threshold = 0;
    private double percentPixels = 0;
    private double boxWidth = 0;
    private double numColumns = 0;
	
	private boolean ratiometric = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
   		Bundle extras = getIntent().getExtras();
		if (extras == null) {
			Log.i("Diagnostics","extras == null");
			return;
		}

		ratiometric = extras.getBoolean("ratiometric");
		patientID = extras.getString("patientID");
		testType = extras.getString("testType");
		photoName = extras.getString("photoName");
		label = extras.getString("label");
		
		threshold = extras.getDouble("threshold");
		percentPixels = extras.getDouble("percentPixels");
		numColumns = extras.getDouble("numColumns");
		boxWidth = extras.getDouble("boxWidth");
		
		Uri imageUri = Uri.fromFile(new File(DiagnosticsUtils.getPhotoPath(photoName)));
		String filepath = DiagnosticsUtils.getPhotoPath(photoName);
		Log.i("Diagnostics",imageUri.toString());
		
		if (ratiometric)
		{
			description = DiagnosticsUtils.loadTemplate(testType);
			
			int width = 100;
			int height = 100;
			int innerWidth = 50;
			int innerHeight = 50;
			int xOffset = 20;
			int yOffset = 20;
			
			if ((description.has("width")) && (description.has("height")))
			{
				if ((description.has("innerWidth")) && (description.has("innerHeight")))
				{
					if ((description.has("xOffset")) && (description.has("yOffset")))
					{
						try {
							width = description.getInt("width");				
							height = description.getInt("height");
							innerWidth = description.getInt("innerWidth");
							innerHeight = description.getInt("innerHeight");
							xOffset = description.getInt("xOffset");
							yOffset = description.getInt("yOffset");
						} 
						catch (JSONException e) 
						{
							e.printStackTrace();
						}
					}
				}
			}
			
			int array[] = {width, height, innerWidth, innerHeight, xOffset, yOffset};
			
			Intent cameraIntent = new Intent(); 
			cameraIntent.setComponent(new ComponentName(ODK_CAMERA_INTENT_PACKAGE, ODK_CAMERA_TAKE_PICTURE_INTENT_COMPONENT));
			// pass the array of test dimensions, the saved photo directory, and a boolean for the retake button
			cameraIntent.putExtra("retakeOption", true);
			cameraIntent.putExtra("dimensions", array);
			
			Log.i("Diagnostics", "Filepath is " + filepath);
			Log.i("Diagnostics", "imageUri is " + imageUri.toString());
			
			cameraIntent.putExtra("filePath", filepath);
			startActivityForResult(cameraIntent, CAMERA_PIC_REQUEST);
		}
		else  
		{	
			Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
			intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
			startActivityForResult(intent, TAKE_PICTURE);
		}
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) 
	{
		super.onActivityResult(requestCode, resultCode, data);
		
		Log.i("Diagnostics", "Request code: " + requestCode + " result code " + resultCode);
		
		if ((requestCode == TAKE_PICTURE) || (requestCode == CAMERA_PIC_REQUEST))
		{
			finishActivity(requestCode);
			if (resultCode == Activity.RESULT_OK) 
			{
				Intent intent = new Intent(getApplication(), AfterPhotoTaken.class);				
				intent.putExtra("testType", testType);
				intent.putExtra("patientID", patientID);
				intent.putExtra("photoName", photoName);
				intent.putExtra("label", label);
				intent.putExtra("ratiometric", ratiometric);
				intent.putExtra("threshold", threshold);
				intent.putExtra("percentPixels", percentPixels);
				intent.putExtra("numColumns", numColumns);
				intent.putExtra("boxWidth", boxWidth);
				
				if( new File(DiagnosticsUtils.getPhotoPath(photoName)).exists() ) 
				{
					Log.i("Diagnostics,", DiagnosticsUtils.getPhotoPath(photoName));
					Log.i("Diagnostics", "Starting processing with "
							+ DiagnosticsUtils.getPhotoPath(photoName) + "...");
					startActivity(intent);
				}
				else
				{
					Log.i("Diagnostics", DiagnosticsUtils.getPhotoPath(photoName));
					Log.i("Diagnostics", "photo name: " + photoName + "could not be written.");
				}
			}
			else if(resultCode == Activity.RESULT_FIRST_USER){
				Log.i("Diagnostics", "First User");
			}
			else if(resultCode == Activity.RESULT_CANCELED){
				Log.i("Diagnostics", "Canceled");
			}
			finish(); 
		}
	}
	
	//This method will generate a unique name with the given prefix by appending
	//the current value of a counter, then incrementing the counter.
	//Each prefix used has its own counter stored in the share preferences.
	/*protected String getUniqueName(String prefix) 
	{
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		int uid = settings.getInt(prefix, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putInt(prefix, uid + 1);
		editor.commit();
		return prefix + uid;
	}*/
}
