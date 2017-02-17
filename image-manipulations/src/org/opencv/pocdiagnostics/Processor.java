package org.opencv.pocdiagnostics;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import android.util.Log;
  
public class Processor 
{
	private String tag = "Processor";
	private Mat image = new Mat();
	private Mat result = new Mat();
	private JSONObject description = null;
	private JSONObject [] fields = new JSONObject [0];
	private double calculatedThreshold = 0;
	private Mat emptySpace = new Mat();
	
	private double averageIntensityDifference = 0;
	private double averageLineIntensity = 0;
	private double averageBackgroundIntensity = 0;  
	
	public Processor(){};
	
	public boolean processImage(String photoName, String type, double threshold, double percentPixels, double boxWidth, double numColumns, String colorChannel)
	{	
		this.image = Highgui.imread(DiagnosticsUtils.getAlignedPhotoPath(photoName));
		//this.image = Highgui.imread(DiagnosticsUtils.getAlignedPhotoPath("test5"));  
		
		if (image.empty())
		{	
			Log.e(tag,"Image load failed for " + DiagnosticsUtils.getAlignedPhotoPath(photoName));
			return false;
		}
		
		this.description = DiagnosticsUtils.loadDescription(photoName);
		
		//////
		//this.description = DiagnosticsUtils.loadTemplate(type);
		/////
		
		this.fields = DiagnosticsUtils.getFields(description, "fields");
		
		boolean success = processFields(photoName, threshold, percentPixels, boxWidth, numColumns, colorChannel);

		//Save image
		ArrayList<Integer> params = new ArrayList<Integer>();
		params.add(Highgui.CV_IMWRITE_JPEG_QUALITY);
		params.add(25);	
		
		Highgui.imwrite(DiagnosticsUtils.getMarkedupPhotoPath(photoName), image, params);
		Log.i(tag, "Saved image as " + DiagnosticsUtils.getMarkedupPhotoPath(photoName));
		return success;
	}

