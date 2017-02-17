package org.opencv.pocdiagnostics;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;

/**
 * Created by isaaclong on 3/30/16.
 *
 * We use the Silicon Labs Sensor Puck hardware to capture environmental data at the time of
 * the test. More information about the sensor puck can be found here:
 * https://www.silabs.com/products/sensors/Pages/environmental-biometric-sensor-puck.aspx
 *
 * This activity is modeled after an existing activity within the Sensor Puck Cloud android
 * application. We received the source code and documentation for this app from Silicon Labs as
 * a reference, as we needed to use the Puck data independent of their show-case app.
 */
public class ImportPuckData extends Activity {
    /* Sensor Data types */
    public static final int SD_MODE = 0;
    public static final int SD_SEQUENCE = 1;
    public static final int SD_HUMIDITY = 2;
    public static final int SD_TEMPERATURE = 3;
    public static final int SD_AMB_LIGHT = 4;
    public static final int SD_UV_LIGHT = 5;
    public static final int SD_BATTERY = 6;
    public static final int SD_HRM_STATE = 16;
    public static final int SD_HRM_RATE = 17;
    public static final int SD_HRM_SAMPLE = 18;

    /* Measurement Mode */
    public static final int ENVIRONMENTAL_MODE = 0;
    public static final int BIOMETRIC_MODE = 1;
    public static final int PENDING_MODE = 8;
    public static final int NOT_FOUND_MODE = 9;

    /* Heart Rate Monitor state */
    public static final int HRM_STATE_IDLE = 0;
    public static final int HRM_STATE_NOSIGNAL = 1;
    public static final int HRM_STATE_ACQUIRING = 2;
    public static final int HRM_STATE_ACTIVE = 3;
    public static final int HRM_STATE_INVALID = 4;
    public static final int HRM_STATE_ERROR = 5;

    public static final int HRM_SAMPLE_COUNT = 5;
    public static final int MAX_PUCK_COUNT = 16;
    public static final int MAX_IDLE_COUNT = 3;

    public static final int GRAPH_RANGE_SIZE = 40000;
    public static final int GRAPH_DOMAIN_SIZE = 50;

    private BluetoothAdapter Adapter;
    private Handler SensorHandler = new Handler(new onSensorMessage());


    /* Advertisement structure */
    private class Advertisement {
        String Address;
        byte[] Data;
    }

    /* Puck data structure */
    private class Puck {
        /* Identification */
        String Address;
        String Name;

        /* Sensor data */
        int MeasurementMode;
        int Sequence;
        float Humidity = 0;
        float Temperature = 0;
        int AmbientLight = 0;
        int UV_Index;
        float Battery;
        int HRM_State;
        int HRM_Rate;
        int[] HRM_Sample;
        int HRM_PrevSample;

        /* Statistics */
        int PrevSequence;
        int RecvCount;
        int PrevCount;
        int UniqueCount;
        int LostAdv;
        int LostCount;
        int IdleCount;
    }

    Puck myPuck = new Puck();

    /* UI Elements */
    Button scanButton;
    TextView puckIDView;
    TextView scanTime;
    CountDownTimer cdt;
    boolean bluetoothOn = false;

    /* Debug */
    String logTag = "PuckImport";
    String inputPuckID = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* Set up UI */
        setContentView(R.layout.import_puck_data);
        scanButton = (Button) findViewById(R.id.scanButton);
        puckIDView = (TextView) findViewById(R.id.puckId);
        scanTime = (TextView) findViewById(R.id.scanTime);

        Bundle odkFormParams = getIntent().getExtras();
        /* This key should be the same as given in the odk form in the intent */
        inputPuckID = odkFormParams.getString("puckID");
        if (inputPuckID == null) inputPuckID = "none"; // if for some reason they open bluetooth in earlier app session
        puckIDView.append(inputPuckID);
        Log.d(logTag, "Puck ID: " + myPuck.Name);
        scanButton.setVisibility(View.INVISIBLE);

        /* Hook up button listener */
        // TODO: this will break when more than one sensor comes into play. Need to add a check for the name before assigning myPuck so we
        // never assign the bad puck
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: scan for the specific puckID via bluetooth and get a reading
                // set up countdown timer and finish after 10 seconds

