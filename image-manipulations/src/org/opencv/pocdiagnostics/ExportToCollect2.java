package org.opencv.pocdiagnostics;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * Created by isaaclong on 3/24/16.
 */
public class ExportToCollect2 extends Activity {

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
            ODK Collect is what calls this ODK_Diagnostics app, so we no longer have to check
            if it is installed. We know there is no form instance yet, as user is still in the middle
            of filling out the form, but there will be saved form state within .../odk/.cache/ and this
            is what our own created instance will initialize with.
        */

        // The testType indicated in testDescription input file must be the same name as the form
        // name being used
        CharSequence formName = "SD_HIV";//DiagnosticsUtils.getTestType();
        String formCachePath = "/sdcard/odk/.cache/";
        Log.d("Files", "Path: " + formCachePath);
        File f = new File(formCachePath);
        File cache_files[] = f.listFiles();
        Log.d("Files", "Num cache files: " + cache_files.length);
        String cache_form_name = "1";
        for(int i = 0; i < cache_files.length; i++) {
            String cache_form = cache_files[i].getName();
            Log.d("Files", "Filename: " + cache_files[i].getName());
            if(cache_form.contains(formName)) {
                Log.i("Files","found the cached form!");
                cache_form_name = cache_form;
            }
        }
        Log.d("Files", "cache form found: " + cache_form_name);
    }
}