	private boolean processFields(String photoName, double threshold, double percentPixels, double boxWidth, double numColumns, String colorChannel) 
	{
		if (fields.length == 0)
		{
			Log.e(tag, "No fields");
			return false;
		}
		
		JSONArray processedFields = null;
		try 
		{
			numColumns = description.getInt("numColumns");
			processedFields = description.getJSONArray("fields");
		} 
		catch (JSONException e1) 
		{
			e1.printStackTrace();
		}
		
		result = new Mat();
		image.copyTo(result);
		
		//Normalize color intensities		
		/*for (int i=0;i<result.rows();i++)
		{
			for (int j=0;j<result.cols();j++)
			{
				double [] value = result.get(i, j);
				double sum = value[0] + value[1] + value[2];
				value[0] = (value[0]/sum)*255;
				value[1] = (value[1]/sum)*255;
				value[2] = (value[2]/sum)*255;
				
				result.put(i, j, value);
			}
		}*/

		List<Mat> bgr_planes = new ArrayList<Mat>();
		
		if ((colorChannel.equals("blue")) || (colorChannel.equals("green")) || (colorChannel.equals("red")))
		{
			Core.split( result, bgr_planes );
			if (colorChannel.equals("blue"))
			{
				result = bgr_planes.get(0);
			}
			else if (colorChannel.equals("green"))
			{
				result = bgr_planes.get(1);
			}
			else if (colorChannel.equals("red"))
			{
				result = bgr_planes.get(2);
			}
		}
		else if ((colorChannel.equals("hue")) || (colorChannel.equals("saturation")) || (colorChannel.equals("value")))
		{
			Mat hsv_image = new Mat();
			Imgproc.cvtColor(result, hsv_image, Imgproc.COLOR_BGR2HSV);
			Core.split( hsv_image, bgr_planes );
			
			if (colorChannel.equals("hue"))
			{
				result = bgr_planes.get(0);
			}
			else if (colorChannel.equals("saturation"))
			{
				result = bgr_planes.get(1);
			}
			else if (colorChannel.equals("value"))
			{
				result = bgr_planes.get(2);
			}
		}
		//Default to trusty green channel
		else
		{
			Core.split( result, bgr_planes );
			result = bgr_planes.get(1);
		}

		//Blur a little to get rid of noise
		Size s = new Size(3,3);
		Imgproc.GaussianBlur(result, result, s, 0, 0, Imgproc.BORDER_DEFAULT );
		
		//get average column intensity for empty area
		setEmptyArea();	
		
		if (threshold > 0)
		{
			calculatedThreshold = threshold;
		}
						
		//Process control and test lines
		for (int i=0;i<fields.length;i++)
		{	
			String resultString = "NOT PROCESSED";
			
			try 
			{
				JSONObject field = fields[i];
				
				if (field.has("type"))
				{
					if (field.getString("type").equals("control") || field.getString("type").equals("test"))
					{	
						int x1, y1, x2, y2;

						if (boxWidth > 0)
						{
							x1 = (int) ((int) field.getInt("x")-(0.5*boxWidth));
							x2 = (int) ((int) field.getInt("x")+(0.5*boxWidth));
						}
						else
						{
							x1 = (int) ((int) field.getInt("x")-(0.5*field.getInt("width")));
							x2 = (int) ((int) field.getInt("x")+(0.5*field.getInt("width")));
						}
						
						y1 = (int) ((int) field.getInt("y")-(0.4*field.getInt("height")));
						y2 = (int) ((int) field.getInt("y")+(0.4*field.getInt("height")));
						
						//Set ROI to just this field
						int dtop = y1;
						int dleft = x1;
						int dbottom = image.height() - y2;
						int dright = image.width() - x2;
						result.adjustROI(-dtop, -dbottom, -dleft, -dright);

						Mat littleImage = new Mat();
						result.copyTo(littleImage);
						result.adjustROI(dtop, dbottom, dleft, dright);
						
						//TEMP: Saving regions of interest
						//image.adjustROI(-dtop, -dbottom, -dleft, -dright);
						//String filename = "";
						//if (field.has("name"))
						//	filename = DiagnosticsUtils.getWorkingPhotoPath(photoName) + "_" + field.getString("name") + ".jpg";
						//else
						//	filename = DiagnosticsUtils.getWorkingPhotoPath(photoName) + "_" + field.getString("type") + ".jpg";

						//Highgui.imwrite(filename, image);
						//Log.i(tag, "Saved working image as " + filename);
						//image.adjustROI(dtop, dbottom, dleft, dright);
						
						//get the result for this field: true = line, false = no line
						boolean line = getResult(littleImage, percentPixels, numColumns);
						
						Point pt1 = new Point(x1,y1);
						Point pt2 = new Point(x2,y2);
						
						Scalar green = new Scalar(0,255,0);
						Scalar red = new Scalar(0,0,255);
						
						if (field.getString("type").equals("control"))
						{
							if (line)
							{								
								resultString = "VALID";	
								Core.rectangle(image, pt1, pt2, green, 5);
								Core.rectangle(result, pt1, pt2, green, 5);
							}
							else
							{
								resultString = "INVALID";
								Core.rectangle(image, pt1, pt2, red, 5);
								Core.rectangle(result, pt1, pt2, red, 5);
							}
						}
						else if (field.getString("type").equals("test"))
						{
							if (line)
							{
								resultString = "POSITIVE";
								Core.rectangle(image, pt1, pt2, green, 5);
								Core.rectangle(result, pt1, pt2, green, 5);
							}
							else
							{
								resultString = "NEGATIVE";
								Core.rectangle(image, pt1, pt2, red, 5);
								Core.rectangle(result, pt1, pt2, red, 5);
							}
						}
					
						//Save results
						field.put("result", resultString);						
						field.put("averageIntensityDifference", averageIntensityDifference);						
						field.put("averageLineIntensity", averageLineIntensity);						
						field.put("averageBackgroundIntensity", averageBackgroundIntensity);						
						processedFields.put(i, field);
					}
				}
			}
			catch (JSONException e) 
			{
				e.printStackTrace();
			}
		}
			
		//Save data
		try 
		{	
			description.put("fields", processedFields);
			description.put("processed", true);
			DiagnosticsUtils.writeFile(DiagnosticsUtils.getJsonPath(photoName), description.toString(2));
			Log.i(tag, "Saved JSON output as " + DiagnosticsUtils.getJsonPath(photoName));
			
			//Save working image
			/*String filename = DiagnosticsUtils.getWorkingPhotoPath(photoName) + "_" + colorChannel + ".jpg";
			Highgui.imwrite(filename, result);
			Log.i(tag, "Saved working image as " + filename);*/
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
		}
		
		return true;
	}

