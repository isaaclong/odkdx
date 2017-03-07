package org.opencv.pocdiagnostics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
/**
 * Contains methods and data shared across the mScan application.
 */
public class DiagnosticsUtils {
	
	private static String tag = "Utils";
	
	//Prevent instantiations
	private DiagnosticsUtils(){}

	private static double threshold = 0;
	private static double percentPixels = 0;
	private static double boxWidth = 0;
	private static double numColumns = 0;
	private static String testType = "";

	//Added for bluetooth and timer
	public static boolean exit = false;
	public static double temperature = 0;
	public static double humidity = 0;
	public static double light = 0;
	public static Date formStartTime;
	public static Date endTime;
	public static String elapsedTime = "";
	public static int elapsedTimeInt = 0;

	public static String hiv1 = "";
	public static String hiv2 = "";
	public static String control = "";

	public static String controld = "";
	public static String resultd = "";

	public static boolean dflag = false;
	public static String dflagS = "False"; // default false, if invalid will not change
	
	public static String appFolder2 = Environment.getExternalStorageDirectory().toString() + "/Diagnostics/";
	public static String appFolder = "/storage/extSdCard/Android/data/org.opencv.pocdiagnostics/files/Diagnostics/";
	//public static final String appFolder = "/storage/sdcard1/Diagnostics/";

	// quickly updating this for external SD card usage
	//public static final String internalSDCardPath = "/storage/sdcard0/Diagnostics/";
	//public static final String internalSDCardPath = "/storage/sdcard1/Diagnostics/";
	public static String internalSDCardPath = "/storage/extSdCard/Android/data/org.opencv.pocdiagnostics/files/Diagnostics/";
	public static final String photoDir = "CapturedPhotos/";
	public static final String alignedPhotoDir = "AlignedPhotos/";
	public static final String jsonDir = "ProcessedData/";
	public static final String markupDir = "MarkedupPhotos/";
	public static final String templateImageDir = "TemplateImages/";
	public static final String templateDir = "TestDescriptions/";
	public static final String xFormDir = "XForms/";
	
	public static String getPhotoPath(String photoName){
		return appFolder + photoDir + photoName + ".jpg";
	}
	public static String getAlignedPhotoPath(String photoName){
		return appFolder + alignedPhotoDir + photoName + ".jpg";
	}
	public static String getJsonPath(String photoName){
		return appFolder + jsonDir + photoName + ".txt";
	}
	public static String getMarkedupPhotoPath(String photoName){
		return appFolder + markupDir + photoName + "_processed.jpg";
	}
	public static String getWorkingPhotoPath(String photoName){
		return appFolder + markupDir + photoName;
	}
	public static String getTemplatePath(String testType){
		return internalSDCardPath + templateDir + testType + ".txt";
	}
	public static String getTemplateImagePath(String testType){
		return appFolder + templateImageDir + testType + ".jpg";
	}
	public static String getXFormPath(String photoName){
		return appFolder + xFormDir + photoName + ".xml";
	}
	
	public static void displayImageInWebView(WebView myWebView, String photoName, boolean processed)
    {	
		String imagePath = "";
		if (processed)
			imagePath = getMarkedupPhotoPath(photoName);
		else
			imagePath = getAlignedPhotoPath(photoName);
			

    	//JSONObject description = loadDescription(photoName);
    	
		myWebView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
		myWebView.getSettings().setBuiltInZoomControls(true);
		myWebView.getSettings().setDefaultZoom(WebSettings.ZoomDensity.FAR);
		myWebView.setVisibility(View.VISIBLE);
		
		// HTML is used to display the image.
		String html =   "<body bgcolor=\"Grey\">+" +
						//"<p>" +  + "</p>" +
						"<center> <img src=\"file:///" +
						imagePath +
						// Appending the time stamp to the filename is a hack to prevent caching.
						"?" + new Date().getTime() + 
						"\" width=\"350\" > " +
						"<table>";

		html += "</table></center></body>";
		       
		myWebView.loadDataWithBaseURL("file:///unnecessary/",
								 html, "text/html", "utf-8", "");
    }
    /**
     * Check if the given class has the given method.
     * Useful for dealing with android comparability issues (see getUsableSpace)
     * @param c
     * @param method
     * @return
     */
	public static <T> boolean hasMethod(Class<T> c, String method){
		Method methods[] = c.getMethods();
		for(int i = 0; i < methods.length; i++){
			if(methods[i].getName().equals(method)){
				return true;
			}
		}
		return false;
	}
	/**
	 * Combine the elements in the array using the + operator
	 * @param array
	 */
	public static String sum(String[] stringArray){
		String sum = "";
		for(int i = 0; i < stringArray.length; i++){
			sum += stringArray[i];
		}
		return sum;
	}
	/**
	 * Combine the elements in the array using the + operator
	 * @param array
	 */
	public static Number sum(Number[] numberArray){
		Double sum = 0.0;
		for(int i = 0; i < numberArray.length; i++){
			sum += numberArray[i].doubleValue();
		}
		return sum;
	}
	
