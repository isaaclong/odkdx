package org.opencv.pocdiagnostics;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.json.JSONObject;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.HybridBinarizer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.View;
  
public class RatiometricAligner 
{
	Scalar color = new Scalar(255,0,0);
	String tag = "RAT Aligner";
	Mat image = new Mat();
	Mat hsv_image = new Mat();
	Mat result = new Mat();
	String photoName;
	JSONObject description;
	int width, height = 100;
	
	public RatiometricAligner(){};
	
	public boolean alignCapturedImage(String photoName, String testType)
	{
		long startTime = 0;
		this.photoName = photoName;
		
		try 
		{
			description = DiagnosticsUtils.loadTemplate(testType);
			width = description.getInt("width");
			height = description.getInt("height");
			Log.i("LOADED", "Loaded size " + height + " x " + width);
		
		}
		catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		
		image = new Mat();
		hsv_image = new Mat();
		  
		//Load image
		image = Highgui.imread(DiagnosticsUtils.getPhotoPath(photoName));
		if (image.empty())
			Log.e(tag,"Image load failed");
		else
			Log.i(tag, "Started ratiometric aligning with " + DiagnosticsUtils.getPhotoPath(photoName));
		
		startTime = System.currentTimeMillis();
		
		//Do aligning here
		List<Point> corners = alignImage();
		
		//Debugging for alignment
		/*//Draw results of alignment on the image
		for (int i=0;i<corners.size();i++)
		{
			Core.circle(image, corners.get(i), 40, color, 5);
		}
		
		//Save output
		Highgui.imwrite(DiagnosticsUtils.getAlignedPhotoPath(photoName), image);
		Log.i(tag, "Saved aligned image as " + DiagnosticsUtils.getAlignedPhotoPath(photoName));
		*/
		
		//Find rectangle from the points
		Rect rect = locateRDTPixelsFromContours(corners);
		
		//Set final ROI to be only the RDT area
		int dtop = rect.y;
		int dleft = rect.x;
		int dbottom = image.height() - (rect.y+rect.height);
		int dright = image.width() - (rect.x+rect.width);
		image.adjustROI(-dtop, -dbottom, -dleft, -dright);
		
		//Resize to match JSON template
		try 
		{	  
			description.put("filename",photoName);
			description.put("type", testType);
			
			Mat temp = new Mat();
			//Size dsize = new Size(width, height);
			Size dsize = new Size(height, width);
			Imgproc.resize(image, temp, dsize, 0, 0, Imgproc.INTER_LINEAR);
			image = temp;

			JSONObject [] fields = DiagnosticsUtils.getFields(description, "fields");
			for (int i=0;i<fields.length;i++)
			{					
				JSONObject field = fields[i];
				if (field.has("type"))
				{
					//Process QR code if there is one
					if (field.get("type").equals("qrcode"))
					{
						Log.i("Aligner", "Found QR Code");
						int x1, y1, x2, y2;
						x1 = (int) ((int) field.getInt("x")-(0.5*field.getInt("width")));
						x2 = (int) ((int) field.getInt("x")+(0.5*field.getInt("width")));
						y1 = (int) ((int) field.getInt("y")-(0.5*field.getInt("height")));
						y2 = (int) ((int) field.getInt("y")+(0.5*field.getInt("height")));
						Point pt1 = new Point(x1,y1);
						Point pt2 = new Point(x2,y2);
						Core.rectangle(image, pt1, pt2, color, 5);
						Log.i("Aligner", "Drawn QR Code");
						
						//Set ROI to just this field
						dtop = y1;
						dleft = x1;
						dbottom = image.height() - y2;
						dright = image.width() - x2;
						image.adjustROI(-dtop, -dbottom, -dleft, -dright);
						Mat qrcode = new Mat();
						image.copyTo(qrcode);
						
						//reset ROI to whole image
						image.adjustROI(dtop, dbottom, dleft, dright);
						
						//Save QR code image snippet
						String filename = DiagnosticsUtils.getWorkingPhotoPath(photoName) + "_qrcode.jpg";
						Highgui.imwrite(filename, qrcode);
						
						//Read qrcode from image snippet
						try 
						{
							Result result = DiagnosticsUtils.readBarcode(filename);
							String text = result.getText();
					        Log.i("QRCODE", "Text is " + text);
					        
					        //TODO: Probably add data to json file from barcode here.
						}
						catch (Exception e)
						{
							e.printStackTrace();
						}
						
					}
					
					//Process color standard if there is one
					if (field.get("type").equals("standard"))
					{
						int x1, y1, x2, y2;
						x1 = (int) ((int) field.getInt("x")-(0.5*field.getInt("width")));
						x2 = (int) ((int) field.getInt("x")+(0.5*field.getInt("width")));
						y1 = (int) ((int) field.getInt("y")-(0.5*field.getInt("height")));
						y2 = (int) ((int) field.getInt("y")+(0.5*field.getInt("height")));
						Point pt1 = new Point(x1,y1);
						Point pt2 = new Point(x2,y2);
						Core.rectangle(image, pt1, pt2, color, 5);
						Log.i("STANDARD", "Drawn standard " + field.getString("name"));
					}
					
					//Draw boxes around control and test lines
					if ((field.get("type").equals("control")) || (field.get("type").equals("test")))
					{
						int x1, y1, x2, y2;
						x1 = (int) ((int) field.getInt("x")-(0.5*field.getInt("width")));
						x2 = (int) ((int) field.getInt("x")+(0.5*field.getInt("width")));
						y1 = (int) ((int) field.getInt("y")-(0.5*field.getInt("height")));
						y2 = (int) ((int) field.getInt("y")+(0.5*field.getInt("height")));
						Point pt1 = new Point(x1,y1);
						Point pt2 = new Point(x2,y2);
						Core.rectangle(image, pt1, pt2, color, 5);
						Log.i("BOXES", "Drawn " + field.getString("type"));
					}
				}
			}
			
			//Save data
			DiagnosticsUtils.writeFile(DiagnosticsUtils.getJsonPath(photoName), description.toString(2));
			Log.i(tag, "Saved JSON output as " + DiagnosticsUtils.getJsonPath(photoName));
			
			//Save output
			Highgui.imwrite(DiagnosticsUtils.getAlignedPhotoPath(photoName), image);
			Log.i(tag, "Saved aligned image as " + DiagnosticsUtils.getAlignedPhotoPath(photoName));
			
			
		}
		catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		
		long alignImageTime = System.currentTimeMillis() - startTime;
		Log.i(tag, "Time to align ratiometric image is " + alignImageTime);
		
		return true;
	}