	private boolean getResult(Mat littleImage, double percentPixels, double numColumns) 
	{	
		
		//Resize empty space to match test image
		Mat resizedEmptySpace = new Mat();
		Mat subtractionResult = new Mat();
		Size dSize = new Size(littleImage.cols(), littleImage.rows());
		Imgproc.resize(emptySpace, resizedEmptySpace, dSize);
		
		//Subtract empty space from test image
		Core.subtract(resizedEmptySpace, littleImage, subtractionResult);
		
		int numColsPositive = 0;
		int numColsNegative = 0;
		int numColsConsecutive = 0;
		int lastPositiveIndex = subtractionResult.cols();;		
		int width = subtractionResult.cols();
		
		double intensitySum = 0;
		double subtractionSum = 0;
		double backgroundSum = 0;
		
		for (int i=0;i<width-1;i++)
		{
			subtractionResult.adjustROI(0, 0, -i, -(subtractionResult.cols()-i-1));
			littleImage.adjustROI(0, 0, -i, -(littleImage.cols()-i-1));
			resizedEmptySpace.adjustROI(0, 0, -i, -(resizedEmptySpace.cols()-i-1));
			
			int count = 0;
			for (int j=0;j<subtractionResult.rows();j++)
			{
				double value = subtractionResult.get(j,0)[0];				
				if (value > calculatedThreshold)
				{
					//Log.i("", "Positive pixel. Value = " + value + " Threshold is " + calculatedThreshold);
					count ++;
				}	
				else
				{
					//Log.i("", "Negative pixel. Value = " + value + " Threshold is " + calculatedThreshold);
				}
			}
			
			if (count > (percentPixels/100)*subtractionResult.rows())
			{
				numColsPositive++;
				subtractionSum += Core.mean(subtractionResult).val[0];
				intensitySum += Core.mean(littleImage).val[0];
				
				if ((i-lastPositiveIndex) == 1)
				{
					numColsConsecutive ++;
				}
				
				lastPositiveIndex = i;
			}
			else
			{
				numColsNegative ++;
				backgroundSum += Core.mean(littleImage).val[0];
			}

			//reset ROI
			subtractionResult.adjustROI(0, 0, i, subtractionResult.cols()-i-1);
			littleImage.adjustROI(0, 0, i, littleImage.cols()-i-1);
			resizedEmptySpace.adjustROI(0, 0, i, resizedEmptySpace.cols()-i-1);
		}
		
		//Log.i("Diagnostics", "NumColsPostive " + numColsPositive + " numColsConsecutive " + numColsConsecutive);
		
		if (numColsConsecutive >= numColumns)
		{
			Log.i("", "POSITIVE. numColsConsecutive = " + numColsConsecutive + ", numColumns required = " + numColumns);
			
			if (numColsPositive != 0)
			{
				averageIntensityDifference = subtractionSum/numColsPositive;
				averageLineIntensity = intensitySum/numColsPositive;
			
				if (numColsNegative == 0)
				{
					averageBackgroundIntensity = Core.mean(resizedEmptySpace).val[0];
				}
				else
				{
					averageBackgroundIntensity = backgroundSum/numColsNegative;
				}
			}
			Log.i("", "averageIntensityDifference = " + averageIntensityDifference + ", averageLineIntensity = " + averageLineIntensity + ", averageBackgroundIntensity = " + averageBackgroundIntensity);
			return true;
		}
		else
		{
			Log.i("", "NEGATIVE. numColsConsecutive = " + numColsConsecutive + ", numColumns required = " + numColumns);
			averageLineIntensity = 0;
			averageIntensityDifference = Core.mean(subtractionResult).val[0];
			averageBackgroundIntensity = Core.mean(littleImage).val[0];
			Log.i("", "averageIntensityDifference = " + averageIntensityDifference + ", averageLineIntensity = " + averageLineIntensity + ", averageBackgroundIntensity = " + averageBackgroundIntensity);
			return false;
		}
	}

	private void setEmptyArea() 
	{
		for (int i=0;i<fields.length;i++)
		{	
			try 
			{
				JSONObject field = fields[i];
				
				if (field.has("type"))
				{
					if (field.getString("type").equals("empty"))
					{
						//Get boundaries of this field
						int x1 = (int) ((int) field.getInt("x")-(0.5*field.getInt("width")));
						int y1 = (int) ((int) field.getInt("y")-(0.4*field.getInt("height")));
						int x2 = (int) ((int) field.getInt("x")+(0.5*field.getInt("width")));
						int y2 = (int) ((int) field.getInt("y")+(0.4*field.getInt("height")));
						
						//Set ROI to just this field
						int dtop = y1;
						int dleft = x1;
						int dbottom = result.height() - y2;
						int dright = result.width() - x2;
						
						//Set ROI to only empty region
						result.adjustROI(-dtop, -dbottom, -dleft, -dright);
						result.copyTo(emptySpace);
						//reset ROI to whole image
						result.adjustROI(dtop, dbottom, dleft, dright);
						
						//Calculate threshold
						/*if (savedThreshold > 0)
						{
							calculatedThreshold = (savedThreshold/1000) * Core.mean(emptySpace).val[0];
							Log.i("", "Saved threshold is " + savedThreshold + ". calculatedThreshold is: " + calculatedThreshold);
						}
						else 
						{
							calculatedThreshold = 0.001 * Core.mean(emptySpace).val[0];
							Log.i("", "NO Saved threshold. calculatedThreshold is: " + calculatedThreshold);
						}*/
					}
				}
			}
			catch (JSONException e) 
			{
				e.printStackTrace();
			}
		}
		//return calculatedThreshold;
	}
}