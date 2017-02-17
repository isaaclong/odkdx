package org.opencv.pocdiagnostics;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import android.util.Log;
  
public class RatiometricProcessor 
{
	private Scalar color = new Scalar(0,255,0);
	private String tag = "Processor";
	private Mat image = new Mat();
	private Mat result = new Mat();
	private String photoName;
	private String testType;
	private JSONObject description = null;
	private JSONObject [] fields = new JSONObject [0];
	
	public RatiometricProcessor(){};
	
	public boolean processImage(String photoName, String type)
	{
		boolean success = true;
		this.photoName = photoName;
		this.testType = type;
		this.image = Highgui.imread(DiagnosticsUtils.getAlignedPhotoPath(photoName));
		if (image.empty())
		{	
			Log.e(tag,"Image load failed");
			return false;
		}
		this.description = DiagnosticsUtils.loadDescription(photoName);
		this.fields = DiagnosticsUtils.getFields(description, "fields");
		
		long startTime = System.currentTimeMillis();
		
		//PROCESS HERE
		//success = processFields(threshold, percentPixels, boxWidth, numColumns);
		
		long endTime = System.currentTimeMillis();

		long processFieldsTime = endTime - startTime;
		Log.i(tag, "time to process fields is " + processFieldsTime);
		
		//Save image
		Highgui.imwrite(DiagnosticsUtils.getMarkedupPhotoPath(photoName), image);
		Log.i(tag, "Saved image as " + DiagnosticsUtils.getMarkedupPhotoPath(photoName));
		return success;
	}
}