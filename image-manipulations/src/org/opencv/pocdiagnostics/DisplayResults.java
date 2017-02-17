package org.opencv.pocdiagnostics;

import org.joda.time.Duration;
import org.joda.time.Interval;
import org.json.JSONException;
import org.json.JSONObject;
import org.kxml2.kdom.Element;
import org.kxml2.kdom.Node;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import org.joda.time.DateTime;

public class DisplayResults extends Activity {
	
	private String photoName;
	private String patientID;
	private String testType;
	private String label;
    private LinearLayout content;
    JSONObject description = null;
	JSONObject [] fields = new JSONObject [0];
    
    int index = 4;
    
    @Override
	protected void onCreate(Bundle savedInstanceState) 
    {	
    	super.onCreate(savedInstanceState);
    	setContentView(R.layout.display_results);
    	content = (LinearLayout) findViewById(R.id.myLinearLayout);
    	content.setVisibility(View.VISIBLE);
    	
   		Bundle extras = getIntent().getExtras();
		photoName = extras.getString("photoName");
		patientID = extras.getString("patientID");
		testType = extras.getString("testType");
		label = extras.getString("label");
		
		TextView pid =  (TextView) findViewById(R.id.PatientID);
		pid.append(" " + patientID);
		
		TextView tType =  (TextView) findViewById(R.id.Test_Type);
		tType.append(" " + label);
		
    	this.description = DiagnosticsUtils.loadDescription(photoName);
		this.fields = DiagnosticsUtils.getFields(description, "fields");

		// save the current time and then calculate the elapsed time
		DateTime start = new DateTime(DiagnosticsUtils.formStartTime);
		DateTime end = new DateTime();
		Interval elapsedTime = new Interval(start, end);
		Duration duration = elapsedTime.toDuration();
		DiagnosticsUtils.elapsedTime = duration.getStandardMinutes() + " minutes";
		DiagnosticsUtils.elapsedTimeInt = (int)duration.getStandardMinutes();
		Log.d("DisplayResult","elapsed time: " + DiagnosticsUtils.elapsedTime);

		boolean invalid = false;
		boolean hiv1Compare = false;
		boolean hiv2Compare = false;
		boolean controlCompare = false;
		String hiv1f = "a";
		String hiv2f = "a";
		String controlf = "a";
		String controld = "a";
		String resultd = "a";
		Log.d("Flag", "test type: " + DiagnosticsUtils.getTestType());
		for (int i=0;i<fields.length;i++)
		{
			JSONObject field = fields[i];
			Log.d("DisplayResults", "fields: " + fields[i]);
			if (field.has("name") && field.has("type") && field.has("result"))
			{
				try {
					
					if (field.getString("type").equals("control"))
					{	
			    	    final TextView rowTextView = new TextView(this);
			    	    rowTextView.setTextSize(20);
			    	    rowTextView.setText(field.getString("name") + " : " + field.getString("result"));
			    	    content.addView(rowTextView, index);
			    	    index++;
			    	    
			    	    if (field.getString("result").equals("INVALID"))
			    	    {
			    	    	invalid = true;
			    	    	break;
			    	    }
					}

					if(DiagnosticsUtils.getTestType().equals("FirstResponse_HIV")) {
						Log.d("Flag", "This is first response");
						if(field.getString("name").equals("control")) {
							Log.d("Flag", "Setting FR Control");
							controlf = field.getString("result");
						}
						else if(field.getString("name").equals("two")) {
							Log.d("Flag", "Setting FR hiv2");
							hiv2f = field.getString("result");
						}
						else if(field.getString("name").equals("one")) {
							Log.d("Flag", "Setting FR hiv1");
							hiv1f = field.getString("result");
						}
					}

					else if(DiagnosticsUtils.getTestType().equals("Zim_Determine_HIV")) {
						if(field.getString("name").equals("control")) {
							controld = field.getString("result");
						}
						else if(field.getString("name").equals("Test")) {
							resultd = field.getString("result");
						}
					}
					
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}

		// assign the flag to be passed back
		Log.d("DisplayResult", controlf + "\n" + hiv2f + "\n" + hiv1f);
		//if(!controlf.equals("") && !hiv2f.equals("") && !hiv1f.equals("")) {
			Log.i("DisplayResult", "Assigning dflag");
			if(DiagnosticsUtils.getTestType().equals("FirstResponse_HIV")) {
				Log.d("Flag", "Setting FR dflag");
				DiagnosticsUtils.dflag = (DiagnosticsUtils.control.equals(controlf) && (DiagnosticsUtils.hiv1.equals(hiv1f)) && (DiagnosticsUtils.hiv2.equals(hiv2f)));
			}
			else if(DiagnosticsUtils.getTestType().equals("Determine_HIV"))
				DiagnosticsUtils.dflag = (DiagnosticsUtils.controld.equals(controld) && (DiagnosticsUtils.resultd.equals(resultd)));
			if(DiagnosticsUtils.dflag) DiagnosticsUtils.dflagS = "True";
			else DiagnosticsUtils.dflagS = "False";
		//}
    	
		//Do not display test results if the test is invalid
		if (!invalid)
		{
			for (int i=0;i<fields.length;i++)
			{
				JSONObject field = fields[i];
				
				if (field.has("name") && field.has("type") && field.has("result"))
				{
					try {
						
						if ((field.getString("type").equals("test")))
						{	
				    	    final TextView rowTextView = new TextView(this);
				    	    rowTextView.setTextSize(20);
				    	    rowTextView.setText(field.getString("name") + " : " + field.getString("result"));
				    	    content.addView(rowTextView, index);
				    	    index++;
						}
						
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		// Hook up handler for launching Collect button
		Button saveAndExit = (Button) findViewById(R.id.LaunchCollectButton);
		saveAndExit.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

                // Adding call to ExportToCollect Activity to generate ODKCollect results form
				// ***we now attach the output JSON file to the end of the form, so there is no need
				// for the instance generation in ExportToCollect: simply go back to the odk
				// app in progress

                Log.d("DisplayResults", "Calling ExportToCollect");
				Intent exportIntent = new Intent(getApplication(), ExportToCollect2.class);
                exportIntent.putExtra("photoName", photoName);
                exportIntent.putExtra("patientID", patientID);
                exportIntent.putExtra("testType",testType);
                exportIntent.putExtra("label",label);

				// Below is a method used in an example breathing application for calling ODKCollect from an app
				// tested and does not work, could be missing something
                //exportIntent.putExtra("odk_intent_data", "POSITIVE");
                //setResult(RESULT_OK, exportIntent);
                //startActivity(exportIntent);

				/*
					To test inserting the textfile before an instance of the form is there
					1) find form save file in odk/.cache
					2) change save file name from .xml.save to just xml
					3) move this file to instance directory
					4) when we return from app, see if, when saved, it picks up the textfile
				 */

				// construct the instance path, since we can no longer control it

				// A few assumptions for the following:
				// form name is assumed to be the same for all runs

				String formName = "CHAI";
				//if(photoName.equals("syphilis_output")) formName = "ANC";
				String instancePath = "/sdcard/odk/instances/";
				//String instancePath = "/storage/extSdCard/Android/data/org.odk.collect.android/files/odk/instances/"; // for GSID external storage
				Log.d("Files", "Path: " + instancePath);
				File f = new File(instancePath);
				File instance_dirs[] = f.listFiles();
				Log.d("Files", "Num instance dirs: " + instance_dirs.length);
				String instance_name = "";
				for(int i = 0; i < instance_dirs.length; i++) {
					Log.d("Files", "Filename: " + instance_dirs[i].getName());
					if(instance_dirs[i].getName().contains(formName)) { // this is fairly weak, should be stronger. If for some reason another instance in there there is a chance it could choose wrong one
						instance_name = instance_dirs[i].getName() + "/";
					}
				}

				// copy over processed_data.txt in /Diagnostics/ProcessedData

				String pDataPath = DiagnosticsUtils.getJsonPath(photoName);
				int len;

				try {
					Log.i("Files:", "Filepath: " + instancePath + instance_name + new File(pDataPath).getName());
					// copy processed_data.txt in /Diagnostics/ProcessedData to instance dir
					InputStream fis = new FileInputStream(pDataPath);
					FileOutputStream fos = new FileOutputStream(instancePath + instance_name + new File(pDataPath).getName());
					byte[] buf = new byte[1024];
					while((len = fis.read(buf)) > 0) {
						fos.write(buf, 0, len);
					}

					// copy output.txt to SD card and create directory specific to the instance
					String sdHomeDir = "/storage/extSdCard/Android/data/org.opencv.pocdiagnostics/files/";
					new File(sdHomeDir + instance_name).mkdirs();
					fis = new FileInputStream(pDataPath);
					fos = new FileOutputStream(sdHomeDir + instance_name + new File(pDataPath).getName());
					buf = new byte[1024];
					while((len = fis.read(buf)) > 0) {
						fos.write(buf, 0, len);
					}

					// copy over image file to instance dir
					String imagePath = DiagnosticsUtils.getMarkedupPhotoPath(photoName);
					fis = new FileInputStream(imagePath);
					fos = new FileOutputStream(instancePath + instance_name + new File(imagePath).getName());
					// Transfer bytes from in to out
					buf = new byte[1024];
					while ((len = fis.read(buf)) > 0) {
						fos.write(buf, 0, len);
					}

					// copy image.jpg to SD card
					fis = new FileInputStream(imagePath);
					fos = new FileOutputStream(sdHomeDir + instance_name + "markedup_" + new File(imagePath).getName());
					buf = new byte[1024];
					while ((len = fis.read(buf)) > 0) {
						fos.write(buf, 0, len);
					}

					// copy over original captured image and the aligned image
					imagePath = DiagnosticsUtils.getPhotoPath(photoName);
					fis = new FileInputStream(imagePath);
					fos = new FileOutputStream(sdHomeDir + instance_name + "captured_" + new File(imagePath).getName());
					buf = new byte[1024];
					while ((len = fis.read(buf)) > 0) {
						fos.write(buf, 0, len);
					}

					imagePath = DiagnosticsUtils.getAlignedPhotoPath(photoName);
					fis = new FileInputStream(imagePath);
					fos = new FileOutputStream(sdHomeDir + instance_name + "aligned_" + new File(imagePath).getName());
					buf = new byte[1024];
					while ((len = fis.read(buf)) > 0) {
						fos.write(buf, 0, len);
					}

					fos.close();
					fis.close();



				} catch(Exception e) {
					Log.e("DisplayResults","Error copying output text file or image to instance directory");
					e.printStackTrace();
				}

				DiagnosticsUtils.exit = true;

		        finish();
			}
		});
		
		DiagnosticsUtils.displayImageInWebView((WebView) findViewById(R.id.webview), photoName, true);



	}


}