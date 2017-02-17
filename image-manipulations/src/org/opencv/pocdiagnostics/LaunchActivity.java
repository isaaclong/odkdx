package org.opencv.pocdiagnostics;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
  
/* 
 * This is the main activity. It displays the main menu
 * and launch the corresponding activity based on the user's selection
 */
public class LaunchActivity extends Activity 
{	
	int version = 3;
	private boolean ratiometric = false;
	private boolean highPoint = false;
	
	String [] rdtNames = new String[0];
	int selectedIndex = 0;    
	
	ProgressDialog pd;
	SharedPreferences settings;
	String testType;
	String patientID = "2";
	String photoName = "None";
	String label;
	
    private double threshold = -1;
    private double percentPixels = -1;
    private double boxWidth = -1;
    private double numColumns = -1;
    
	Button settingsButton;
	Button returnButton;
	Button sensorButton;
	Spinner spinny;
	JSONObject description = null;
	JSONObject [] fields = new JSONObject [0];

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Setup the UI
		setContentView(R.layout.launch_activity); 
		setupDirs();

		// TODO: settings button and batch button do not work/cause upload to not go off on aggregateqq

		settingsButton = (Button) findViewById(R.id.SettingsButton);
		//settingsButton.setVisibility(View.VISIBLE);
		settingsButton.setVisibility(View.INVISIBLE);
		returnButton = (Button) findViewById(R.id.ReturnButton);
		returnButton.setVisibility(View.INVISIBLE);
		sensorButton = (Button) findViewById(R.id.puck_button);
		sensorButton.setVisibility(View.INVISIBLE);
		spinny = (Spinner) findViewById(R.id.rdtSpinner); 

		/* This application is called from an ODK Collect form */

