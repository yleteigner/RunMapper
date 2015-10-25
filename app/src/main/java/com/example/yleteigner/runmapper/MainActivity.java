package com.example.yleteigner.runmapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import com.dsi.ant.plugins.antplus.common.AntFsCommon;
import com.dsi.ant.plugins.antplus.common.FitFileCommon;
import com.dsi.ant.plugins.antplus.common.FitFileCommon.FitFile;
import com.dsi.ant.plugins.antplus.pcc.AntPlusWatchDownloaderPcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusWatchDownloaderPcc.DeviceInfo;
import com.dsi.ant.plugins.antplus.pcc.defines.AntFsRequestStatus;
import com.dsi.ant.plugins.antplus.pcc.defines.AntFsState;
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState;
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;
import com.garmin.fit.ActivityMesg;
import com.garmin.fit.ActivityMesgListener;
import com.garmin.fit.Decode;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.FileIdMesgListener;
import com.garmin.fit.FitRuntimeException;
import com.garmin.fit.MesgBroadcaster;
import com.garmin.fit.SessionMesg;
import com.garmin.fit.SessionMesgListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MainActivity extends Activity {
    AntPlusWatchDownloaderPcc watchPcc = null;
    PccReleaseHandle<AntPlusWatchDownloaderPcc> releaseHandle = null;
    TextView tv_status;
    List<Map<String, String>> deviceList_Display;
    DeviceInfo[] deviceInfoArray;
    boolean bDevicesInList;
    SimpleAdapter adapter_deviceList_Display;
    ProgressDialog antFsProgressDialog;
    List<FitFile> fitFileList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv_status = (TextView) findViewById(R.id.tv_status);
        deviceList_Display = new ArrayList<Map<String, String>>();
        adapter_deviceList_Display = new SimpleAdapter(this, deviceList_Display,
                android.R.layout.simple_list_item_2, new String[]{"title", "desc"},
                new int[]{android.R.id.text1, android.R.id.text2});
        ListView listView_Devices = (ListView) findViewById(R.id.listView_deviceList);
        listView_Devices.setAdapter(adapter_deviceList_Display);

        //Set the list to download the data for the selected device and display it.
        listView_Devices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int pos, long id) {
                if (!bDevicesInList || watchPcc == null)
                    return;

                final CharSequence[] downloadOptions = {"All Activities", "New Activities", "Wait For New Activities"};
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Download...");
                builder.setItems(downloadOptions, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int downloadSelection) {
                        antFsProgressDialog = new ProgressDialog(MainActivity.this);
                        antFsProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                        antFsProgressDialog.setMessage("Sending Request...");
                        antFsProgressDialog.setCancelable(false);
                        antFsProgressDialog.setIndeterminate(false);

                        fitFileList = new ArrayList<FitFile>();

                        if (downloadSelection == 0) {// download all
                            if (watchPcc.requestDownloadAllActivities(
                                    deviceInfoArray[pos].getDeviceUUID(),
                                    new DownloadActivitiesFinished(pos),
                                    new FileDownloadedReceiver(),
                                    new AntFsUpdateReceiver())) {
                                antFsProgressDialog.show();
                            }
                        } else if (downloadSelection == 1) {// download new
                            if (watchPcc.requestDownloadNewActivities(
                                    deviceInfoArray[pos].getDeviceUUID(),
                                    new DownloadActivitiesFinished(pos),
                                    new FileDownloadedReceiver(),
                                    new AntFsUpdateReceiver())) {
                                antFsProgressDialog.show();
                            }
                        } else if (downloadSelection == 2) {  // Wait for new activities
                            boolean reqSubmitted = watchPcc.listenForNewActivities(
                                    deviceInfoArray[pos].getDeviceUUID(),
                                    new AntPlusWatchDownloaderPcc.IDownloadActivitiesFinishedReceiver() {
                                        @Override
                                        public void onNewDownloadActivitiesFinished(AntFsRequestStatus status) {
                                            //Only received on cancel right now, only thing to do is cancel dialog, already taken care of below
                                        }
                                    },
                                    new FileDownloadedReceiver() {
                                        @Override
                                        public void onNewFitFileDownloaded(FitFile downloadedFitFile) {
                                            super.onNewFitFileDownloaded(downloadedFitFile);

                                            //Now show each file as we get it
                                            List<FitFile> newActivityOnly = new ArrayList<FitFile>(fitFileList);
                                            fitFileList.clear();
                                            final Dialog_WatchData dataDialog = new Dialog_WatchData(deviceInfoArray[pos], newActivityOnly);
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    //dataDialog.show(getSupportFragmentManager(), "DeviceData");
                                                }
                                            });
                                        }
                                    });

                            //Once the listener is started we leave this dialog open until it is cancelled
                            //Note: Because the listener is an asynchronous process, you do not need to block the UI like the sample app does with this, you can leave it invisible to the user
                            if (reqSubmitted) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                                LayoutInflater inflater = MainActivity.this.getLayoutInflater();

                                // Inflate and set the layout for the dialog
                                // Pass null as the parent view because its going in the dialog layout
                                View detailsView = inflater.inflate(0, null);//R.layout.dialog_progresswaiter, null);
                                TextView textView_status = (TextView) detailsView.findViewById(0);//R.id.textView_Status);
                                textView_status.setText("Waiting for new activities on " + deviceInfoArray[pos].getDisplayName() + "...");
                                builder.setView(detailsView);
                                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        //Let onCancelListener take care of business
                                        dialog.cancel();
                                    }
                                });
                                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                                    @Override
                                    public void onCancel(DialogInterface dialog) {
                                        if (watchPcc != null) {
                                            watchPcc.cancelListenForNewActivities(deviceInfoArray[pos].getDeviceUUID());
                                        }
                                    }
                                });
                                AlertDialog waitDialog = builder.create();
                                waitDialog.show();
                            }
                        }
                    }
                });

                AlertDialog alert = builder.create();
                alert.show();
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void connectButtonClick(View v) {
        Toast.makeText(MainActivity.this, "Clicked!", Toast.LENGTH_LONG).show();
        detectWatch();
    }

    public void analyzeButtonClick(View v) {

    }

    public void LoadText(int resourceId) {
        // The InputStream opens the resourceId and sends it to the buffer
        InputStream is = this.getResources().openRawResource(resourceId);
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String readLine = null;

        try {
            // While the BufferedReader readLine is not null
            while ((readLine = br.readLine()) != null) {
                Log.d("TEXT", readLine);
            }

            // Close the InputStream and BufferedReader
            is.close();
            br.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void detectWatch() {
        //Release the old access if it exists
        if (releaseHandle != null) {
            releaseHandle.close();
        }

        releaseHandle = AntPlusWatchDownloaderPcc.requestDeviceListAccess(this,
                new AntPluginPcc.IPluginAccessResultReceiver<AntPlusWatchDownloaderPcc>() {
                    @Override
                    public void onResultReceived(AntPlusWatchDownloaderPcc result,
                                                 RequestAccessResult resultCode, DeviceState initialDeviceState) {
                        switch (resultCode) {
                            case SUCCESS:
                                watchPcc = result;
                                tv_status.setText(result.getDeviceName() + ": " + initialDeviceState);
                                watchPcc.requestCurrentDeviceList();
                                //subscribeToEvents();
                                break;
                            case CHANNEL_NOT_AVAILABLE:
                                Toast.makeText(MainActivity.this, "Channel Not Available", Toast.LENGTH_SHORT).show();
                                tv_status.setText("Error. Do Menu->Reset.");
                                break;
                            case ADAPTER_NOT_DETECTED:
                                Toast.makeText(MainActivity.this, "ANT Adapter Not Available. Built-in ANT hardware or external adapter required.", Toast.LENGTH_SHORT).show();
                                tv_status.setText("Error. Do Menu->Reset.");
                                break;
                            case BAD_PARAMS:
                                //Note: Since we compose all the params ourself, we should never see this result
                                Toast.makeText(MainActivity.this, "Bad request parameters.", Toast.LENGTH_SHORT).show();
                                tv_status.setText("Error. Do Menu->Reset.");
                                break;
                            case OTHER_FAILURE:
                                Toast.makeText(MainActivity.this, "RequestAccess failed. See logcat for details.", Toast.LENGTH_SHORT).show();
                                tv_status.setText("Error. Do Menu->Reset.");
                                break;
                            case DEPENDENCY_NOT_INSTALLED:
                                tv_status.setText("Error. Do Menu->Reset.");
                                AlertDialog.Builder adlgBldr = new AlertDialog.Builder(MainActivity.this);
                                adlgBldr.setTitle("Missing Dependency");
                                adlgBldr.setMessage("The required service\n\"" + AntPlusWatchDownloaderPcc.getMissingDependencyName() + "\"\n was not found. You need to install the ANT+ Plugins service or you may need to update your existing version if you already have it. Do you want to launch the Play Store to get it?");
                                adlgBldr.setCancelable(true);
                                adlgBldr.setPositiveButton("Go to Store", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent startStore = null;
                                        startStore = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + AntPlusWatchDownloaderPcc.getMissingDependencyPackageName()));
                                        startStore.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                                        MainActivity.this.startActivity(startStore);
                                    }
                                });
                                adlgBldr.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });

                                final AlertDialog waitDialog = adlgBldr.create();
                                waitDialog.show();
                                break;
                            case USER_CANCELLED:
                                tv_status.setText("Cancelled. Do Menu->Reset.");
                                break;
                            case UNRECOGNIZED:
                                Toast.makeText(MainActivity.this,
                                        "Failed: UNRECOGNIZED. PluginLib Upgrade Required?",
                                        Toast.LENGTH_SHORT).show();
                                tv_status.setText("Error. Do Menu->Reset.");
                                break;
                            default:
                                Toast.makeText(MainActivity.this, "Unrecognized result: " + resultCode, Toast.LENGTH_SHORT).show();
                                tv_status.setText("Error. Do Menu->Reset.");
                                break;
                        }
                    }
                },
                //Receives state changes and shows it on the status display line
                new AntPluginPcc.IDeviceStateChangeReceiver() {
                    @Override
                    public void onDeviceStateChange(final DeviceState newDeviceState) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tv_status.setText(watchPcc.getDeviceName() + ": " + newDeviceState);
                                if (newDeviceState == DeviceState.DEAD)
                                    watchPcc = null;
                            }
                        });


                    }
                },
                //Receives the device list updates and displays the current list
                new AntPlusWatchDownloaderPcc.IAvailableDeviceListReceiver() {
                    @Override
                    public void onNewAvailableDeviceList(AntPlusWatchDownloaderPcc.DeviceListUpdateCode listUpdateCode,
                                                         final DeviceInfo[] deviceInfos, DeviceInfo deviceChanging) {
                        switch (listUpdateCode) {
                            case NO_CHANGE:
                            case DEVICE_ADDED_TO_LIST:
                            case DEVICE_REMOVED_FROM_LIST:
                                // Note: This is for reference only and is not necessarily the optimal way to update.

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        deviceList_Display.clear();

                                        if (deviceInfos.length != 0) {
                                            deviceInfoArray = deviceInfos;
                                            bDevicesInList = true;
                                            for (int i = 0; i < deviceInfos.length; ++i) {
                                                HashMap<String, String> listItem = new HashMap<String, String>();
                                                listItem.put("title", deviceInfos[i].getDisplayName());
                                                listItem.put("desc", Integer.toString(deviceInfos[i].getAntfsDeviceType()));

                                                deviceList_Display.add(listItem);
                                            }
                                        } else {
                                            bDevicesInList = false;
                                            HashMap<String, String> listItem = new HashMap<String, String>();
                                            listItem.put("title", "No Devices Found");
                                            listItem.put("desc", "No watches detected in range yet...");
                                            deviceList_Display.add(listItem);
                                        }

                                        adapter_deviceList_Display.notifyDataSetChanged();
                                    }
                                });
                                break;

                            case UNRECOGNIZED:
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this,
                                                "Failed: UNRECOGNIZED. PluginLib Upgrade Required?",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                                break;
                            default:        // Unknown code received
                                return;
                        }
                    }
                });

    }

    public class DownloadActivitiesFinished implements AntPlusWatchDownloaderPcc.IDownloadActivitiesFinishedReceiver {
        private final int pos;

        /**
         * Constructor.
         *
         * @param pos Index in the device array
         */
        public DownloadActivitiesFinished(int pos) {
            this.pos = pos;
        }

        public void onNewDownloadActivitiesFinished(final AntFsRequestStatus status) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    antFsProgressDialog.dismiss();

                    switch (status) {
                        case SUCCESS:
                            final Dialog_WatchData dataDialog = new Dialog_WatchData(deviceInfoArray[pos], fitFileList);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    //dataDialog.show(getSupportFragmentManager(), "DeviceData");
                                }
                            });
                            return;
                        case FAIL_ALREADY_BUSY_EXTERNAL:
                            Toast.makeText(MainActivity.this, "Download failed, device busy.", Toast.LENGTH_SHORT).show();
                            break;
                        case FAIL_DEVICE_COMMUNICATION_FAILURE:
                            Toast.makeText(MainActivity.this, "Download failed, communication error.", Toast.LENGTH_SHORT).show();
                            break;
                        case FAIL_AUTHENTICATION_REJECTED:
                            //NOTE: This is thrown when authentication has failed, most likely when user action is required to enable pairing
                            Toast.makeText(MainActivity.this, "Download failed, authentication rejected.", Toast.LENGTH_LONG).show();
                            break;
                        case FAIL_DEVICE_TRANSMISSION_LOST:
                            Toast.makeText(MainActivity.this, "Download failed, transmission lost.", Toast.LENGTH_SHORT).show();
                            break;
                        case UNRECOGNIZED:
                            Toast.makeText(MainActivity.this,
                                    "Failed: UNRECOGNIZED. PluginLib Upgrade Required?",
                                    Toast.LENGTH_SHORT).show();
                            break;
                        default:
                            break;
                    }
                }
            });
        }
    }

    public class FileDownloadedReceiver implements FitFileCommon.IFitFileDownloadedReceiver {
        public void onNewFitFileDownloaded(FitFile downloadedFitFile) {
            Log.e("WatchDownloaderSampler", "Received FIT file of size " + downloadedFitFile.getRawBytes().length);
            InputStream fitFile = downloadedFitFile.getInputStream();

            if (!Decode.checkIntegrity(fitFile)) {
                Toast.makeText(MainActivity.this, "FIT file integrity check failed.", Toast.LENGTH_SHORT).show();
                return;
            }
            fitFileList.add(downloadedFitFile);
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            FileOutputStream outputStream;
            String filename = "fit_file_"+sdf.format(cal.getTime())+".fit";
            try {
                outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
                outputStream.write(downloadedFitFile.getRawBytes());
                outputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Handles displaying the ANTFS status and progress in the progress dialog.
     */
    public class AntFsUpdateReceiver implements AntFsCommon.IAntFsProgressUpdateReceiver {
        public void onNewAntFsProgressUpdate(final AntFsState state, final long transferredBytes,
                                             final long totalBytes) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switch (state) {
                        //In Link state and requesting to link with the device in order to pass to Auth state
                        case LINK_REQUESTING_LINK:
                            antFsProgressDialog.setMax(4);
                            antFsProgressDialog.setProgress(1);
                            antFsProgressDialog.setMessage("In Link State: Requesting Link.");
                            break;

                        //In Authentication state, processing authentication commands
                        case AUTHENTICATION:
                            antFsProgressDialog.setMax(4);
                            antFsProgressDialog.setProgress(2);
                            antFsProgressDialog.setMessage("In Authentication State.");
                            break;

                        //In Authentication state, currently attempting to pair with the device
                        //NOTE: Feedback SHOULD be given to the user here as pairing typically requires user interaction with the device
                        case AUTHENTICATION_REQUESTING_PAIRING:
                            antFsProgressDialog.setMax(4);
                            antFsProgressDialog.setProgress(2);
                            antFsProgressDialog.setMessage("In Authentication State: User Pairing Requested.");
                            break;

                        //In Transport state, no requests are currently being processed
                        case TRANSPORT_IDLE:
                            antFsProgressDialog.setMax(4);
                            antFsProgressDialog.setProgress(3);
                            antFsProgressDialog.setMessage("Requesting download (In Transport State: Idle)...");
                            break;

                        //In Transport state, files are currently being downloaded
                        case TRANSPORT_DOWNLOADING:
                            antFsProgressDialog.setMessage("In Transport State: Downloading.");
                            antFsProgressDialog.setMax(100);

                            if (transferredBytes >= 0 && totalBytes > 0) {
                                int progress = (int) (transferredBytes * 100 / totalBytes);
                                antFsProgressDialog.setProgress(progress);
                            }
                            break;
                        case UNRECOGNIZED:
                            Toast.makeText(MainActivity.this,
                                    "Failed: UNRECOGNIZED. PluginLib Upgrade Required?",
                                    Toast.LENGTH_SHORT).show();
                            break;
                        default:
                            Log.w("WatchDownloaderSampler", "Unknown ANT-FS State Code Received: " + state);
                            break;
                    }
                }
            });
        }
    }
}
