package org.opencv.pocdiagnostics;

import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.pocdiagnostics.RunProcessor.Mode;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
  
/**
 * This activity runs the form processor and provides user feedback
 * displaying progress dialogs and the alignment results.
 */
public class AfterPhotoTaken extends Activity {
	
	private boolean ratiometric;
	private boolean aligned = false;
    private String photoName;
    private String testType;
    private String patientID;
    private String label;
    private double threshold = 0;
    private double percentPixels = 0;
    private double boxWidth = 0;
    private double numColumns = 0;
    private RunProcessor runProcessor;
    private Button processButton;
    private LinearLayout content;
    
    private Boolean batch_enabled = false; // changed back to false
    
    @Override
	protected void onCreate(Bundle savedInstanceState) 
    {
    	super.onCreate(savedInstanceState);
    	
    	Log.i("Diagnostics", "After Photo taken");
    	setContentView(R.layout.after_photo_taken);
		content = (LinearLayout) findViewById(R.id.myLinearLayout);
    	
   		Bundle extras = getIntent().getExtras();
		if (extras == null) {
			Log.i("Diagnostics","extras == null");
			//This might happen if we use back to reach this activity from the camera activity.
			content.setVisibility(View.VISIBLE);
			return;
		}
		
		patientID = extras.getString("patientID");
		photoName = extras.getString("photoName");
		testType = extras.getString("testType");
		label = extras.getString("label");
		ratiometric = extras.getBoolean("ratiometric");
		threshold = extras.getDouble("threshold");
		percentPixels = extras.getDouble("percentPixels");
		numColumns = extras.getDouble("numColumns");
		boxWidth = extras.getDouble("boxWidth");
		
		TextView pid =  (TextView) findViewById(R.id.PatientID);
		pid.append("\n" + patientID);
		
		TextView tType =  (TextView) findViewById(R.id.Test_Type);
		tType.append("\n" + label);
		
		try
		{
			JSONObject description = DiagnosticsUtils.loadTemplate(testType);
			
			if (threshold == -1)
			{
				if (description.has("threshold")) 
				{
					this.threshold = description.getDouble("threshold");
				}
				else
				{
					this.threshold = 0;
				}
			}
		
			if (percentPixels == -1)
			{
				if (description.has("percentPixels"))
				{
					this.percentPixels = description.getDouble("percentPixels");
				}
				else
				{
					this.percentPixels = 35;
				}
			}
	    	
			if (boxWidth == -1)
			{
				if (description.has("boxWidth")) 
				{
					this.boxWidth = description.getDouble("boxWidth");
				}
				else
				{
					this.boxWidth = 40;
				}
			}
	    	
			if (numColumns == -1)
			{
				if (description.has("numColumns")) 
				{
					this.numColumns = description.getDouble("numColumns");
				}
				else
				{
					this.numColumns = 5;
				}
			}
		}
		catch (JSONException e) 
		{
			e.printStackTrace();
		}
		
		//Do the processing
		runProcessor = new RunProcessor(handler, this.photoName, this.testType, this.threshold, this.percentPixels, this.boxWidth, this.numColumns);
		
		if( extras.getBoolean("preAligned") )
		{
			startThread(RunProcessor.Mode.LOAD);
		}
		else if (ratiometric)
		{
			startThread(RunProcessor.Mode.ALIGN_RATIOMETRIC);
		}
		else
		{
			startThread(RunProcessor.Mode.LOAD_ALIGN);
		}
		
		//Button handlers:
		Button retake = (Button) findViewById(R.id.retake_button);
		retake.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(getApplication(), CaptureImage.class);
				intent.putExtra("testType", testType);
				intent.putExtra("patientID", patientID);
				intent.putExtra("photoName", photoName);
				intent.putExtra("label", label);
				intent.putExtra("ratiometric", ratiometric);
				intent.putExtra("threshold", threshold);
				intent.putExtra("percentPixels", percentPixels);
				intent.putExtra("numColumns", numColumns);
				intent.putExtra("boxWidth", boxWidth);
				startActivity(intent);
				finish();
			}
		});
		
		processButton = (Button) findViewById(R.id.process_button);
		processButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (ratiometric)
					startThread(RunProcessor.Mode.PROCESS_RATIOMETRIC);
				else
					startThread(RunProcessor.Mode.PROCESS);
			}
		});
		
		Button batch = (Button) findViewById(R.id.process_batch_button);
		batch.setVisibility(View.INVISIBLE);
		
		if (batch_enabled)
		{
			batch.setVisibility(View.VISIBLE);
			batch.setOnClickListener(new View.OnClickListener() 
			{
				public void onClick(View v) {
					startThread(RunProcessor.Mode.BATCH);
				}
			});
		}
	}
    
    //Launch a thread to do the image processing.
    //Shows a status dialog as well.
	protected void startThread(Mode mode) 
	{
		showDialog(mode.ordinal());
		runProcessor.setMode(mode);
		
		Thread thread = new Thread( runProcessor );
		thread.setPriority(Thread.MAX_PRIORITY);
		thread.start();
	}

	@Override
	protected Dialog onCreateDialog(int id, Bundle args) {
				
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		switch (RunProcessor.Mode.values()[id]) {
		case LOAD:
			builder.setTitle("Loading Image");
			break;
		case LOAD_ALIGN:
			builder.setTitle(getResources().getString(R.string.aligning));
			break;
		case PROCESS:
			builder.setTitle(getResources().getString(R.string.processing));
			break;
		case BATCH:
			builder.setTitle(getResources().getString(R.string.processing_batch));
			break;
		case ALIGN_RATIOMETRIC:
			builder.setTitle(getResources().getString(R.string.aligning));
			break;
		case PROCESS_RATIOMETRIC:
			builder.setTitle(getResources().getString(R.string.processing));
			break;
		default:
			return null;
		}
		builder.setCancelable(false);
		
		return builder.create();
	}

    private Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
            	
            	RunProcessor.Mode mode = RunProcessor.Mode.values()[msg.what];
            	try
            	{
            		dismissDialog(msg.what);
            	}
            	catch(IllegalArgumentException e)
            	{
            		Log.i("Diagnostics", "Exception: Dialog with id " + msg.what + " was not previously shown.");
            	}
            	
            	switch (mode) 
            	{
        		case LOAD:
        		case LOAD_ALIGN:
        		case ALIGN_RATIOMETRIC:
            		aligned = (msg.arg1 == 1);
	        		if ( aligned ) {
	        			DiagnosticsUtils.displayImageInWebView((WebView)findViewById(R.id.webview), photoName, false);
	        		}
	        		else {
	        			RelativeLayout failureMessage = (RelativeLayout) findViewById(R.id.failureMessage);
	        			failureMessage.setVisibility(View.VISIBLE);
	        		}
	        		content.setVisibility(View.VISIBLE);
	        		processButton.setEnabled(aligned);
        			break;
        		case PROCESS:
        		case PROCESS_RATIOMETRIC:
            		if ( msg.arg1 == 1 ) {
	            		Intent intent = new Intent(getApplication(), DisplayResults.class);
	    				intent.putExtra("testType", testType);
	    				intent.putExtra("patientID", patientID);
	    				intent.putExtra("photoName", photoName);
	    				intent.putExtra("label", label);
	    				intent.putExtra("ratiometric", ratiometric);
	    				intent.putExtra("threshold", threshold);
	    				intent.putExtra("percentPixels", percentPixels);
	    				intent.putExtra("numColumns", numColumns);
	    				intent.putExtra("boxWidth", boxWidth);
	                    startActivity(intent);
	        			finish(); 
            		}
        			break;
        			
        		//TODO: Create a new activity for handling batch processing results	
        		case BATCH:
            		if ( msg.arg1 == 1 ) {
	            		Intent intent = new Intent(getApplication(), DisplayResults.class);
	                    intent.putExtra("photoName", photoName);
	                    intent.putExtra("testType", testType);
	                    intent.putExtra("patientID", patientID);
	                    intent.putExtra("label", label);
	                    startActivity(intent);
	        			finish(); 
            		}
        			break;
        		default:
        			return;
        		}
            }
    };

}