        /* Based on action parameter in calling intent bundle, either process image normally or
         * attempt to receive bluetooth data from Puck sensor
         *
         * The large block of commented code here is, for our purposes, deprecated, as we will never
         * be called by commcare with bundle input parameters. For now I'm leaving this in to preserve
         * as much of the original app as possible.
         */
        try {

			// print the passed in time startTime
			Bundle extras = getIntent().getExtras();
			for(String key: extras.keySet()) {
				Log.d("LaunchActivity", key + " is a key in the bundle with value: " + extras.getString(key));
			}
			// Copy over passed in parameters from ODK Collect call
			Date startTime = (Date)extras.getSerializable("startTime");
			Log.d("LaunchActivity", "startTime: " + startTime);
			// TODO: eventually make these dependent on testType
			DiagnosticsUtils.formStartTime = startTime;
			DiagnosticsUtils.hiv1 = extras.getString("hiv1");
			DiagnosticsUtils.hiv2 = extras.getString("hiv2");
			DiagnosticsUtils.control = extras.getString("control");
			DiagnosticsUtils.controld = extras.getString("determine_control");
			DiagnosticsUtils.resultd = extras.getString("determine_result");
			//String action = odkFormParams.getString("action");
			//if(action == null) action = "none";

			//if(action.equals("puck")) {


			//}
			//else if(action.equals("image") || action.equals("none")) {
			long time = System.currentTimeMillis();
			patientID = "" + time + "";
			setupSpinner();
			hookupButtonHandlers();
			DiagnosticsUtils.setTestType(extras.getString("testType"));
			if(extras.getString("action").equals("puck"))
				sensorButton.performClick();
			//}
			//else if(action.equals("return")) {
				//for(String key: getIntent().getExtras().keySet()) {
					//Log.d("LaunchActivity", key + " is a key in the bundle.");
				//}
			//}
            /*
            if(extras != null)
            {

			Log.i("Diagnostics","Data from CommCare received");

			spinny.setVisibility(View.INVISIBLE);

			patientID = extras.getString("patientId"); // switched to proper camel case here
			System.out.println("patientID: " + patientID);
			testType = extras.getString("testType");
            System.out.println("testType: " + testType);
			//description = DiagnosticsUtils.loadTemplate(testType);

			//label = extras.getString("label");

			if (extras.containsKey("threshold"))
			{
				threshold = Double.parseDouble(extras.getString("threshold"));
				try {
					description.put("threshold", threshold);
				} catch (JSONException e) {
					e.printStackTrace();
				}
				Log.e("", "Got threshold from CommCare " + threshold);
			}

			if (extras.containsKey("percentPixels"))
			{
				percentPixels = Double.parseDouble(extras.getString("percentPixels"));
				try {
					description.put("percentPixels", percentPixels);
				} catch (JSONException e) {
					e.printStackTrace();
				}
				Log.e("", "Got percentPixels from CommCare " + percentPixels);
			}

			if (extras.containsKey("numColumns"))
			{
				numColumns = Double.parseDouble(extras.getString("numColumns"));
				try {
					description.put("numColumns", numColumns);
				} catch (JSONException e) {
					e.printStackTrace();
				}
				Log.e("", "Got numColumns from CommCare " + numColumns);
			}

			if (extras.containsKey("boxWidth"))
			{
				boxWidth = Double.parseDouble(extras.getString("boxWidth"));
				try {
					description.put("boxWidth", boxWidth);
				} catch (JSONException e) {
					e.printStackTrace();
				}
				Log.e("", "Got boxWidth from CommCare " + boxWidth);
			}

			if (highPoint)
			{
				if (description.has("threshold"))
				{
					try {
						threshold = description.getDouble("threshold");
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
				if (description.has("percentPixels"))
				{
					try {
						percentPixels = description.getDouble("percentPixels");
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
				if (description.has("numColumns"))
				{
					try {
						numColumns = description.getDouble("numColumns");
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
				if (description.has("boxWidth"))
				{
					try {
						boxWidth = description.getDouble("boxWidth");
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
			}


			TextView pid =  (TextView) findViewById(R.id.PatientID);
			pid.append("\n" + patientID);

			TextView tType =  (TextView) findViewById(R.id.Test_Type);
			tType.append("\n" + testType); // this was previously + label, why not testType?

			photoName = testType + "_" + patientID;

			try {
				DiagnosticsUtils.writeFile(DiagnosticsUtils.getTemplatePath(testType), description.toString(2));
			} catch (Exception e) {
				e.printStackTrace();
			}

            }
            */

            //else
            //{


            //}
        } catch (NullPointerException e) {
            Log.e("LaunchActivity", "Extras is null on initial app startup");
        }
	}

	private void hookupButtonHandlers() {
		// Hook up handler for scan button
		Button scanForm = (Button) findViewById(R.id.ScanButton);
		scanForm.setOnClickListener(new View.OnClickListener() {
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
			}
		});
		
		// Hook up handler for settings button
		settingsButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(getApplication(), AppSettings.class);
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
			}
		});

		// hook up handler for sensor button
		sensorButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Bundle odkFormParams = getIntent().getExtras();
				for(String key: odkFormParams.keySet()) {
					Log.d("LaunchActivity", key + " is a key in the bundle.");
				}
				Intent puckIntent = new Intent(getApplication(), ImportPuckData.class);
				puckIntent.putExtras(odkFormParams);
				startActivity(puckIntent);
			}
		});
		
		// Hook up handler for return to CommCare button
		// This is invoked by faking a button click when we return from processing a test
		returnButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.i("DIAGNOSTICS", "In return button...exiting 3");
				Intent returningIntent = new Intent(getIntent());
                Bundle returnBundle = new Bundle();
                
		    	if (testType.equals("") || (photoName.equals("")))
		    	{
		    		Log.i("DIAGNOSTICS", "Returning with no data");
		    		finish();
		    	}
		    	else
		    	{		    		
		    		JSONObject [] flds = DiagnosticsUtils.getFields(description, "fields");
		    		
		    		for (int i=0;i<flds.length;i++)
		    		{
		    			JSONObject field = flds[i];
		    			
		    			if (field.has("name") && field.has("type") && field.has("result"))
		    			{
		    				try {
		    					if ((field.getString("type").equals("test")) || (field.getString("type").equals("control")))
		    					{	
		    						returnBundle.putString(field.getString("name"), field.getString("result"));
		    					}
		    				} catch (JSONException e) {
		    					e.printStackTrace();
		    				}
		    			}
		    		}
		    		
		    		returnBundle.putString("testType", testType);
		    		returnBundle.putString("alignedPhotoPath", DiagnosticsUtils.appFolder + DiagnosticsUtils.alignedPhotoDir + photoName + "_aligned.jpg");
		    		returnBundle.putString("processedPhotoPath", DiagnosticsUtils.getMarkedupPhotoPath(photoName));
		    		returnBundle.putString("patientID", patientID);
		    		//returnBundle.putString("json", description.toString());
		        	
		    		try {
		    			returnBundle.putString("json", description.toString(2));
		    			description.put("processed", false);
						DiagnosticsUtils.writeFile(DiagnosticsUtils.getJsonPath(photoName), description.toString(2));
					} catch (Exception e) {
						e.printStackTrace();
					}
		    		try {
						Log.i("DIAGNOSTICS", "FINISHING photoName = " + DiagnosticsUtils.getMarkedupPhotoPath(photoName));
						//returningIntent.putExtra("odk_intent_bundle", returnBundle);
						//returningIntent.putExtra("odk_intent_data", "----");
						returningIntent.putExtra("elapsed_time", DiagnosticsUtils.elapsedTimeInt);
						returningIntent.putExtra("hiv_test_dflag", DiagnosticsUtils.dflagS);
						Log.d("LaunchActivity", "dflag: " + DiagnosticsUtils.dflag);

						LaunchActivity.this.setResult(Activity.RESULT_OK, returningIntent);
					} catch (Exception e) {
						e.printStackTrace();
					}

	                finish();
		    	}	
			}
		});
	}	
	private void setupDirs()
	{
		for(File f: this.getExternalFilesDirs(null)) {
			Log.d("writable path", f.getAbsolutePath());
		}

		Log.i("SET UP DIRS", "STARTING");
		Log.i("SET UP DIRS", DiagnosticsUtils.appFolder);
		//Create the app folder if it doesn't exist:
		boolean test = new File(DiagnosticsUtils.appFolder).mkdirs();
		Log.i("SET UP DIRS", ""+test);
		test = new File((DiagnosticsUtils.appFolder) + (DiagnosticsUtils.photoDir)).mkdirs();
		Log.i("SET UP DIRS", ""+test);
		test = new File((DiagnosticsUtils.appFolder) + (DiagnosticsUtils.alignedPhotoDir)).mkdirs();
		Log.i("SET UP DIRS", ""+test);
		new File((DiagnosticsUtils.appFolder) + (DiagnosticsUtils.templateDir)).mkdirs();
		new File((DiagnosticsUtils.appFolder) + (DiagnosticsUtils.templateImageDir)).mkdirs();
		new File((DiagnosticsUtils.appFolder) + (DiagnosticsUtils.markupDir)).mkdirs();
		new File((DiagnosticsUtils.appFolder) + (DiagnosticsUtils.jsonDir)).mkdirs();
	}
		
	@Override
	public void onBackPressed() {
		//This override is needed in order to avoid going back to the AfterPhotoTaken activity
		Intent setIntent = new Intent(Intent.ACTION_MAIN);
        setIntent.addCategory(Intent.CATEGORY_HOME);
        setIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(setIntent);
	}

    @Override
    protected void onResume() throws NullPointerException {
            super.onResume();  
            Log.i("Diagnostics", "Resuming");

            if(description != null)
            {
				Log.i("LaunchActivity", "exiting...");
	            // If we're returning from processing a test, simulate a return button click
	            // so we can exit the application gracefully. 
	            if (description.has("processed"))
	            {
	            	try {
						if (description.getBoolean("processed") || DiagnosticsUtils.exit)
						{
							Log.i("LaunchActivity", "exiting 1");
							description = DiagnosticsUtils.loadDescription(photoName);
							Bundle returnParams = getIntent().getExtras();
							for(String key: returnParams.keySet()) {
								Log.d("LaunchActivity", key + " is a key in the bundle.");
							}
							if(description.getBoolean("processed")) {
								Log.i("LaunchActivity", "exiting 2");
								returnButton.performClick();
							}
							else {
								Intent returnIntent = new Intent();
								returnIntent.putExtra("action","return");
								returnIntent.putExtra("temperature", DiagnosticsUtils.temperature);
								returnIntent.putExtra("relativeHumidity", DiagnosticsUtils.humidity);
								returnIntent.putExtra("ambientLight", DiagnosticsUtils.light);
								returnIntent.putExtra("elapsed_time", DiagnosticsUtils.elapsedTime);
								setResult(RESULT_OK, returnIntent);

								finish();
							}
						}

					} catch (JSONException e) {
						e.printStackTrace();
					}
	            }     
            }
        System.out.println("Test type at end of resume: " + DiagnosticsUtils.getTestType());
    }
    
	public void setupSpinner() 
	{	
	    File dir = new File(DiagnosticsUtils.internalSDCardPath + DiagnosticsUtils.templateDir);
		Log.i("Initial template dir", DiagnosticsUtils.internalSDCardPath + DiagnosticsUtils.templateDir);

		rdtNames = dir.list(new FilenameFilter() {
			public boolean accept (File dir, String name) {
				if (new File(dir,name).isDirectory())
					return false;
				return name.toLowerCase().endsWith(".txt");
			}
		});

		for(int i = 0; i < rdtNames.length; i++){
			rdtNames[i] = rdtNames[i].substring(0, rdtNames[i].lastIndexOf(".txt"));
		}
		
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, rdtNames);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinny.setPrompt("Select Test Type");
		spinny.setAdapter(adapter);
		spinny.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
				testType = parent.getItemAtPosition(position).toString();
				//Get the human readable label from the test description file
				description = DiagnosticsUtils.loadTemplate(testType);
				label = testType;
				if (description.has("label")) {
					try {
						label = description.getString("label");
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
				if (description.has("type")) {
					try {
						String r = description.getString("type");
						if (r.equals("ratiometric")) {
							ratiometric = true;
						} else {
							ratiometric = false;
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}
				} else {
					ratiometric = false;
				}
				Log.i("Diagnostics", "RATIOMETRIC IS " + ratiometric);

				TextView pid = (TextView) findViewById(R.id.PatientID);
				pid.setText("Patient ID:\n" + patientID);

				TextView tType = (TextView) findViewById(R.id.Test_Type);
				tType.setText("Test Type:\n" + label);

				// photo name is now the same for each test: the text file is appended to the answer form
				// (this will cause cascading consquences in original source, because photoName has various dependencies
				//photoName = testType + "_" + patientID;
				try {
					if(DiagnosticsUtils.getTestType().equals("FirstResponse_HIV"))
						photoName = "first_response_output";
					else if(DiagnosticsUtils.getTestType().equals("Zim_Determine_HIV"))
						photoName = "determine_output";
					else if(DiagnosticsUtils.getTestType().equals("ChemBio_HIV"))
						photoName = "chembio_output";
					else if(DiagnosticsUtils.getTestType().equals("CareStart_Malaria_Pf_HRP2"))
					{
						photoName = "carestart_mal_pf_hrp2_output";
					}
					else if(DiagnosticsUtils.getTestType().equals("CareStart_Malaria_Pf_PAN")) {
						photoName = "carestart_mal_pf_pan_output";
					}
					else if(DiagnosticsUtils.getTestType().equals("CareStart_Malaria_PAN_pLDH")) {
						photoName = "carestart_mal_pan_pldh_output";
					}
					else if(DiagnosticsUtils.getTestType().equals("SD_Malaria_Pf")) {
						photoName = "sd_mal_pf_output";
					}
					else if(DiagnosticsUtils.getTestType().equals("SD_Malaria_Pan_Pf")) {
						photoName = "sd_mal_pan_pf_output";
					}
					else if(DiagnosticsUtils.getTestType().equals("FirstResponse_Malaria_Pf")) {
						photoName = "firstresponse_mal_pf_output";
					}
					else
						photoName = "syphilis_output";
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			public void onNothingSelected(AdapterView<?> parent) {
				Toast.makeText(parent.getContext(), "Nothing selected.", Toast.LENGTH_LONG).show();
			}

		});

		// check passed in extras for which one to auto select: determine, first_response, or chembio
		// harcoded numbers refer to the order of the files in testDescription dir
		// hardcoded strings refer to values given in the form
		try {

			for(int i = 0; i < spinny.getCount(); i++) {
				if(getIntent().getExtras().getString("testType").equals(spinny.getItemAtPosition(i))) {
					spinny.setSelection(i);
				}

			}
			/*
			if(getIntent().getExtras().getString("testType").equals("Zim_Determine_HIV")) {
				Log.i("LaunchActivity","Setting to 3");
				spinny.setSelection(2);
			}
			else if(getIntent().getExtras().getString("testType").equals("FirstResponse_HIV")) {
				Log.i("LaunchActivity","Setting to 2");
				spinny.setSelection(1);
			}
			*/
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}
