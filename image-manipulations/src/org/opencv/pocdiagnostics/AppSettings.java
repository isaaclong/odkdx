package org.opencv.pocdiagnostics;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class AppSettings extends Activity {

	EditText thresholdEdit, percentPixelsEdit, boxWidthEdit, numColumnsEdit;
	String testType;
	String patientID;
	String label;
	String [] rdtNames = new String[0];
	int selectedIndex = 0;
	private JSONObject description = null;
	
    private double threshold = -1;
    private double percentPixels = -1;
    private double boxWidth = -1;
    private double numColumns = -1;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
		setContentView(R.layout.settings_activity); // Setup the UI
		
		Bundle extras = getIntent().getExtras();
		if (extras == null) {
			Log.i("Diagnostics","No test type to load");
			return;
		}
		
		patientID = extras.getString("patientID");
		testType = extras.getString("testType");
		label = extras.getString("label");

		threshold = extras.getDouble("threshold");
		percentPixels = extras.getDouble("percentPixels");
		numColumns = extras.getDouble("numColumns");
		boxWidth = extras.getDouble("boxWidth");
		
		TextView type = (TextView) findViewById(R.id.type);
		type.setText("Test: " + label);
		
		thresholdEdit = (EditText) findViewById(R.id.threshold);
		percentPixelsEdit  = (EditText) findViewById(R.id.percentPixels);
		boxWidthEdit  = (EditText) findViewById(R.id.boxWidth);
		numColumnsEdit  = (EditText) findViewById(R.id.numColumns);
				
		//Restore preferences from the test description
		try
		{
			description = DiagnosticsUtils.loadTemplate(testType);
			if (description == null)
			{
				Log.i("NULL", "DESCRIPTION IS NULL");
			}
	    	
			if (threshold != -1)
			{
				String restoredThreshold = "" + threshold;
				thresholdEdit.setText(restoredThreshold, TextView.BufferType.EDITABLE);
			}
			else if (description.has("threshold")) 
			{
				String restoredThreshold = description.getString("threshold");
				thresholdEdit.setText(restoredThreshold, TextView.BufferType.EDITABLE);
			}
			else
			{
				thresholdEdit.setText("0", TextView.BufferType.EDITABLE);
			}
	
			if (percentPixels != -1)
			{
				String restoredPercentPixels = "" + percentPixels;
				percentPixelsEdit.setText(restoredPercentPixels, TextView.BufferType.EDITABLE);
			}
			else if (description.has("percentPixels"))
			{
				String restoredPercentPixels = description.getString("percentPixels");
				percentPixelsEdit.setText(restoredPercentPixels, TextView.BufferType.EDITABLE);
			}
			else
			{
				percentPixelsEdit.setText("35", TextView.BufferType.EDITABLE);
			}
	    	
			if (boxWidth != -1) 
			{
				String restoredBoxWidth = "" + boxWidth;
				boxWidthEdit.setText(restoredBoxWidth, TextView.BufferType.EDITABLE);
			}
			else if (description.has("boxWidth")) 
			{
				String restoredBoxWidth = description.getString("boxWidth");
				boxWidthEdit.setText(restoredBoxWidth, TextView.BufferType.EDITABLE);
			}
			else
			{
				boxWidthEdit.setText("40", TextView.BufferType.EDITABLE);
			}
	
			if (numColumns != -1) 
			{
				String restoredNumColumns = "" + numColumns;
				numColumnsEdit.setText(restoredNumColumns, TextView.BufferType.EDITABLE);
			}
			else if (description.has("numColumns")) 
			{
				String restoredNumColumns = description.getString("numColumns");
				numColumnsEdit.setText(restoredNumColumns, TextView.BufferType.EDITABLE);
			}
			else
			{
				numColumnsEdit.setText("5", TextView.BufferType.EDITABLE);
			}
		}
		catch (JSONException e) 
		{
			e.printStackTrace();
		}
		
		//Save settings
		Button saveButton = (Button) findViewById(R.id.SaveButton);
		saveButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				//Save data
				try 
				{	
					description.put("threshold", thresholdEdit.getText().toString());
					description.put("percentPixels", percentPixelsEdit.getText().toString());
					description.put("boxWidth", boxWidthEdit.getText().toString());
					description.put("numColumns", numColumnsEdit.getText().toString());
					
					DiagnosticsUtils.writeFile(DiagnosticsUtils.getTemplatePath(testType), description.toString(2));
					Log.i("Settings", "Saved JSON output as " + DiagnosticsUtils.getTemplatePath(testType));
				} 
				catch (Exception e) 
				{
					e.printStackTrace();
				}

				finish();
			}
		});
    }
}