                if(!bluetoothOn) {
                     /* Init Bluetooth */
                    BluetoothManager Manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                    Adapter = BluetoothAdapter.getDefaultAdapter();
                    if (Adapter == null || !Adapter.isEnabled()) {
                    /* Ask the user to turn on Bluetooth */
                        Intent EnableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(EnableIntent, 1);
                    }

                    bluetoothOn = true;
                    scanButton.setText("Stop Scanning");
                    Adapter.startLeScan(ScanCallback);
                    // TODO: start the countdown here
                }
                else {
                    Adapter.stopLeScan(ScanCallback);
                    bluetoothOn = false;
                    scanButton.setText("Start Scanning");
                    //TODO: stop the countdown here
                    if(myPuck.RecvCount > 0) {
                    /* After scanning is complete and we have at least 1 enviro response
                        process the response name to make sure it matches one they supplied
                     */

                        Log.d(logTag, "Address: " + myPuck.Address);
                        String splitAddress[] = myPuck.Address.split(":");
                        for(String a: splitAddress) {
                            Log.d(logTag, "Parsed Address" + a);
                        }

                        String parsedAddress = splitAddress[4] + splitAddress[5]; // are the bluetooth address always same length?
                        if(parsedAddress.equals(inputPuckID) || true) { // for now, we just accept that the first puck they find is correct
                            //Toast.makeText(getApplication(), "Sending data from puck...",
                                    //Toast.LENGTH_LONG).show();
                            Log.d(logTag, "Data - " + myPuck.Temperature);
                            Log.d(logTag, "Data - " + myPuck.Humidity);
                            Log.d(logTag, "Data - " + myPuck.AmbientLight);

                            DiagnosticsUtils.temperature = myPuck.Temperature;
                            DiagnosticsUtils.humidity = myPuck.Humidity;
                            DiagnosticsUtils.light = myPuck.AmbientLight;

                            DiagnosticsUtils.exit = true;
                            finish();
                        }
                        else
                        {
                            Toast.makeText(getApplication(), "You found a puck, but it was not the one you were looking for :(. Verify puckID",
                                    Toast.LENGTH_LONG).show();
                        }
                        bluetoothOn = false;
                        scanButton.setText("Start Scanning");
                    }
                    else {
                        Toast.makeText(getApplication(), "No pucks were found.",
                                Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
        scanButton.performClick();
        cdt = new CountDownTimer(20000, 1000) {
            public void onTick(long msUntilFinished) {
                // could insert some visual cue here
                scanTime.setText("Scan time remaining: " + msUntilFinished / 1000);
            }
            public void onFinish() {
                DiagnosticsUtils.exit = true;
                finish();
            }
        }.start();
    }

    /* This is called when a BLE advertisement is received from any BLE device */
    private BluetoothAdapter.LeScanCallback ScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            if(myPuck.RecvCount > 0) { //TODO: change this to reflect a count of 10-15 seconds since the start time
                scanButton.performClick();
                Log.i(logTag,"performing click to stop scanning...");
                return;
            }
         /* Create an advertisement object */
            Advertisement Adv = new Advertisement();
            Adv.Address = device.getAddress();
            Adv.Data = scanRecord;

         /* Send the advertisement to the sensor handler */
            Message Msg = Message.obtain();
            Msg.obj = Adv;
            SensorHandler.sendMessage(Msg);
        }
    };

    /* This is called whenever another part of the app calls SensorHandler.sendEmptyMessage(). */
   /* This routine handles the empty message on the UI thread. */
   /* This routine is the overall fragment manager. */
    private class onSensorMessage implements Handler.Callback {
        @Override
        public boolean handleMessage(Message Msg) {
         /* If the message is not empty */
            if (Msg.obj != null) {
            /* Get the advertisement from the message */
                Advertisement Adv = (Advertisement) Msg.obj;

            /* Process the advertising data */
                for (int x = 0; x < Adv.Data.length && Adv.Data[x] != 0; x += Adv.Data[x] + 1)
                    onAdvertisingData(Adv.Address, Adv.Data[x + 1], Arrays.copyOfRange(Adv.Data, x + 2, x + Adv.Data[x] + 1));

                return true;
            }


            //MainActivity Main = MainActivity.this;

         /* If switching fragments is in progress, then just exit */
            //if (Main.CurrentMode == PENDING_MODE)
            //return true;

         /* If the measurement mode has changed */
            //if ((Main.SelectedPuck == null) ||
            // (Main.CurrentMode != Main.SelectedPuck.MeasurementMode)) {
            //FragmentTransaction Transaction = getFragmentManager().beginTransaction();

            /* Switch to the appropriate fragment */
            /*
                if (Main.SelectedPuck == null) {
                    Transaction.replace(R.id.container, NotFragment);
                } else if (Main.SelectedPuck.MeasurementMode == ENVIRONMENTAL_MODE) {
                    Transaction.replace(R.id.container, EnvFragment);
                } else if (Main.SelectedPuck.MeasurementMode == BIOMETRIC_MODE) {
                    Transaction.replace(R.id.container, BioFragment);
                }

                Transaction.commit();
            */
            /* Switching fragments is in progress */
            // Main.CurrentMode = PENDING_MODE;
            //}

         /* If the first puck has been found */
            //if (Main.SelectedPuck != null && !Main.PuckName.isEnabled()) {
            /* Enable the PuckName text field and display the name of the first puck */
            /*
                Main.PuckName.setEnabled(true);
                Main.PuckName.setVisibility(View.VISIBLE);
                Main.EditButton.setEnabled(true);
                Main.EditButton.setVisibility(View.VISIBLE);
                Main.Spacer.setVisibility(View.INVISIBLE);
            */

            //}
            //}
            return true;
        }
    }

