package tw.edu.nctu.pcslab.socketctl;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class ControllerActivity extends AppCompatActivity {

    private final String TAG = "ControllerActivity";

    /* device list for ui */
    private ArrayList<String> deviceList;
    private ArrayAdapter<String> deviceListAdapter;
    private String currentDevice;

    /* socket list for ui */
    private ArrayList<String> socketList;
    private ArrayAdapter<String> socketListAdapter;

    /* device and socket list relation */
    private LinkedHashMap<String, ArrayList<String>> deviceLinkedHashMap;

    /* appliance list */
    private List<String> applianceList;
    private ArrayAdapter<String> applianceListAdapter;

    /*Mqtt client*/
    MqttAndroidClient mqttClient;
    private String mqttUri = "tcp://192.168.11.103:1883";
    private String clientId = "MorSocketAndroidClient";
    // subscribe
    private String deviceInfoTopic = "DeviceInfo";
    private String devicesInfoTopic = "DevicesInfo";
    // publish
    private String syncDeviceInfoTopic = "SyncDeviceInfo";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);

        currentDevice = null;
        deviceLinkedHashMap = new LinkedHashMap<String, ArrayList<String>>();
        //UI
        Spinner deviceListSpinner = (Spinner) findViewById(R.id.device_list_spinner);
        deviceList = new ArrayList<String>();
        deviceList.add(0, getString(R.string.select_morsocket_placeholder));
        deviceListAdapter = new ArrayAdapter<String>(getBaseContext(), R.layout.device_row_view, R.id.device_row_text_view, deviceList);
        deviceListSpinner.setAdapter(deviceListAdapter);

        ListView socketListView = (ListView) findViewById(R.id.socket_list_view);
        socketList = new ArrayList<String>();
        socketListAdapter = new ArrayAdapter<String>(getBaseContext(), R.layout.socket_row_view, R.id.socket_row_text_view, socketList);
        socketListView.setAdapter(socketListAdapter);

        /*Spinner appliancesListSpinner = (Spinner) findViewById(R.id.appliance_list_spinner);
        applianceList = Arrays.asList(getResources().getStringArray(R.array.appliances));
        applianceListAdapter = new ArrayAdapter<String>(getBaseContext(), R.layout.appliance_row_view, R.id.appliance_row_text_view, applianceList);
        appliancesListSpinner.setAdapter(applianceListAdapter);*/

        // click listener for deviceListSpinner
        deviceListSpinner.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                Object item = adapterView.getItemAtPosition(position);
                if (!(item.toString() == getString(R.string.select_morsocket_placeholder))) {
                    currentDevice = item.toString();
                    ArrayList<String> sl = new ArrayList<String>(deviceLinkedHashMap.get(currentDevice));
                    socketList.clear();
                    for(int i = 0; i < sl.size(); i++)
                        socketList.add(sl.get(i));
                    Log.d(TAG, socketList.toString());
                    socketListAdapter.notifyDataSetChanged();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        //mqtt
        mqttClient = new MqttAndroidClient(getApplicationContext(), mqttUri, clientId);
        mqttClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {

                if (reconnect) {
                    //addToHistory("Reconnected to : " + serverURI);
                    Log.d(TAG, "Reconnected to : " + serverURI);
                    // Because Clean Session is true, we need to re-subscribe
                    subscribeToTopic(deviceInfoTopic);
                    subscribeToTopic(devicesInfoTopic);
                    publishMessage(syncDeviceInfoTopic);
                } else {
                    //addToHistory("Connected to: " + serverURI);
                    Log.d(TAG, "Connected to : " + serverURI);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.d(TAG, cause.toString());
                Log.d(TAG, "The Connection was lost.");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Log.d(TAG, "Incoming message: " + new String(message.getPayload()));

            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }

        });
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);
        try {
            //addToHistory("Connecting to " + serverUri);
            mqttClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttClient.setBufferOpts(disconnectedBufferOptions);
                    subscribeToTopic(deviceInfoTopic);
                    subscribeToTopic(devicesInfoTopic);
                    publishMessage(syncDeviceInfoTopic);

                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    //addToHistory("Failed to connect to: " + serverUri);
                    Log.d(TAG, exception.toString());
                    Log.d(TAG, "Failed to connect to: " + mqttUri);

                }
            });
        } catch (MqttException ex){
            ex.printStackTrace();
        }

    }
    public void subscribeToTopic(String subscriptionTopic){
        try {
            mqttClient.subscribe(subscriptionTopic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "subscribe success");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d(TAG, "subscribe failed");
                }
            });
            mqttClient.subscribe(subscriptionTopic, 0, new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    Log.d(TAG, "Message: " + topic + " : " + new String(message.getPayload()));
                    if(topic.equals(deviceInfoTopic)) {
                        parseDeviceInfo(message);
                    }
                    else if(topic.equals(devicesInfoTopic)) {
                        parseDevicesInfo(message);
                    }
                }
            });

        } catch (MqttException ex){
            Log.d(TAG, "Exception whilst subscribing");
            ex.printStackTrace();
        }
    }
    private void parseDeviceInfo(MqttMessage message) throws Exception{
        String jsonString = new String(message.getPayload());
        JSONObject jsonObj = new JSONObject(jsonString);
        Log.d(TAG, jsonString);
        String device = jsonObj.getString("id");
        ArrayList<String> listData = new ArrayList<String>();
        JSONArray sockets = jsonObj.getJSONArray("sockets");
        if (sockets != null) {
            for (int i = 0; i < sockets.length(); i++)
                listData.add(sockets.getString(i));
        }
        deviceLinkedHashMap.put(device, listData);
        if (!deviceList.contains(device)) {
            deviceList.add(device);
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    deviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    }
    private void parseDeviceInfo(JSONObject jsonObj) throws Exception{
        String device = jsonObj.getString("id");
        ArrayList<String> listData = new ArrayList<String>();
        JSONArray sockets = jsonObj.getJSONArray("sockets");
        if (sockets != null) {
            for (int i = 0; i < sockets.length(); i++)
                listData.add(sockets.getString(i));
        }
        deviceLinkedHashMap.put(device, listData);
        if (!deviceList.contains(device)) {
            deviceList.add(device);
        }
    }
    private void parseDevicesInfo(MqttMessage message) throws Exception{
        String jsonString = new String(message.getPayload());
        JSONObject jsonObj = new JSONObject(jsonString);
        Log.d(TAG, jsonString);
        JSONArray devices = jsonObj.getJSONArray("devices");
        for(int i = 0; i < devices.length(); i++){
            JSONObject deviceObj = devices.getJSONObject(i);
            parseDeviceInfo(deviceObj);
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                deviceListAdapter.notifyDataSetChanged();
            }
        });
    }
    public void publishMessage(String publishTopic){
        try {
            MqttMessage message = new MqttMessage();
            message.setPayload("synchronize".getBytes());
            mqttClient.publish(publishTopic, message);
            Log.d(TAG, "Message Published");
            if(!mqttClient.isConnected()){
                Log.d(TAG, mqttClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (MqttException e) {
            System.err.println("Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.action_setup) {
            Intent intent = new Intent(this, SetupActivity.class);
            startActivity(intent);
            return true;
        }
        else{
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // synchronize devices information.
        if(mqttClient.isConnected()) {
            publishMessage(syncDeviceInfoTopic);
        }
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}