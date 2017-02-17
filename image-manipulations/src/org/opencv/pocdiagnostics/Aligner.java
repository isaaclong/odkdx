package org.opencv.pocdiagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.opencv.core.Core;
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
import android.os.Environment;
import android.util.Log;
import android.view.View;

public class Aligner 
{
	Scalar color = new Scalar(0,0,255);
	String tag = "Aligner";
	Mat image = new Mat();
	Mat gray_image = new Mat();
	Mat clusterImage = new Mat();
	String photoName;
	
	//Locate RDT with edge detection vs. k-means
	boolean edgeDetect = false;
	
	public Aligner(){};
	
	public boolean alignCapturedImage(String photoName, String testType, double threshold, double percentPixels, double boxWidth, double numColumns)
	{
		this.photoName = photoName;
		image = new Mat();
		gray_image = new Mat();
		clusterImage = new Mat();
		  
		//Load image
		image = Highgui.imread(DiagnosticsUtils.getPhotoPath(photoName));
		if (image.empty())
			Log.e(tag,"Image load failed");
				
		Rect rect;
		if (edgeDetect)
		{
			rect = doEdgeDetect(photoName, testType);
		}
		else
		{
			rect = doKMeans(photoName, testType);
		}
		
		//Set final ROI to be only the RDT area
		int dtop = rect.y;
		int dleft = rect.x;
		int dbottom = image.height() - (rect.y+rect.height);
		int dright = image.width() - (rect.x+rect.width);
		image.adjustROI(-dtop, -dbottom, -dleft, -dright);
		
		//Resize to match JSON template
		try 
		{
			Mat temp = new Mat();
			JSONObject description = DiagnosticsUtils.loadTemplate(testType);
			
			description.put("filename", photoName);
			description.put("type", testType);
			description.put("threshold", threshold);
			description.put("percentPixels", percentPixels);
			description.put("boxWidth", boxWidth);
			description.put("numColumns", numColumns);

			int width = description.getInt("width");
			int height = description.getInt("height");
			Size dsize = new Size(width, height);
			Imgproc.resize(image, temp, dsize, 0, 0, Imgproc.INTER_LINEAR);
			image = temp;
						
			//Compress original captured image
			//ArrayList<Integer> params = new ArrayList<Integer>();
			//params.add(Highgui.CV_IMWRITE_JPEG_QUALITY);
			//params.add(60);	
			//Mat originalImage = Highgui.imread(DiagnosticsUtils.getPhotoPath(photoName));
			//Highgui.imwrite(DiagnosticsUtils.getPhotoPath(photoName), originalImage, params);

			
			Highgui.imwrite(DiagnosticsUtils.getAlignedPhotoPath(photoName), image);
			//Highgui.imwrite(DiagnosticsUtils.appFolder + DiagnosticsUtils.alignedPhotoDir + photoName + "_aligned.jpg", image, params);
			Highgui.imwrite(DiagnosticsUtils.appFolder + DiagnosticsUtils.alignedPhotoDir + photoName + "_aligned.jpg", image);
			
			Log.i(tag, "Saved image as " + DiagnosticsUtils.getAlignedPhotoPath(photoName));

			//Save data
			// hardcoding the file name, as we now attach this file to the form before it is uploaded to aggregate
			DiagnosticsUtils.writeFile(DiagnosticsUtils.getJsonPath(photoName), description.toString(2));
			Log.i(tag, "Saved JSON output as " + DiagnosticsUtils.getJsonPath(photoName));
		}
		catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	public Rect doEdgeDetect(String photoName, String testType)
	{		
		image.adjustROI(-200, -200, 0, 0);
		
		//Convert to grayscale
		Imgproc.cvtColor(image, gray_image, Imgproc.COLOR_BGR2GRAY);
		
		//Downsample for processing efficiency
		for (int i=0;i<4;i++)
		{
			Mat temp = new Mat();
			Size s = new Size(gray_image.cols()/2, gray_image.rows()/2 );
			Imgproc.pyrDown( gray_image, temp, s);
			gray_image = temp;
		}
		
		//Process the image
		List<Point> gradientPoints = doEdgeDetection();
		
		//Upscale to get back to original size
		for (int i=0;i<gradientPoints.size();i++)
		{
			gradientPoints.get(i).x = gradientPoints.get(i).x*16;
			gradientPoints.get(i).y = gradientPoints.get(i).y*16;
		}
		
		//Get corner points of RDT
		List<Point> results = locateRDTPixelsFromGradients(gradientPoints);
		
		return locateRDTPixelsFromContours(results);
	}

	public Rect doKMeans(String photoName, String testType)
	{		
		//Set ROI and convert to grayscale
		//image.adjustROI(-300, -200, -200, 0);
		
		Imgproc.cvtColor(image, gray_image, Imgproc.COLOR_BGR2GRAY);
		
		//Downsample for processing efficiency
		for (int i=0;i<4;i++)
		{
			Mat temp = new Mat();
			Size s = new Size(gray_image.cols()/2, gray_image.rows()/2 );
			Imgproc.pyrDown( gray_image, temp, s);
			gray_image = temp;
		}
		
		//Process the image
		int [] means = KMeans(2);
		List<Point> points = locateRDTcontour(means);
		
		//Upscale to get back to original size
		for (int i=0;i<points.size();i++)
		{
			points.get(i).x = points.get(i).x*16;
			points.get(i).y = points.get(i).y*16;
		}
		
		//Rotate and align RDT
		return locateRDTPixelsFromContours(points);

	}
	
	private int[] KMeans(int k)
	{
		//initialize means
		int [] means = new int[k];
		for (int i=0;i<k;i++)
		{
			means[i] = (i*50)+50;
		}
		
		Mat bins = new Mat();
		bins = Mat.zeros(gray_image.rows(), gray_image.cols(), CvType.CV_32F);
		
		int iterations = 0;
		while (true)
		{
			iterations ++;
			
			//Assign pixels to means
			for (int i=0;i<gray_image.rows();i++)
			{
				for (int j=0;j<gray_image.cols();j++)
				{
					//get pixel value
					double [] value = gray_image.get(i,j);
					double s = value[0];
					
					//find closest mean
					double distance = 300;
					int closestK = 0;
					for (int m=0;m<k;m++)
					{
						//Compute distance to mean
						double diff = s-(means[m]);
						double sum = diff*diff;
						double pixelValue = Math.sqrt(sum); 

						//Is it closer?
						if (pixelValue < distance)
						{
							//Save as closest mean to this pixel
							distance = pixelValue;
							closestK = m;
						}
					}

					//assign pixel to closest mean
					bins.put(i, j, closestK);
				}
			}
			
			int [] count = new int[k];
			int [] totals = new int[k];

			//Initialize variables
			for (int i=0;i<k;i++)
			{
				totals[i] = 0;
				count[i] = 0;
			}

			//Iterate through all pixels in the image
			for (int i=0;i<gray_image.rows();i++)
			{
				for (int j=0;j<gray_image.cols();j++)
				{
					//get value of assigned bin
					int bin = (int)bins.get(i,j)[0];
					
					//get value of pixel in image
					double s = gray_image.get(i,j)[0];

					//Update totals
					totals[bin] += s;
					count[bin] ++;
				}
			}

			//Update means
			int [] oldMeans = new int [k];

			for (int i=0;i<k;i++)
			{
				//Save old means
				oldMeans[i] = means[i];

				//Recalculate means
				if (count[i] > 0)
				{
					means[i] = (int)totals[i]/count[i];
				}
			}
			
			//Test to see if means have converged
			boolean allSame = true;
			for (int i=0;i<k;i++)
			{
				if (oldMeans[i] != means[i])
				{
					allSame = false;
				}
			}

			//All means are the same
			if (allSame)
				break;
		
			if(iterations > 20)
			{
				Log.i("Diagnostics", "KMeans did not converge");
				break;
			}
		}
		
		//Assign color of pixels to be their cluster
		for (int i=0;i<gray_image.rows();i++)
		{
			for (int j=0;j<gray_image.cols();j++)
			{
				int bin = (int)bins.get(i,j)[0];
				gray_image.put(i,j, bin*255);
			}
		}
		
		return means;
	}

	private List<Point> locateRDTcontour(int[] means)
	{
		int erosion_size = 5;
		Size s = new Size(2*erosion_size + 1, 2*erosion_size+1);
		Point p = new Point(erosion_size, erosion_size);
		Mat element = Imgproc.getStructuringElement( 1, s, p);
		Imgproc.dilate( gray_image, gray_image, element );
		Imgproc.erode( gray_image, gray_image, element );
		
		clusterImage = Mat.zeros(gray_image.rows(), gray_image.cols(), CvType.CV_8UC1);
		
		//For every pixel
		for (int i=0;i<gray_image.rows();i++)
		{
			for (int j=0;j<gray_image.cols();j++)
			{
				//If the pixel is it in this cluster make it white
				int v = (int)gray_image.get(i,j)[0];
				if (v == 255)
					clusterImage.put(i,j,255);
			}
		}
	
		//Filters to remove noise
		Imgproc.dilate( clusterImage, clusterImage, element );
		Imgproc.erode( clusterImage, clusterImage, element );
		
		//Find contours
		List<Mat> contours = new ArrayList<Mat>();
		Mat hierarchy = new Mat();
		Imgproc.findContours(clusterImage, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
		
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
		
		List<Point> pts = new ArrayList<Point>();
		Converters.Mat_to_vector_Point(contours.get(maxIndex), pts);
		
		return pts;
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
		
		//Rotate RDT to be horizontal
		Scalar sc = new Scalar(0);
		Mat M = Imgproc.getRotationMatrix2D(rdt.center, rdt.angle, 1);
		Imgproc.warpAffine(image, clusterImage, M, image.size(), Imgproc.INTER_LINEAR, Imgproc.BORDER_CONSTANT, sc);
		image = clusterImage;
		
		//Save rectangle defining RDT
		int x = (int) ((int)rdt.center.x-(0.5*rdt.size.width));
		int y = (int) ((int)rdt.center.y-(0.5*rdt.size.height));
		int width = (int) rdt.size.width;
		int height = (int) rdt.size.height;
		
		return new Rect(x,y,width,height);
	}

	private List<Point> doEdgeDetection()
	{
		Mat image = gray_image;		
		Point middlePoint = new Point((int)image.cols()/2, (int)image.rows()/2);
		
		double maxDiffPlus = 0, maxDiffMinus = 0;
		double diffPlus = 0, diffMinus = 0;
		
		Point maxYLocationPlus = new Point ((middlePoint.x)+20,0);
		Point maxYLocationMinus = new Point ((middlePoint.x)-20,0);
		
		//First half of Y coords
		for (int i=0; i< image.rows()/2;i++)
		{
			diffPlus = Math.abs(image.get(i, (int) (middlePoint.x)+20)[0]-image.get(i+1, (int) (middlePoint.x)+20)[0]);			
			if (diffPlus > maxDiffPlus)
			{
				maxDiffPlus = diffPlus;
				maxYLocationPlus.y = i;
			}
			
		}
		for (int i=0; i< image.rows()/2;i++)
		{
			diffMinus = Math.abs(image.get(i, (int) (middlePoint.x)-20)[0]-image.get(i+1, (int) (middlePoint.x)-20)[0]);			
			if (diffMinus > maxDiffMinus)
			{
				maxDiffMinus = diffMinus;
				maxYLocationMinus.y = i;
			}
			
		}

		//Second half of Y coords
		maxDiffPlus = 0; 
		maxDiffMinus = 0;
		
		Point maxYLocationPlus2 = new Point ((middlePoint.x)+20,0);
		Point maxYLocationMinus2 = new Point ((middlePoint.x)-20,0);
				
		for (int i=image.rows()-2; i>image.rows()/2;i--)
		{			
			diffPlus = Math.abs(image.get(i, (int) (middlePoint.x)+20)[0]-image.get(i+1, (int) (middlePoint.x)+20)[0]);
			if (diffPlus > maxDiffPlus)
			{
				maxDiffPlus = diffPlus;
				maxYLocationPlus2.y = i;
			}
		}
		for (int i=image.rows()-2; i>image.rows()/2;i--)
		{			
			diffPlus = Math.abs(image.get(i, (int) (middlePoint.x)+20)[0]-image.get(i+1, (int) (middlePoint.x)+20)[0]);				
			if (diffMinus > maxDiffMinus)
			{
				maxDiffMinus = diffMinus;
				maxYLocationMinus2.y = i;
			}
		}

		//First half of X coords
		maxDiffPlus = 0; 
		maxDiffMinus = 0;
		
		Point maxXLocationPlus = new Point (0, (middlePoint.y)+10);
		Point maxXLocationMinus = new Point (0, (middlePoint.y)-10);
		
		for (int i=0; i< image.cols()/2;i++)
		{
			diffPlus = Math.abs(image.get((int) (middlePoint.y)+10, i)[0]-image.get((int) (middlePoint.y)+10, i+1)[0]);
			if (diffPlus > maxDiffPlus)
			{
				maxDiffPlus = diffPlus;
				maxXLocationPlus.x = i;
			}
		}
		for (int i=0; i< image.cols()/2;i++)
		{
			diffMinus = Math.abs(image.get((int) (middlePoint.y)-10, i)[0]-image.get((int) (middlePoint.y)-10, i+1)[0]);
			if (diffMinus > maxDiffMinus)
			{
				maxDiffMinus = diffMinus;
				maxXLocationMinus.x = i;
			}
		}

		//Second half of X coords
		maxDiffPlus = 0; 
		maxDiffMinus = 0;
		
		Point maxXLocationPlus2 = new Point (0, (middlePoint.y)+10);
		Point maxXLocationMinus2 = new Point (0, (middlePoint.y)-10);
		
		for (int i=image.cols()-2; i> image.cols()/2;i--)
		{
			diffPlus = Math.abs(image.get((int) (middlePoint.y)+10, i)[0]-image.get((int) (middlePoint.y)+10, i+1)[0]);
			if (diffPlus > maxDiffPlus)
			{
				maxDiffPlus = diffPlus;
				maxXLocationPlus2.x = i;
			}
		}
		
		for (int i=image.cols()-2; i> image.cols()/2;i--)
		{
			diffMinus = Math.abs(image.get((int) (middlePoint.y)-10, i)[0]-image.get((int) (middlePoint.y)-10, i+1)[0]);
			if (diffMinus > maxDiffMinus)
			{
				maxDiffMinus = diffMinus;
				maxXLocationMinus2.x = i;
			}
		}
		
		List<Point> pts = new ArrayList<Point>();
		pts.add(maxXLocationPlus);
		pts.add(maxXLocationMinus);
		pts.add(maxXLocationPlus2);
		pts.add(maxXLocationMinus2);
		
		pts.add(maxYLocationPlus);
		pts.add(maxYLocationMinus);
		pts.add(maxYLocationPlus2);
		pts.add(maxYLocationMinus2);
		
		return pts;
	}

	private List<Point> locateRDTPixelsFromGradients(List<Point> gradientPoints) 
	{
		List<Point> pts = new ArrayList<Point>();
		
		double x1 = gradientPoints.get(0).x;
		double x2 = gradientPoints.get(1).x;
		double y1 = gradientPoints.get(0).y;
		double y2 = gradientPoints.get(1).y;		
		
		double x3 = gradientPoints.get(4).x;
		double x4 = gradientPoints.get(5).x;
		double y3 = gradientPoints.get(4).y;
		double y4 = gradientPoints.get(5).y;
		
		Point corner1 = intersection(x1, y1, x2, y2,  x3,  y3,  x4, y4);

		x1 = gradientPoints.get(0).x;
		x2 = gradientPoints.get(1).x;
		y1 = gradientPoints.get(0).y;
		y2 = gradientPoints.get(1).y;		
		
		x3 = gradientPoints.get(6).x;
		x4 = gradientPoints.get(7).x;
		y3 = gradientPoints.get(6).y;
		y4 = gradientPoints.get(7).y;
		
		Point corner2 = intersection(x1, y1, x2, y2,  x3,  y3,  x4, y4);

		x1 = gradientPoints.get(2).x;
		x2 = gradientPoints.get(3).x;
		y1 = gradientPoints.get(2).y;
		y2 = gradientPoints.get(3).y;		
		
		x3 = gradientPoints.get(6).x;
		x4 = gradientPoints.get(7).x;
		y3 = gradientPoints.get(6).y;
		y4 = gradientPoints.get(7).y;
		
		Point corner3 = intersection(x1, y1, x2, y2,  x3,  y3,  x4, y4);

		x1 = gradientPoints.get(2).x;
		x2 = gradientPoints.get(3).x;
		y1 = gradientPoints.get(2).y;
		y2 = gradientPoints.get(3).y;		
		
		x3 = gradientPoints.get(4).x;
		x4 = gradientPoints.get(5).x;
		y3 = gradientPoints.get(4).y;
		y4 = gradientPoints.get(5).y;
		
		Point corner4 = intersection(x1, y1, x2, y2,  x3,  y3,  x4, y4);
		
		pts.add(corner1);
		//pts.add(gradientPoints.get(1));
		//pts.add(gradientPoints.get(0));
		pts.add(corner4);
		//pts.add(gradientPoints.get(7));
		//pts.add(gradientPoints.get(6));
		pts.add(corner3);
		//pts.add(gradientPoints.get(2));
		//pts.add(gradientPoints.get(3));
		pts.add(corner2);
		//pts.add(gradientPoints.get(4));
		//pts.add(gradientPoints.get(4));
		
		return pts;
	}
	
	public Point intersection(double x1,double y1,double x2,double y2, double x3, double y3, double x4,double y4) 
	{
		double d = (x1-x2)*(y3-y4) - (y1-y2)*(x3-x4);
		if (d == 0) 
		{
			Log.i(tag, "NO INTERSECTION");
			return null;
		}

		double xi = ((x3-x4)*(x1*y2-y1*x2)-(x1-x2)*(x3*y4-y3*x4))/d;
		double yi = ((y3-y4)*(x1*y2-y1*x2)-(y1-y2)*(x3*y4-y3*x4))/d;
		  
		return new Point(xi,yi);
	}
}