    /* Process advertising data */
    void onAdvertisingData( String Address, byte ADType, byte ADData[] )
    {
      /* If the advertisement contains Silabs manufacturer specific data */
        if ( (ADType==(-1)) && ((ADData[0]==0x34)||(ADData[0]==0x35)) && (ADData[1]==0x12) )
        {
         /* Find the puck with this Bluetooth address */
            //Puck ThisPuck = FindPuck( Address );
            // we simply construct a puck after the first successful advertisement we see
            Puck ThisPuck = new Puck();

         /* If its an old style advertisement */

            // We are not expecting any old advertisements
            //if ( ADData[0] == 0x34 )
            //{
            /* Process the sensor data */
                //for ( int x=2; x<ADData.length; x+=ADData[x]+1 )
                    //onSensorData( ThisPuck, ADData[x+1], Arrays.copyOfRange(ADData,x+2,x+ADData[x]+1) );
            //}
         /* If its a new style advertisement */
            if ( ADData[0] == 0x35 )
            {
            /* If its an environmental advertisement then process it */
                if ( ADData[2] == ENVIRONMENTAL_MODE ) {
                    cdt.cancel();
                    scanTime.setText("Found an environmental reading! Processing...");
                    Log.i("Bluetooth", "Found environmental advertisement!");
                    //Toast.makeText(getApplication(), "You found a puck advertising environmental data!",
                            //Toast.LENGTH_LONG).show();
                    onEnvironmentalData( myPuck, Arrays.copyOfRange(ADData,3,14) );
                    myPuck.Address = Address;
                }

            /* If its a biometric advertisement then process it */
                if ( ADData[2] == BIOMETRIC_MODE )
                    onBiometricData( myPuck, Arrays.copyOfRange(ADData,3,18) );
            }

         /* Another adverstisement has been received */
            myPuck.RecvCount++;

         /* Ignore duplicate advertisements */
            if ( myPuck.Sequence != myPuck.PrevSequence )
            {
            /* Another unique adverstisement has been received */
                myPuck.UniqueCount++;

            /* Calculate the number of lost advertisements */
                if ( myPuck.Sequence > myPuck.PrevSequence )
                    myPuck.LostAdv = myPuck.Sequence - myPuck.PrevSequence - 1;
                else /* Wrap around */
                    myPuck.LostAdv = myPuck.Sequence - myPuck.PrevSequence + 255;

            /* Big losses means just found a new puck */
                if ( (myPuck.LostAdv == 1) || (myPuck.LostAdv == 2) )
                    myPuck.LostCount += myPuck.LostAdv;

                myPuck.PrevSequence = myPuck.Sequence;

            /* Display new sensor data for the selected puck */
                //if ( ThisPuck == SelectedPuck )
                    //SensorHandler.sendEmptyMessage( 0 );
            }
        }
    }

    /* Process environmental data (new style advertisement) */
    void onEnvironmentalData( Puck ThisPuck, byte Data[] )
    {
        ThisPuck.MeasurementMode = ENVIRONMENTAL_MODE;
        ThisPuck.Sequence        = Int8(Data[0]);
        ThisPuck.Sequence        = Int8(Data[0]);
        ThisPuck.Humidity        = ((float)Int16(Data[3],Data[4]))/10;
        ThisPuck.Temperature     = ((float)Int16(Data[5],Data[6]))/10;
        ThisPuck.AmbientLight    = Int16( Data[7], Data[8] )*2;
        ThisPuck.UV_Index        = Int8( Data[9] );
        ThisPuck.Battery         = ((float)Int8(Data[10]))/10;
    }

    /* Process biometric data (new style advertisement) */
    void onBiometricData( Puck ThisPuck, byte Data[] )
    {
        ThisPuck.MeasurementMode = BIOMETRIC_MODE;
        ThisPuck.Sequence        = Int8( Data[0] );
        ThisPuck.HRM_State       = Int8( Data[3] );
        ThisPuck.HRM_Rate        = Int8( Data[4] );

        ThisPuck.HRM_PrevSample = ThisPuck.HRM_Sample[HRM_SAMPLE_COUNT-1];
        for ( int x=0; x<HRM_SAMPLE_COUNT; x++ )
            ThisPuck.HRM_Sample[x] = Int16( Data[5+(x*2)], Data[6+(x*2)] );
    }

    /* Convert byte to int */
    int Int8( byte Data )
    {
        return (int)(((char)Data)&0xFF);
    }

    /* Convert two bytes to int */
    int Int16( byte LSB, byte MSB )
    {
        return Int8(LSB) + (Int8(MSB)*256);
    }


}