	public static JSONObject loadDescription(String path)
	{
		JSONObject description = null;
		String filename = getJsonPath(path);
		try {
			String descriptionString = readFile(filename);
			description = new JSONObject(descriptionString);
			Log.i(tag, "Loaded description " + filename);
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
		return description;
	}
	
	public static JSONObject[] getFields(JSONObject description, String label)
	{
		JSONObject[] fields = new JSONObject[0];
		try 
		{	
			if (description.has(label))
			{
				JSONArray f = description.getJSONArray(label);
			    ArrayList<JSONObject> fields1 = new ArrayList<JSONObject>();
			    for (int i = 0; i < f.length(); i++) 
			    {
			        JSONObject next_field = f.getJSONObject(i);
			        fields1.add(next_field);
			    }
	
			    fields = new JSONObject[fields1.size()];
			    fields1.toArray(fields);
			    return fields;
			}
			else
			{
				Log.i(tag, "No " + label + " found");
			}
		}
		catch (JSONException e) {
			e.printStackTrace();
		}
		return fields;
	}

	// processes the JSON data in testDescription file
	public static JSONObject loadTemplate(String testType)
	{
		JSONObject description = null;
		
		try {
			String path = DiagnosticsUtils.getTemplatePath(testType);
			String descriptionString = readFile(path);
			description = new JSONObject(descriptionString);
			Log.i(tag, "Loaded template " + path);
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
		return description;
	}
	
	public static String readFile(String filename) throws IOException 
	{
	    StringBuilder text = new StringBuilder();
	    String NL = System.getProperty("line.separator");
	    Scanner scanner = new Scanner(new FileInputStream(new File(filename)), "UTF-8");
	    try {
	      while (scanner.hasNextLine()){
	        text.append(scanner.nextLine() + NL);
	      }
	    }
	    finally{
	      scanner.close();
	    }
	    return text.toString();
	}
	
	//Try to find and read a barcode from the aligned image
	public static Result readBarcode(String filename)
	{
		Result result;				
		Bitmap bitmap = BitmapFactory.decodeFile(filename);

		int barcodeWidth = bitmap.getWidth(), barcodeHeight = bitmap.getHeight();
        int[] pixels = new int[barcodeWidth * barcodeHeight];
        bitmap.getPixels(pixels, 0, barcodeWidth, 0, 0, barcodeWidth, barcodeHeight);
        bitmap.recycle();
        bitmap = null;
        RGBLuminanceSource source = new RGBLuminanceSource(barcodeWidth, barcodeHeight, pixels);
        BinaryBitmap bBitmap = new BinaryBitmap(new HybridBinarizer(source));
        MultiFormatReader reader = new MultiFormatReader();
        try
        {
            result = reader.decode(bBitmap);
	        return result;
        }
        catch (NotFoundException e)
        {
            Log.e("EXCEPTION", "decode exception", e);
            return null;
        }
	}
	
	public static void writeFile(String filename, String text) throws IOException 
	{
	    Writer out = new OutputStreamWriter(new FileOutputStream(filename), "UTF-8");
	    try {
	      out.write(text);
	    }
	    finally {
	      out.close();
	    }
	}

	public static boolean getExit() {return exit;}

	public static void setThreshold(String t)
	{
		threshold = Double.parseDouble(t);
	}

	public static double getThreshold()
	{
		return threshold;
	}

	public static void setPercentPixels(String t)
	{
		percentPixels = Double.parseDouble(t);
	}

	public static double getPercentPixels()
	{
		return percentPixels;
	}
	
	public static void setBoxWidth(String t)
	{
		boxWidth = Double.parseDouble(t);
	}

	public static double getBoxWidth()
	{
		return boxWidth;
	}

	public static void setNumColumns(String t)
	{
		numColumns = Double.parseDouble(t);
	}

	public static double getNumColumns()
	{
		return numColumns;
	}
	
	public static void setTestType(String t)
	{
		testType = t;
	}

	public static String getTestType()
	{
		return testType;
	}
	
}