	private List<Point> alignImage()
	{		
		//Downsample for processing efficiency
		Mat small_image = image;
		for (int i=0;i<2;i++)
		{
			Mat temp = new Mat();
			Size s = new Size(small_image.cols()/2, small_image.rows()/2 );
			Imgproc.pyrDown( small_image, temp, s);
			small_image = temp;
		}
		
		Log.i(tag, "Image size is " + small_image.rows() + " " + small_image.cols());
		
		//Normalize color intensities		
		for (int i=0;i<small_image.rows();i++)
		{
			for (int j=0;j<small_image.cols();j++)
			{
				double [] value = small_image.get(i, j);
				double sum = value[0] + value[1] + value[2];
				value[0] = (value[0]/sum)*255;
				value[1] = (value[1]/sum)*255;
				value[2] = (value[2]/sum)*255;
				
				small_image.put(i, j, value);
			}
		}
		
		Imgproc.cvtColor(small_image, hsv_image, Imgproc.COLOR_BGR2HSV);
		
		double meanS = Core.mean(hsv_image).val[1];
		double meanV = Core.mean(hsv_image).val[2];
				
		int centerX = hsv_image.cols()/2;
		int centerY = hsv_image.rows()/2;

		double outerRecRatio = (double) height/width;
		int outerRecWidth = (int) (hsv_image.cols() * 0.7);
		int outerRecHeight = (int) (outerRecWidth / outerRecRatio);

		int cornerX = centerX - (outerRecWidth/2);
		int cornerY = centerY - (outerRecHeight/2);

		int cornerX2 = centerX + (outerRecWidth/2);
		int cornerY2 = centerY + (outerRecHeight/2);

		Log.i(tag, "X = " + cornerX + " Y = " + cornerY + " X2 = " + cornerX2 + " Y2 = " + cornerY2);
		
		/*Point point = new Point(cornerX, cornerY);
		Point point2 = new Point(cornerX2,cornerY2);
		Core.rectangle(hsv_image, point, point2, color);
		
		Point tl1 = new Point (cornerX-30, cornerY-30);
		Point tl2 = new Point (cornerX+70, cornerY+70);
		Core.rectangle(hsv_image, tl1, tl2, color);
		
		Point tr1 = new Point (cornerX2-70, cornerY-30);
		Point tr2 = new Point (cornerX2+30, cornerY+70);
		Core.rectangle(hsv_image, tr1, tr2, color);		

		Point bl1 = new Point (cornerX-30, cornerY2-70);
		Point bl2 = new Point (cornerX+70, cornerY2+30);
		Core.rectangle(hsv_image, bl1, bl2, color);

		Point br1 = new Point (cornerX2-70, cornerY2-70);
		Point br2 = new Point (cornerX2+30, cornerY2+30);
		Core.rectangle(hsv_image, br1, br2, color);	*/
		
		int smaller = 10;
		int bigger = 20;
		
		//TOP LEFT
		Mat top_left = new Mat();
		hsv_image.copyTo(top_left);
		
		int dtop = cornerY-smaller;
		int dleft = cornerX-smaller;
		int dbottom = top_left.rows() - (cornerY+bigger);
		int dright = top_left.cols() - (cornerX+bigger);
		top_left.adjustROI(-dtop, -dbottom, -dleft, -dright);
		
		Point top_left_corner = getCenterPoint(top_left, meanS, meanV);
		top_left_corner.x = (top_left_corner.x+cornerX-smaller)*4;
		top_left_corner.y = (top_left_corner.y+cornerY-smaller)*4;
		
		//TOP RIGHT
		Mat top_right = new Mat();
		hsv_image.copyTo(top_right);
		
		dtop = cornerY-smaller;
		dleft = cornerX2-bigger;
		dbottom = top_right.rows() - (cornerY+bigger);
		dright = top_right.cols() - (cornerX2+smaller);
		top_right.adjustROI(-dtop, -dbottom, -dleft, -dright);
		
		Point top_right_corner = getCenterPoint(top_right, meanS, meanV);
		top_right_corner.x = (top_right_corner.x + cornerX2-bigger)*4;
		top_right_corner.y = (top_right_corner.y + cornerY-smaller)*4;	
		
		//BOTTOM LEFT
		Mat bottom_left = new Mat();
		hsv_image.copyTo(bottom_left);
		
		dtop = cornerY2-bigger;
		dleft = cornerX-smaller;
		dbottom = bottom_left.rows() - (cornerY2+smaller);
		dright = bottom_left.cols() - (cornerX+bigger);
		bottom_left.adjustROI(-dtop, -dbottom, -dleft, -dright);
		
		Point bottom_left_corner = getCenterPoint(bottom_left, meanS, meanV);
		bottom_left_corner.x = (bottom_left_corner.x+cornerX-smaller)*4;
		bottom_left_corner.y = (bottom_left_corner.y+cornerY2-bigger)*4;	
		
		//BOTTOM RIGHT
		Mat bottom_right = new Mat();
		hsv_image.copyTo(bottom_right);
		
		dtop = cornerY2-bigger;
		dleft = cornerX2-bigger;
		dbottom = bottom_right.rows() - (cornerY2+smaller);
		dright = bottom_right.cols() - (cornerX2+smaller);
		bottom_right.adjustROI(-dtop, -dbottom, -dleft, -dright);
		
		Point bottom_right_corner = getCenterPoint(bottom_right, meanS, meanV);
		bottom_right_corner.x = (bottom_right_corner.x+cornerX2-bigger)*4;
		bottom_right_corner.y = (bottom_right_corner.y+cornerY2-bigger)*4;
		 
		List<Point> ret = new ArrayList<Point>();
		ret.add(top_left_corner);
		ret.add(top_right_corner);
		ret.add(bottom_left_corner);
		ret.add(bottom_right_corner);
		return ret;
	}
	
