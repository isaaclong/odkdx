package org.opencv.pocdiagnostics;
         
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kxml2.io.KXmlParser;
import org.kxml2.io.KXmlSerializer;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.kxml2.kdom.Node;
import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class ExportToCollect extends Activity {
    
    private static final String COLLECT_FORMS_URI_STRING =
            "content://org.odk.collect.android.provider.odk.forms/forms";
    private static final Uri COLLECT_FORMS_CONTENT_URI =
            Uri.parse(COLLECT_FORMS_URI_STRING);
    private static final String COLLECT_INSTANCES_URI_STRING =
            "content://org.odk.collect.android.provider.odk.instances/instances";
    private static final Uri COLLECT_INSTANCES_CONTENT_URI =
            Uri.parse(COLLECT_INSTANCES_URI_STRING);
    private static final DateFormat COLLECT_INSTANCE_NAME_DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd_kk-mm-ss");

	private static final String LOG_TAG = "Diagnostics";

	private static String photoName;
	
    @Override
	protected void onCreate(Bundle savedInstanceState) 
    {	
    	super.onCreate(savedInstanceState);

		try {
			//Initialize the intent that will start collect and use it to see if collect is installed.
		    Intent intent = new Intent();
		    intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		    intent.setComponent(new ComponentName("org.odk.collect.android",
		            "org.odk.collect.android.activities.FormEntryActivity"));
		    PackageManager packMan = getPackageManager();
		    if(packMan.queryIntentActivities(intent, 0).size() == 0){
		    	throw new Exception("ODK Collect was not found on this device.");
		    }

	    	Bundle extras = getIntent().getExtras();
	    	if(extras == null){ throw new Exception("No parameters specified"); }
			photoName = extras.getString("photoName");
			
			String jsonPath = DiagnosticsUtils.getJsonPath(photoName);
			
			JSONObject description = DiagnosticsUtils.loadDescription(photoName);
			String testType = description.getString("type");
			String xFormPath = DiagnosticsUtils.getXFormPath(testType);
			
			if(jsonPath == null){ throw new Exception("jsonPath is null"); }
			if(xFormPath == null){ throw new Exception("xFormPath is null"); }

			Log.d("call to Collect: ", jsonPath);
			String templateName = new File(jsonPath).getName();
			
			//////////////
			Log.i(LOG_TAG, "Checking if there is no xform or the xform is out of date.");
			//////////////
			File xformFile = new File(xFormPath);
            // try not creating the file, since we already have one we want to work with

			if( !xformFile.exists() || 
				new File(jsonPath).lastModified() > xformFile.lastModified()){
				//////////////
				Log.i(LOG_TAG, "Unregistering any existing old versions of xform.");
				//////////////
			    String [] deleteArgs = { templateName };
		        int deleteResult = getContentResolver().delete(COLLECT_FORMS_CONTENT_URI, "jrFormId like ?", deleteArgs);
		        Log.w(LOG_TAG, "Removing " + deleteResult + " rows.");
				//////////////
				Log.i(LOG_TAG, "Creating new xform.");
				//////////////
			    
				buildXForm(jsonPath, xFormPath, testType, testType);
                Log.i(LOG_TAG, "Finished new xform create.");
			}

			
			Log.i(LOG_TAG, "Verifying form in Collect...");
			String jrFormId = verifyFormInCollect(xFormPath, templateName);
			//////////////
			Log.i(LOG_TAG, "Checking if the form instance is already registered with collect.");

			//////////////
			int instanceId;
		    String instanceName = photoName;
			Log.d("ExportToCollect",photoName);
		    String instancePath = "/sdcard/odk/instances/" + instanceName + "/";
            Log.d("ExportToCollect", instancePath);
		    (new File(instancePath)).mkdirs();
		    String instanceFilePath = instancePath + instanceName + ".xml";
			String selection = "instanceFilePath = ?";
	        String[] selectionArgs = { instanceFilePath };
	        Cursor c = getContentResolver().query(COLLECT_INSTANCES_CONTENT_URI, null, selection, selectionArgs, null);

	    	if(c.moveToFirst()){
	    		//////////////
				Log.i(LOG_TAG, "Registered odk instance found.");
				//////////////
	    		instanceId = c.getInt(c.getColumnIndex("_id"));
	    	}
	    	else{
				//////////////
				Log.i(LOG_TAG, "Registered odk instance not found, creating one...");
				//////////////
	    		jsonOut2XFormInstance(jsonPath, xFormPath, instancePath, instanceName);
	            ContentValues insertValues = new ContentValues();
	            insertValues.put("displayName", instanceName);
	            insertValues.put("instanceFilePath", instanceFilePath);
	            insertValues.put("jrFormId", jrFormId);
	            Uri insertResult = getContentResolver().insert(
	                    COLLECT_INSTANCES_CONTENT_URI, insertValues);
	            instanceId = Integer.valueOf(insertResult.getLastPathSegment());
	    	}
	    	c.close();
			Log.i(LOG_TAG, "instanceId: " + instanceId);
	        //////////////
	        Log.i(LOG_TAG, "Starting Collect...");
	        //////////////
		    intent.setAction(Intent.ACTION_EDIT);
		    intent.putExtra("start", true);
		    intent.setData(Uri.parse(COLLECT_INSTANCES_URI_STRING + "/" + instanceId));
		    startActivity(intent);
		    finish();

		} catch (Exception e) {
			//Display an error dialog if something goes wrong.
			Log.i(LOG_TAG, e.toString());
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(e.toString())
			       .setCancelable(false)
			       .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			                dialog.cancel();
			                finish();
			           }
			       });
			AlertDialog alert = builder.create();
			alert.show();
		}

	}
    
	/**
     * Verify that the form is in collect and put it in collect if it is not.
     * @param filepath
     * @return jrFormId
     */
	private String verifyFormInCollect(String filepath, String jrFormId) 
	{
        String[] projection = { "jrFormId" };
        String selection = "formFilePath = ?";
        String[] selectionArgs = { filepath };
        Cursor c = managedQuery(COLLECT_FORMS_CONTENT_URI, projection,
                selection, selectionArgs, null);
        if (c.getCount() != 0) {
            c.moveToFirst();
            String value = c.getString(c.getColumnIndex("jrFormId"));
            c.close();
            return value;
        }
		//////////////
		Log.i(LOG_TAG, "Registering the new xform with collect.");
		//////////////
        ContentValues insertValues = new ContentValues();
        insertValues.put("displayName", filepath);
        insertValues.put("jrFormId", jrFormId);
        insertValues.put("formFilePath", filepath);
        Log.d(LOG_TAG, "calling get content resolver...");
        getContentResolver().insert(COLLECT_FORMS_CONTENT_URI, insertValues);
        return jrFormId;
    }
    
    
	/**
	 * Generates an instance of an xform at xFormPath from the JSON output file
	 */
	private void jsonOut2XFormInstance(String jsonOutFile, String xFormPath, String instancePath, String instanceName)
			throws JSONException, IOException, XmlPullParserException 
	{

	    Log.i(LOG_TAG, "Reading the xform...");

	    Document formDoc = new Document();
	    KXmlParser formParser = new KXmlParser();
        formParser.setInput(new FileReader(xFormPath));
        formDoc.parse(formParser);

	    String namespace = formDoc.getRootElement().getNamespace();	    
        Element hhtmlEl = formDoc.getElement(namespace, "h:html");        
        Element hheadEl = hhtmlEl.getElement(namespace, "h:head");        
        Element modelEl = hheadEl.getElement(namespace, "model");        
        Element instanceEl = modelEl.getElement(namespace, "instance");        
        Element dataEl = instanceEl.getElement(namespace, "data");        
		String jrFormId = dataEl.getAttributeValue(namespace, "id");		

		Element instance = new Element();
        instance.setName(dataEl.getName());
        instance.setAttribute("", "id", jrFormId);
        instance.addChild(Node.ELEMENT, instance.createElement("", "xformstarttime"));
        instance.addChild(Node.ELEMENT, instance.createElement("", "xformendtime"));

        Log.i(LOG_TAG, "Parsing the JSON output:");

        
    	JSONObject description = DiagnosticsUtils.loadDescription(photoName);
    	JSONObject[] fields = DiagnosticsUtils.getFields(description, "fields");
        
		if(fields.length == 0){
			throw new JSONException("There are no fields in the json output file.");
		}

		Log.i(LOG_TAG, "Transfering the values from the JSON output into the xform instance:");

		for(int i = 0; i < fields.length; i++)
		{
			JSONObject field = fields[i];
			String datatype = field.getString("datatype");
			
			if (!datatype.equals("none"))
			{
				String fieldName = field.getString("name");
				Element fieldElement = instance.createElement("", fieldName);
				fieldElement.addChild(Node.TEXT, "" + field.optString("result"));
				instance.addChild(Node.ELEMENT, fieldElement);
			}
		}

		//Add location
		instance.addChild(Node.ELEMENT, instance.createElement("", "location"));
		
		//Add image
		String imageName = "image";
		Element fieldImageElement = instance.createElement("", imageName);
		String imagePath = DiagnosticsUtils.getMarkedupPhotoPath(photoName);
		fieldImageElement.addChild(Node.TEXT, new File(imagePath).getName());
		instance.addChild(Node.ELEMENT, fieldImageElement);

		//Copy original image
		InputStream fis = new FileInputStream(imagePath);
		FileOutputStream fos = new FileOutputStream(instancePath + new File(imagePath).getName());
		// Transfer bytes from in to out
		byte[] buf = new byte[1024];
		int len;
		while ((len = fis.read(buf)) > 0) {
			fos.write(buf, 0, len);
		}
		fos.close();
		fis.close();

		// TODO: add processedDataFile element with appropriate name of text file; similar to image copy above
		// add pData file Node to instance
		String pDataName = "processedDataFile";
		Element pDataEl = instance.createElement("", pDataName);
		String pDataPath = DiagnosticsUtils.getJsonPath(photoName);
		Log.i("pData Instance", pDataPath);
		pDataEl.addChild(Node.TEXT, new File(pDataPath).getName());
		instance.addChild(Node.ELEMENT, pDataEl);

		// copy pData file to directory, binary copy same as image above
		fis = new FileInputStream(pDataPath);
		fos = new FileOutputStream(instancePath + new File(pDataPath).getName());
		buf = new byte[1024];
		while((len = fis.read(buf)) > 0) {
			fos.write(buf, 0, len);
		}
		fos.close();
		fis.close();
        //////////////
        Log.i(LOG_TAG, "Outputting the instance file:");
        //////////////
	    String instanceFilePath = instancePath + instanceName + ".xml";
	    writeXMLToFile(instance, instanceFilePath);
	}
	 
	/**
	 * Write the given XML tree out to a file
	 * @param elementTree
	 * @param outputPath
	 * @throws IOException
	 */
    private void writeXMLToFile(Element elementTree, String outputPath) throws IOException {
	    File instanceFile = new File(outputPath);
	    instanceFile.createNewFile();
	    FileWriter instanceWriter = new FileWriter(instanceFile);
	    KXmlSerializer instanceSerializer = new KXmlSerializer();
        instanceSerializer.setOutput(instanceWriter);
        elementTree.write(instanceSerializer);
        instanceSerializer.endDocument();
        instanceSerializer.flush();
        instanceWriter.close();
	}
    
    /**
     * Builds an xfrom from a json template and writes it out to the specified file.
     * Note:
     * It is important to distinguish between an xform and an xform instance.
     * This builds an xform.
     * @param templatePath
     * @param outputPath
     * @param title
     * @param id
     * @throws IOException
     * @throws JSONException 
     */
    
    public static void buildXForm(String templatePath, String outputPath, String title, String id) throws IOException, JSONException 
    {
		Log.i(LOG_TAG, templatePath);
        Log.i(LOG_TAG, outputPath);
        Log.i(LOG_TAG, title);
        Log.i(LOG_TAG, id);
		
    	JSONObject description = DiagnosticsUtils.loadDescription(photoName);
    	JSONObject[] fields = DiagnosticsUtils.getFields(description, "fields");
		
		//Get the field names and labels:
		String [] fieldNames = new String[fields.length];
		String [] fieldLabels = new String[fields.length];
		
		for(int i = 0; i < fields.length; i++){
			JSONObject field = fields[i];
			if(field.has("name")){
				fieldNames[i] = field.getString("name");
				fieldLabels[i] = field.optString("label", field.getString("name"));
			}
			else{
				Log.i(LOG_TAG, "Field " + i + " has no name.");
				//throw new JSONException("Field " + i + " has no name or label.");
			}
		}

        Log.i(LOG_TAG, "Creating writer to output path...");
        FileWriter writer = new FileWriter(outputPath);
        writer.write("<h:html xmlns=\"http://www.w3.org/2002/xforms\" " +
                "xmlns:h=\"http://www.w3.org/1999/xhtml\" " +
                "xmlns:ev=\"http://www.w3.org/2001/xml-events\" " +
                "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
                "xmlns:jr=\"http://openrosa.org/javarosa\">\n");
        writer.write("<h:head>\n");
        writer.write("<h:title>" + title + "</h:title>\n");
        writer.write("<model>\n");
        writer.write("<instance>\n");
        writer.write("<data id=\"" + id + "\">\n");
        writer.write("<xformstarttime/>\n");
        writer.write("<xformendtime/>\n");
        
        for(int i = 0; i < fields.length; i++)
        {
        	String type = fields[i].optString("datatype", "string");
        	if (!type.equals("none"))
        	{
        		writer.write("<" + fieldNames[i] + "/>");
        	}
        }
        
        writer.write("<image/>");
        
        writer.write("<location/>\n");

		// adding in attribute for processedData txt file
		writer.write("<processedDataFile/>\n");

        writer.write("</data>\n");
        writer.write("</instance>\n");
        writer.write("<itext>\n");
        writer.write("<translation lang=\"eng\">\n");
        
        for(int i = 0; i < fields.length; i++)
        {
        	String type = fields[i].optString("datatype", "string");
        	if (!type.equals("none"))
        	{
        		writer.write("<text id=\"/data/" + fieldNames[i] + ":label\">\n");
        		writer.write("<value>" + fieldLabels[i] + "</value>\n");
        		writer.write("</text>\n");
        	}
        }
        
        writer.write("<text id=\"/data/location:label\">\n");
		writer.write("<value>location</value>\n");
		writer.write("</text>\n");
        
        writer.write("</translation>\n");
        writer.write("</itext>\n");
        writer.write("<bind nodeset=\"/data/xformstarttime\" type=\"dateTime\" jr:preload=\"timestamp\" jr:preloadParams=\"start\"/>\n");
        writer.write("<bind nodeset=\"/data/xformendtime\" type=\"dateTime\" jr:preload=\"timestamp\" jr:preloadParams=\"end\"/>\n");
        
        for(int i = 0; i < fields.length; i++)
        {
        	JSONObject field = fields[i];
        	String type = field.optString("datatype", "string");
        	if (!type.equals("none"))
        		writer.write("<bind nodeset=\"/data/" + fieldNames[i] + "\" type=\"" + type + "\"/>");
        }
        
        writer.write("<bind nodeset=\"/data/location\" type=\"geopoint\"/>");
        writer.write("<bind nodeset=\"/data/image\" appearance=\"web\" readonly=\"true()\" type=\"binary\"/>");

		// adding in bind for processedData txt file
		writer.write("<bind nodeset=\"/data/processedDataFile\" type=\"binary\"/>\n");

        writer.write("</model>");
        writer.write("</h:head>");
        writer.write("<h:body>");
        
        for(int i = 0; i < fields.length; i++)
        {
        	JSONObject field = fields[i];	
        	String type = field.optString("datatype", "string");
        	String tag = "none";
        	if( type.equals("select1") ){
        		tag = type;
        	}
        	else if (! type.equals("none")) {
        		tag = "input";
        	}
        	
        	if (!tag.equals("none"))
        	{
	        	writer.write("<group appearance=\"field-list\">");
	        	writer.write("<" + tag +" ref=\"/data/" + fieldNames[i] + "\">");
	        	writer.write("<label ref=\"jr:itext('/data/" + fieldNames[i] + ":label')\"/>");
	
	        	if(tag.equals("select1") )
	        	{
	        		String fieldID = field.getString("type");
	        		
	        		if (fieldID.equals("control"))
	        		{
	        			writer.write("<item>");
		                writer.write("<label> VALID </label>");
		                writer.write("<value> VALID </value>");
		                writer.write("</item>");
		                
	        			writer.write("<item>");
		                writer.write("<label> INVALID </label>");
		                writer.write("<value> INVALID </value>");
		                writer.write("</item>");
	        		}
	        		else if (fieldID.equals("test"))
	        		{
	        			writer.write("<item>");
		                writer.write("<label> POSITIVE </label>");
		                writer.write("<value> POSITIVE </value>");
		                writer.write("</item>");
		                
	        			writer.write("<item>");
		                writer.write("<label> NEGATIVE </label>");
		                writer.write("<value> NEGATIVE </value>");
		                writer.write("</item>");
	        		}
	        	}
	            writer.write("</" + tag + ">");

	            writer.write("<upload ref=\"/data/image\" mediatype=\"image/*\" />");

	            writer.write("</group>");
        	}
        }

        writer.write("<input ref=\"/data/location\">");
        writer.write("<label ref=\"jr:itext('/data/location:label')\"/>");
        writer.write("</input>");
        
        writer.write("</h:body>");
        writer.write("</h:html>");
        writer.close();
		
	}
	
}