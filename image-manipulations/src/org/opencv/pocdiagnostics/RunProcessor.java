package org.opencv.pocdiagnostics;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;

/*
 * RunProcessor is used to run the image processing algorithms on a separate thread.  
 */
public class RunProcessor implements Runnable {
	
	public enum Mode {
	    LOAD, LOAD_ALIGN, PROCESS, BATCH, ALIGN_RATIOMETRIC, PROCESS_RATIOMETRIC
	}
		
	private Mode mode;
	private Handler handler;
	private String photoName;
	private String testType;
	private double threshold = 0;
	private double percentPixels = 0;
	private double boxWidth = 0;
	private double numColumns = 0;
	private Aligner aligner;
	private RatiometricAligner ratiometricAligner;
	private Processor processor;
	private RatiometricProcessor ratiometricProcessor;
	private String resultCsv = "photoName,testType,threshold,percentPixels,boxWidth,numCols\n";
	private JSONObject description = null;

	public RunProcessor(Handler handler, String photoName, String testType, double restoredThreshold, double percentPixels, double boxWidth, double numColumns) 
	{
		resultCsv = "photoName,testType,threshold,percentPixels,boxWidth,numCols,colorChannel\n";
		this.handler = handler;
		this.photoName = photoName;
		this.testType = testType;
		this.threshold = restoredThreshold;
		this.percentPixels = percentPixels;
		this.boxWidth = boxWidth;
		this.numColumns = numColumns;
		this.aligner = new Aligner();
		this.processor = new Processor();
		this.ratiometricAligner = new RatiometricAligner();
		this.ratiometricProcessor = new RatiometricProcessor();
	}
	/**
	 * This method sets the mode the processor is to run in.
	 * @param mode
	 */
	public void setMode(Mode mode) {
		this.mode = mode;
	}
	//@Override
	public void run() {
		
		Message msg = new Message();
		msg.arg1 = 0;//A value of 1 means success.
		msg.what = mode.ordinal();

		if(mode == Mode.PROCESS) 
		{	
			Log.i("Diagnostics","Processing: " + photoName);
			if (processor.processImage(photoName, testType, threshold, percentPixels, boxWidth, numColumns, "green"))
				msg.arg1 = 1;//indicates success
			msg.arg1 = 1;
		}
		else if(mode == Mode.LOAD) 
		{
			msg.arg1 = 1;
		}
		else if(mode == Mode.LOAD_ALIGN) 
		{	
			Log.i("Diagnostics","Aligning: " + photoName);
			if (aligner.alignCapturedImage(photoName, testType, threshold, percentPixels, boxWidth, numColumns))
				msg.arg1 = 1;//indicates success
		}
		else if (mode == Mode.ALIGN_RATIOMETRIC)
		{
			Log.i("Diagnostics","Aligning ratiometric: " + photoName);
			if (ratiometricAligner.alignCapturedImage(photoName, testType))
				msg.arg1 = 1;//indicates success
		}
		else if(mode == Mode.PROCESS_RATIOMETRIC) 
		{
			Log.i("Diagnostics","Processing ratiometric: " + photoName);
			
			if (processor.processImage(photoName, testType, threshold, percentPixels, boxWidth, numColumns, "green"))
				msg.arg1 = 1;//indicates success
			msg.arg1 = 1;
			
			/*if (ratiometricProcessor.processImage(photoName, testType))
				msg.arg1 = 1;//indicates success
			msg.arg1 = 1;*/
		}
		
		
		else if(mode == Mode.BATCH) 
		{
			//photoName = "stronger_positive_no_flash";
			this.description = DiagnosticsUtils.loadDescription(photoName);
			JSONObject [] batchThresholds = DiagnosticsUtils.getFields(description, "batchThresholds");
			JSONObject [] batchPercentPixels = DiagnosticsUtils.getFields(description, "batchPercentPixels");
			JSONObject [] colorChannels = DiagnosticsUtils.getFields(description, "colorChannels");
			JSONObject [] batchNumCols = DiagnosticsUtils.getFields(description, "batchColumns");
			
			double [] thresholdArray = new double[batchThresholds.length];
			double [] percentPixelsArray = new double[batchPercentPixels.length];
			double [] numColumnsArray = new double[batchNumCols.length];
			String [] colorChannelsArray = new String[colorChannels.length];
			
			if (batchThresholds.length == 0)
			{
				Log.i("Diagnostics", "No thresholds");
				thresholdArray = new double[1];
				thresholdArray[0] = threshold;
			}
			else
			{
				try 
				{
					for (int i=0;i<batchThresholds.length;i++)
					{
						String s = "" + i;
						thresholdArray[i] = batchThresholds[i].getDouble(s);
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}	
			}
			
			if (batchPercentPixels.length == 0)
			{
				Log.i("Diagnostics", "No percent pixels");
				percentPixelsArray = new double[1];
				percentPixelsArray[0] = percentPixels;
			}
			else
			{
				try 
				{
					for (int i=0;i<batchPercentPixels.length;i++)
					{
						String s = "" + i;
						percentPixelsArray[i] = batchPercentPixels[i].getDouble(s);
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}	
			}
			
			if (batchNumCols.length == 0)
			{
				Log.i("Diagnostics", "No num cols");
				numColumnsArray = new double[1];
				numColumnsArray[0] = numColumns;
			}
			else
			{
				try 
				{
					for (int i=0;i<batchNumCols.length;i++)
					{
						String s = "" + i;
						numColumnsArray[i] = batchNumCols[i].getDouble(s);
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}	
			}
			
			if (colorChannels.length == 0)
			{
				Log.i("Diagnostics", "No color channels");
				colorChannelsArray = new String[1];
				colorChannelsArray[0] = "green";
			}
			else
			{
				try 
				{  
					for (int i=0;i<colorChannels.length;i++)
					{
						String s = "" + i;
						colorChannelsArray[i] = colorChannels[i].getString(s);
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}	
			}

			Log.i("Diagnostics","Processing batch: " + photoName);
			
			for (int i=0;i<thresholdArray.length;i++)
			{
				for (int j=0;j<percentPixelsArray.length;j++)
				{
					//for (int k=0;k<boxWidthArray.length;k++)
					//{
						for (int l=0;l<numColumnsArray.length;l++)
						{
							for (int m=0;m<colorChannelsArray.length;m++)
							{
								Log.i("Diagnostics", "Processing using: " + thresholdArray[i] + " " + percentPixelsArray[j] + " " + boxWidth + " " + numColumnsArray[l] + " " + colorChannelsArray[m]);
								processor.processImage(photoName, testType, thresholdArray[i], percentPixelsArray[j], boxWidth, numColumnsArray[l], colorChannelsArray[m]);
								addResultsToCsv(photoName, testType, thresholdArray[i], percentPixelsArray[j], boxWidth, numColumnsArray[l], colorChannelsArray[m]);
							}
						}
					//}
				}
			}
			
			try {
				DiagnosticsUtils.writeFile(DiagnosticsUtils.appFolder + DiagnosticsUtils.jsonDir + photoName + "_Batch.csv", resultCsv);
				Log.i("Diagnostics","Saved " + photoName + "_Batch.csv");
			} catch (IOException e) {
				e.printStackTrace();
			}
			msg.arg1 = 1;
		}
		
		//////////////////////////////////////////////////////////////
		
		/*else if(mode == Mode.BATCH) 
		{
			/*String [] filenames = {
					"hauna2-20131030100907",
					"hauna2-20131102112215",
					"hauna2-20131108154413",
					"hauna2-20131123085914",
					"hauna2-20131209082338",
					"hauna5-20131025145537",
					"hauna5-20131025164709",
					"hauna5-20131025171513",
					"hauna5-20131025173228",
					"hauna5-20131025200348",
					"hauna5-20131026125822",
					"hauna5-20131027090246",
					"hauna5-20131125192307",
					"hauna5-20131126200449",
					"mutare1-20131028141203",
					"mutare1-20131030142332",
					"mutare1-20131111140526",
					"mutare1-20131211080512",
					"mutare4-20131208213007",
					"nyanga2-20131030094536",
					"nyanga2-20131119124351",
					"nyanga2-20131120091953",
					"nyanga2-20131120102048",
					"nyanga2-20131120150139",
					"nyanga2-20131120150322",
					"nyanga2-20131120150638",
					"nyanga2-20131120150830",
					"nyanga2-20131120150957",
					"nyanga2-20131120151300",
					"nyanga2-20131120152642",
					"nyanga2-20131120152950",
					"nyanga2-20131120153123",
					"nyanga2-20131125122125",
					"nyanga2-20131125122252",
					"nyanga2-20131125122421",
					"nyanga2-20131125122604",
					"nyanga2-20131125123141",
					"nyanga2-20131125123638",
					"nyanga2-20131125124352",
					"nyanga2-20131125124640",
					"nyanga2-20131125125055",
					"nyanga2-20131125141817",
					"tombo-20131028110317",
					"tombo-20131028110515",
					"tombo-20131028120632",
					"tombo-20131127072447",
					"tombo-20131127081815",
					"tombo-20131127085855",
					"zindi1-20131024152117",
					"zindi2-20131031100020",
					"zindi2-20131031125801" };*/
			
			/*String [] filenames = {"hauna1-20131210112620",
					"hauna3-20131028093325",
					"zindi2-20131024100310",
					"hauna1-20131116102505",
					"hauna1-20131209114313",
					"hauna3-20131026130812",
					"hauna3-20131028100631",
					"mutare1-20131031144408",
					"mutare1-20131105153847",
					"mutare1-20131105154925",
					"mutare1-20131113143227",
					"mutare1-20131115124831",
					"mutare1-20131127081451",
					"mutare1-20131127082251",
					"mutare1-20131127082908",
					"mutare1-20131127083333",
					"mutare1-20131127083513",
					"mutare1-20131127083725",
					"mutare1-20131127083951",
					"mutare1-20131127084139",
					"mutare3-20131115105953",
					"mutare3-20131118124140",
					"mutare3-20131118130748",
					"nyanga3-20131213102911",
					"nyanga4-20131202143359",
					"nyanga4-20131202151422",
					"nyanga4-20131204145414",
					"nyanga4-20131212115604",
					"nyanga4-20131212115914",
					"tombo-20131105110926",
					"zindi1-20131203171334",
					"zindi1-20131203171514",
					"zindi1-20131203171826",
					"zindi1-20131203172224",
					"zindi1-20131210094534",
					"zindi1-20131210101759",
					"zindi1-20131210102521",
					"zindi1-20131210130235",
					"zindi1-20131210130515",
					"zindi1-20131212123128",
					"zindi1-20131212143631",
					"zindi1-20131212145035",
					"zindi1-20131213163241",
					"zindi1-20131215081305",
					"zindi1-20131215081529",
					"zindi1-20131215081825",
					"zindi1-20131217142148",
					"zindi1-20131217143756",
					"zindi2-20131115101532",
					"zindi2-20131115115534",
					"zindi2-20131115152033",
					"zindi2-20131116091301",
					"zindi2-20131116091535",
					"zindi2-20131116095921",
					"zindi2-20131116100128",
					"zindi2-20131210081543",
					"zindi2-20131210115529",
					"zindi2-20131210122352",
					"zindi2-20131210125051",
					"zindi2-20131210134534",
					"zindi2-20131210134910",
					"zindi2-20131212105405"};*/
			
			/*String [] filenames = {"nyanga1-20131029114531",
				"nyanga1-20131119105226",
				"nyanga3-20131029100452",
				"nyanga3-20131113104942",
				"nyanga4-20131115150536",
				"nyanga4-20131126142158",
				"zindi1-20131203172105"};*/
			
			/*for (int i=0;i<filenames.length;i++)   
			{
				testType = "FirstResponse_HIV";
				photoName = testType + "_" + filenames[i];
				threshold = 4;
				percentPixels = 50;
				boxWidth = 90;
				numColumns = 5;
				
				Log.i("Diagnostics","Processing: " + photoName);
				processor.processImage(photoName, testType, threshold, percentPixels, boxWidth, numColumns, "green");
				addResultsToCsv(photoName, testType, threshold, percentPixels, boxWidth, numColumns, "green");
				msg.arg1 = 1;
				
			}
			try {
				DiagnosticsUtils.writeFile(DiagnosticsUtils.appFolder + DiagnosticsUtils.jsonDir + photoName + "_Batch.csv", resultCsv);
				Log.i("Diagnostics","Saved " + photoName + "_Batch.csv");
			} catch (IOException e) {
				e.printStackTrace();
			}
			msg.arg1 = 1;
		}
		*/
		//////////////////////////////////////////////////////////////
		
		else 
		{
			Log.i("Diagnostics","Failed to load image: " + photoName);
		}
		
		handler.sendMessage(msg);
	
	}
	
	private void addResultsToCsv(String photoName, String testType, double threshold, double percentPixels, double boxWidth, double numCols, String colorChannel) 
	{
	    JSONObject description = DiagnosticsUtils.loadDescription(photoName);
		JSONObject [] fields = DiagnosticsUtils.getFields(description, "fields");
		
		resultCsv +=  photoName + "," + testType+ "," + threshold + "," + percentPixels + "," + boxWidth+ "," + numCols+ "," + colorChannel;
		for (int i=0;i<fields.length;i++)
		{
			JSONObject field = fields[i];
			
			if (field.has("name") && field.has("type") && field.has("result"))
			{
				try {
					
					if ((field.getString("type").equals("control")) || (field.getString("type").equals("test")))
					{	
						resultCsv +=  "," + field.getString("name") + "," + field.getString("result");
					}
					
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}
		
		resultCsv +=  "\n";
	}
}