	private Point getCenterPoint(Mat patch, double meanS, double meanV)
	{
		//Isolate channels
		List<Mat> hsv_planes = new ArrayList<Mat>();;
		Core.split( patch, hsv_planes );
		Mat sat = hsv_planes.get(1);
		Mat val = hsv_planes.get(2);
		
		Mat s = sat;
		Mat v = val;
		
		//Threshold on saturation and value channel
		Imgproc.threshold(sat, s, meanS+5, 255, Imgproc.THRESH_BINARY);
		Imgproc.threshold(val, v, meanV+5, 255, Imgproc.THRESH_BINARY);
		
		//Remove noise
		int erosion_size = 5;
		Size size = new Size(2*erosion_size + 1, 2*erosion_size+1);
		Point p = new Point(erosion_size, erosion_size);
		Mat element = Imgproc.getStructuringElement( 1, size, p);
		
		Imgproc.dilate( s, s, element );
		Imgproc.erode( s, s, element );
		Imgproc.dilate( v, v, element );
		Imgproc.erode( v, v, element );
		
		//bitwise and
		result = v;
		Core.bitwise_and(s, v, result);
		
		//Find contours
		List<Mat> contours = new ArrayList<Mat>();
		Mat hierarchy = new Mat();
		Imgproc.findContours(result, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
		
		//Find the contour with the biggest area
		double maxArea = 0;
    	int maxIndex = 0;
		for(int i = 0; i < contours.size(); i++ )
		{
			Mat c = contours.get(i);
			if (c.total() > maxArea)
			{
				maxArea = c.total();
				maxIndex = i;
			}
		}
		
		Point center = new Point();
		float[] radius = new float[1];
		
		if (contours.size()>0)
		{
			List<Point> pts = new ArrayList<Point>();
			Converters.Mat_to_vector_Point(contours.get(maxIndex), pts);
			Imgproc.minEnclosingCircle(pts, center, radius);
		}
		else
		{
			Log.i("Diagnostics", "Contour finding failed, defaulting to ");
			center = new Point(result.cols()/2, result.rows()/2);
		}
		
		return center;
	}
	
	private Rect locateRDTPixelsFromContours(List<Point> pts)
	{
		//Get rectangle around RDT
		RotatedRect rdt = Imgproc.minAreaRect(pts);
	
		//Transpose if necessary 
		if (rdt.size.width < rdt.size.height)
		{
			double temp = rdt.size.width;
			rdt.size.width = rdt.size.height;
			rdt.size.height = temp;
			rdt.angle = rdt.angle+90;
		}
		
		/*
		//Rotate RDT to be horizontal
		Scalar sc = new Scalar(0);
		Mat M = Imgproc.getRotationMatrix2D(rdt.center, rdt.angle, 1);
		Imgproc.warpAffine(image, result, M, image.size(), Imgproc.INTER_LINEAR, Imgproc.BORDER_CONSTANT, sc);
		image = result;*/
		
		//Save rectangle defining RDT
		int x = (int) ((int)rdt.center.x-(0.5*rdt.size.width));
		int y = (int) ((int)rdt.center.y-(0.5*rdt.size.height));
		int width = (int) rdt.size.width;
		int height = (int) rdt.size.height;
		
		return new Rect(x,y,width,height);
	}
}