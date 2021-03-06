package tw.edu.nctu.pcslab.socketctl;
import android.app.Service;
import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Vibrator;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Switch;
import android.widget.TextView;

import com.github.aakira.expandablelayout.Utils;
import com.google.gson.Gson;
import com.qmuiteam.qmui.util.QMUIDisplayHelper;
import com.qmuiteam.qmui.widget.QMUITopBar;
import com.qmuiteam.qmui.widget.popup.QMUIListPopup;
import com.qmuiteam.qmui.widget.popup.QMUIPopup;

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
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import tw.edu.nctu.pcslab.recyclerview.ItemModel;
import tw.edu.nctu.pcslab.recyclerview.RecyclerViewRecyclerAdapter;
import tw.edu.nctu.pcslab.sectionlistview.DeviceCell;


public class ControllerActivity extends AppCompatActivity {

    private final String TAG = "ControllerActivity";
    public static Context context;

    /* device list for ui */
    private ArrayList<DeviceCell> deviceList;
    private RecyclerView expandAbleDeviceView;
    private RecyclerViewRecyclerAdapter expandAbleDeviceViewAdapter;
    private List<ItemModel> selectMorSocketList;
    private DeviceCell currentDevice;
    private Boolean refreshCurrentDeviceUI;

    /* socket list for ui */
    private ArrayList<String> socketList;
    private ArrayAdapter<String> socketListAdapter;
    private ToneGenerator toneG;

    /* device and socket list relation */
    private LinkedHashMap<DeviceCell, ArrayList<Socket>> deviceLinkedHashMap;

    /* appliance list */
    private ArrayList<String> applianceArrayList;
    private ArrayAdapter<String> applianceListAdapter;
    private QMUIListPopup applianceListPopup;


    /* Mqtt client */
    public MqttAndroidClient mqttClient;
//    private String mqttUri = "tcp://192.168.51.25:1883";
    private String mqttUri = "tcp://140.113.215.17:1883";
    private String clientId = UUID.randomUUID().toString();
    // subscribe
    private String deviceInfoTopic = "DeviceInfo"; // when there is a device online
    private String devicesInfoTopic = "DevicesInfo"; // receive after SyncDeviceInfo
    private String switchesInfoTopic = "SwitchesInfo";
    // publish
    private String syncDeviceInfoTopic = "SyncDeviceInfo"; // when app open
    private String switchTopic = "Switch";
    private String switchDisable = "SwitchDisable";
    private String aliasTopic = "Alias";
    /* Save and load data */
    private SharedPreferences  prefs;
    private SharedPreferences.Editor prefsEditor;
    private Gson gson;



    private void initListPopupIfNeed(int socketListViewIndex) {

        final ListView socketListView = (ListView) findViewById(R.id.socket_list_view);
        final Switch sswitch = (Switch) socketListView.getChildAt(socketListViewIndex).findViewById(R.id.sswitch);
        final Button applianceListButton = (Button) socketListView.getChildAt(socketListViewIndex).findViewById(R.id.appliance_list_button);
        TextView socketRowTextView = (TextView)socketListView.getChildAt(socketListViewIndex).findViewById(R.id.socket_row_text_view);
        final String index = socketRowTextView.getText().toString();
        applianceListPopup = new QMUIListPopup(getContext(), QMUIPopup.DIRECTION_NONE, applianceListAdapter);
        applianceListPopup.create(QMUIDisplayHelper.dp2px(getContext(), 250), QMUIDisplayHelper.dp2px(getContext(), 200), new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                if(position != applianceArrayList.size()-1) {
                    LinearLayout applianceRowView = (LinearLayout) view;
                    TextView tv = (TextView) applianceRowView.findViewById(R.id.appliance_row_text_view);
                    applianceListButton.setText(tv.getText());

                    // update deviceLinkedHashMap
                    ArrayList<Socket> sockets = getDeviceLinkedHashMapByKey(currentDevice);
                    for (Socket s : sockets) {
                        if (s.index.intValue() == Integer.parseInt(index)) {
                            s.alias = applianceArrayList.get(position);
                            break;
                        }
                    }
                    sswitch.setVisibility(View.VISIBLE);
                    // push alias message
                    JSONObject aliasObj = new JSONObject();
                    try {
                        aliasObj.put("id", currentDevice.getName());
                        aliasObj.put("index", index);
                        aliasObj.put("alias", applianceArrayList.get(position));
                        MqttMessage message = new MqttMessage();
                        message.setPayload(aliasObj.toString().getBytes());
                        publishMessage(aliasTopic, message);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    applianceListPopup.dismiss();
                }
                else{
                    AlertDialog.Builder builder;
                    builder = new AlertDialog.Builder(ControllerActivity.this);
                    View viewInflated = LayoutInflater.from(ControllerActivity.this).inflate(R.layout.dialog_edit_text, (ViewGroup) findViewById(android.R.id.content), false);
                    final EditText dialog_edit_text = (EditText)viewInflated.findViewById(R.id.dialog_edit_text);
                    builder.setView(viewInflated);
                    builder.setTitle(R.string.enter_your_appliance)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    String appliance = dialog_edit_text.getText().toString();
                                    if(!appliance.isEmpty()) {
                                        applianceArrayList.add(applianceArrayList.size()-1, appliance);
                                        String json = gson.toJson(applianceArrayList);
                                        prefsEditor.putString("applianceArrayList", json);
                                        prefsEditor.commit();
                                        Log.d(TAG, applianceArrayList.toString());
                                        applianceListAdapter.notifyDataSetChanged();
                                        sswitch.setVisibility(View.VISIBLE);
                                        // push alias message
                                        JSONObject aliasObj = new JSONObject();
                                        try {
                                            aliasObj.put("id", currentDevice.getName());
                                            aliasObj.put("index", index);
                                            aliasObj.put("alias", appliance);
                                            MqttMessage message = new MqttMessage();
                                            message.setPayload(aliasObj.toString().getBytes());
                                            publishMessage(aliasTopic, message);
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }
                                        applianceListButton.setText(appliance);
                                    }
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    applianceListButton.setText(getResources().getText(R.string.select_appliance));
                                    sswitch.setVisibility(View.INVISIBLE);
                                }
                            }).
                            setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialog) {
                                    applianceListPopup.dismiss();
                                }
                            })
                            .show();
                }
            }
        });
        applianceListPopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                //mActionButton2.setText(getContext().getResources().getString(R.string.popup_list_action_button_text_show));
            }
        });
    }

    private void updateSocketListView(Boolean refresh){
        final ListView socketListView = (ListView) findViewById(R.id.socket_list_view);
        if(toneG == null)
            toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        for(int i = 0; i < socketListView.getChildCount(); i++){
            final int socketListViewIndex = i;
            final Switch sswitch = (Switch) socketListView.getChildAt(i).findViewById(R.id.sswitch);
            final ImageView sswitchIcon = (ImageView) socketListView.getChildAt(i).findViewById(R.id.sswitch_icon);
            final Button appliancesListButton = (Button) socketListView.getChildAt(i).findViewById(R.id.appliance_list_button);
            TextView socketRowTextView = (TextView)socketListView.getChildAt(i).findViewById(R.id.socket_row_text_view);
            final String index = socketRowTextView.getText().toString();
            final View socketListViewRowItem = socketListView.getChildAt(i);
            sswitch.setTag(null);
            CompoundButton.OnCheckedChangeListener sswitchCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                    // animation
                    final Animation animation = new AlphaAnimation(1, 0); // Change alpha from fully visible to invisible
                    animation.setDuration(100);
                    animation.setInterpolator(new LinearInterpolator()); // do not alter animation rate
                    animation.setRepeatCount(1); // Repeat animation infinitely
                    animation.setRepeatMode(Animation.REVERSE); // Reverse animation at the end so the button will fade back in
                    socketListViewRowItem.startAnimation(animation);

                    // vibration
                    setVibrate(100);

                    // sound;
                    toneG.startTone(ToneGenerator.TONE_CDMA_CALLDROP_LITE, 100);
                    Log.d(TAG, "isChedcked:" + isChecked);
                    if(sswitch.getTag() != null && sswitch.getTag().equals("touchByUser")) {
                        JSONObject data = new JSONObject();
                        try {
                            data.put("id", currentDevice.getName());
                            data.put("index", index);
                            data.put("state", isChecked);
                            MqttMessage message = new MqttMessage();
                            message.setPayload(data.toString().getBytes());
                            publishMessage(switchTopic, message);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    sswitch.setTag(null);
                    // update deviceLinkedHashMap
                    ArrayList<Socket> sockets = getDeviceLinkedHashMapByKey(currentDevice);
                    for(Socket s: sockets){
                        if(s.index.intValue() == Integer.parseInt(index)){
                            s.state = isChecked;
                        }
                    }
                }
            };
            sswitch.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    sswitch.setTag("touchByUser");
                    return false;
                }
            });
            sswitch.setOnCheckedChangeListener(sswitchCheckedChangeListener);
            sswitchIcon.setOnClickListener(new View.OnClickListener(){
                public void onClick(View view){
                    if(sswitch.isEnabled() && appliancesListButton.isEnabled()){
                        JSONObject switchDisableObj = new JSONObject();
                        try {
                            switchDisableObj.put("id", currentDevice.getName());
                            switchDisableObj.put("index", index);
                            switchDisableObj.put("disable", true);
                            MqttMessage message = new MqttMessage();
                            message.setPayload(switchDisableObj.toString().getBytes());
                            publishMessage(switchDisable, message);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        sswitch.setEnabled(false);
                        appliancesListButton.setEnabled(false);
                        socketListViewRowItem.setAlpha(0.3f);
                    }
                    else {
                        JSONObject switchDisableObj = new JSONObject();
                        try {
                            switchDisableObj.put("id", currentDevice.getName());
                            switchDisableObj.put("index", index);
                            switchDisableObj.put("disable", false);
                            MqttMessage message = new MqttMessage();
                            message.setPayload(switchDisableObj.toString().getBytes());
                            publishMessage(switchDisable, message);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        sswitch.setEnabled(true);
                        appliancesListButton.setEnabled(true);
                        socketListViewRowItem.setAlpha(1.0f);
                    }
                    Log.d(TAG, "SwitchDisable" + index.toString());
                }
            });
            //click listener for appliancesListSpinner
            appliancesListButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    initListPopupIfNeed(socketListViewIndex);
                    applianceListPopup.setAnimStyle(QMUIPopup.ANIM_AUTO);
                    applianceListPopup.setPreferredDirection(QMUIPopup.DIRECTION_NONE);
                    applianceListPopup.show(v);
                }
            });

        }
        if(currentDevice != null && (refreshCurrentDeviceUI || refresh)) {
            ArrayList<Socket> sl = new ArrayList<Socket>(getDeviceLinkedHashMapByKey(currentDevice));
            // update socketList row content: alias, state
            for (int i = 0; i < sl.size(); i++) {
                Socket socket = sl.get(i);
                // appliancesArrayList
                Button appliancesListButton = null;
                Switch sswitch = null;
                View socketListViewRowItem = null;

                for(int j = 0; j < socketListView.getChildCount(); j++){
                    TextView t = (TextView) socketListView.getChildAt(j).findViewById(R.id.socket_row_text_view);
                    if(t.getText().toString().equals(socket.index.toString())) {
                        appliancesListButton = (Button) socketListView.getChildAt(j).findViewById(R.id.appliance_list_button);
                        sswitch = (Switch) socketListView.getChildAt(j).findViewById(R.id.sswitch);
                        socketListViewRowItem = socketListView.getChildAt(j);
                        break;
                    }
                }
                if(appliancesListButton == null)
                    continue;
                if (socket.alias != null) {
                    int appliancesListSpinnerIndex = isArrayListContains(applianceArrayList, socket.alias);
                    //Log.d(TAG, new Integer(appliancesListSpinnerIndex).toString());
                    if (appliancesListSpinnerIndex == -1) { //insert to appliancesArrayList
                        applianceArrayList.add(applianceArrayList.size() - 1, socket.alias);
                    }
                    appliancesListButton.setText(socket.alias);
                } else {
                    appliancesListButton.setText(getResources().getString(R.string.select_appliance));
                    sswitch.setVisibility(View.INVISIBLE);
                }
                // sswitch
                if(sswitch.isChecked() != socket.state)
                    sswitch.setChecked(socket.state);
                if(socket.disable) {
                    sswitch.setEnabled(false);
                    appliancesListButton.setEnabled(false);
                    socketListViewRowItem.setAlpha(0.3f);
                }
            }
            refreshCurrentDeviceUI = false;
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);

        context = ControllerActivity.this;
        prefs = getPreferences(MODE_PRIVATE);
        prefsEditor = prefs.edit();
        gson = new Gson();

        currentDevice = null;
        deviceLinkedHashMap = new LinkedHashMap<DeviceCell, ArrayList<Socket>>();

        //UI
        deviceList = new ArrayList<DeviceCell>();
        selectMorSocketList = new ArrayList<>();
        selectMorSocketList.add(new ItemModel(
                getResources().getString(R.string.select_morsocket_placeholder),
                R.color.gray,
                R.color.white,
                Utils.createInterpolator(Utils.BOUNCE_INTERPOLATOR),
                deviceList));
        expandAbleDeviceView = (RecyclerView) findViewById(R.id.device_alias_recycler_View);
        expandAbleDeviceView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        expandAbleDeviceView.setLayoutManager(linearLayoutManager);
        expandAbleDeviceViewAdapter = new RecyclerViewRecyclerAdapter(selectMorSocketList, this);
        expandAbleDeviceView.setAdapter(expandAbleDeviceViewAdapter);


        if(applianceArrayList == null) {
            ArrayList<String> userCreateAppliances = gson.fromJson(prefs.getString("applianceArrayList", ""), ArrayList.class);
            if(userCreateAppliances != null)
                applianceArrayList = new ArrayList<String>(userCreateAppliances);
            else
                applianceArrayList = new ArrayList<String>(Arrays.asList(getResources().getStringArray(R.array.appliances)));
        }
        applianceListAdapter = new ArrayAdapter<String> (ControllerActivity.this, R.layout.appliance_row_view, R.id.appliance_row_text_view, applianceArrayList);

        final ListView socketListView = (ListView) findViewById(R.id.socket_list_view);
        socketList = new ArrayList<String>();
        socketListAdapter = new ArrayAdapter<String>(getBaseContext(), R.layout.socket_row_view, R.id.socket_row_text_view, socketList);
        socketListView.setAdapter(socketListAdapter);

        socketListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                // TODO Auto-generated method stub
            }
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    updateSocketListView(true);
                }
            }
        });
        socketListView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() { // rebind onCheckedChanged event after Layout has been changed
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                updateSocketListView(false);
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
                    subscribeToTopic(switchesInfoTopic);
                    MqttMessage message = new MqttMessage();
                    message.setPayload("synchronize".getBytes());
                    publishMessage(syncDeviceInfoTopic, message);
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
        mqttConnectOptions.setConnectionTimeout(10);
//        mqttConnectOptions.setKeepAliveInterval(30);
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
                    subscribeToTopic(switchesInfoTopic);
                    MqttMessage message = new MqttMessage();
                    message.setPayload("synchronize".getBytes());
                    publishMessage(syncDeviceInfoTopic, message);

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

    public static Context getContext(){
        return context;
    }
    public void setVibrate(int time){
        Vibrator myVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        myVibrator.vibrate(time);
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
                    else if(topic.equals(switchesInfoTopic)){
                        parseSwitchesInfo(message);
                    }
                }
            });

        } catch (MqttException ex){
            Log.d(TAG, "Exception whilst subscribing");
            ex.printStackTrace();
        }
    }
    private int isArrayListContains(ArrayList<String> al, String s){
        for(int i = 0; i < al.size(); i++){
            String als = al.get(i);
            if(als.equalsIgnoreCase(s)){
                return i;
            }
        }
        return -1;
    }
    private int isDeviceListContains(ArrayList<DeviceCell> al, DeviceCell d){
        for(int i = 0; i < al.size(); i++){
            String als = al.get(i).getName();
            if(als.equalsIgnoreCase(d.getName())){
                return i;
            }
        }
        return -1;
    }
    public void clickDeviceListViewItem(AdapterView<?> adapterView,
                                         View view, int position, long l){
        Log.d(TAG, "click");
        DeviceCell deviceCell = (DeviceCell) adapterView.getItemAtPosition(position);
        if(deviceCell.isSectionHeader())
            return;
        currentDevice = deviceCell;
        updateExpandAbleDeviceView();
        ArrayList<Socket> sl = new ArrayList<Socket>(getDeviceLinkedHashMapByKey(currentDevice));
        socketList.clear();
        for(int i = 0; i < sl.size(); i++) {
            socketList.add(sl.get(i).index.toString());
        }
        Log.d(TAG, socketList.toString());
        socketListAdapter.notifyDataSetChanged();
        refreshCurrentDeviceUI = true;
    }

    private void updateExpandAbleDeviceView(){
        String selectMorSocketListTitle = (currentDevice == null) ?
                getResources().getString(R.string.select_morsocket_placeholder) :
                    currentDevice.getCategory()+"("+currentDevice.getName()+")";
        selectMorSocketList.clear();
        selectMorSocketList.add(new ItemModel(
                selectMorSocketListTitle,
                R.color.gray,
                R.color.white,
                Utils.createInterpolator(Utils.BOUNCE_INTERPOLATOR),
                deviceList));
        expandAbleDeviceView = (RecyclerView) findViewById(R.id.device_alias_recycler_View);
        expandAbleDeviceView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        expandAbleDeviceView.setLayoutManager(linearLayoutManager);
        expandAbleDeviceViewAdapter = new RecyclerViewRecyclerAdapter(selectMorSocketList, this);
        expandAbleDeviceView.setAdapter(expandAbleDeviceViewAdapter);
    }
    private void updateDeviceLinkedHashMap(DeviceCell deviceCell, ArrayList<Socket> listData){
        for(DeviceCell d : deviceLinkedHashMap.keySet()){
            if(d.getName().equals(deviceCell.getName())){
                deviceLinkedHashMap.remove(d);
            }
        }
        deviceLinkedHashMap.put(deviceCell, listData);
    }
    private ArrayList<Socket> getDeviceLinkedHashMapByKey(DeviceCell deviceCell){
        for(DeviceCell d : deviceLinkedHashMap.keySet()){
            if(d.getName().equals(deviceCell.getName())){
                return deviceLinkedHashMap.get(d);
            }
        }
        return null;
    }
    private void parseDeviceInfo(MqttMessage message) throws Exception{
        String jsonString = new String(message.getPayload());
        JSONObject jsonObj = new JSONObject(jsonString);
        Log.d(TAG, jsonString);
        final DeviceCell deviceCell = new DeviceCell(jsonObj.getString("id"), jsonObj.getString("room"));
        final ArrayList<Socket> listData = new ArrayList<Socket>();
        JSONArray sockets = jsonObj.getJSONArray("sockets");
        if (sockets != null) {
            for (int i = 0; i < sockets.length(); i++) {
                JSONObject s = sockets.getJSONObject(i);
                Socket socket;
                if(s.isNull("alias"))
                    socket = new Socket(null,  new Integer(s.getInt("index")), s.getBoolean("state"), s.getBoolean("disable"));
                else
                    socket = new Socket(s.getString("alias"),  new Integer(s.getInt("index")), s.getBoolean("state"), s.getBoolean("disable"));
                listData.add(socket);
            }
        }
        updateDeviceLinkedHashMap(deviceCell, listData);
        Log.d(TAG, "deviceLinkedHashMap:" + deviceLinkedHashMap.size());
        if (isDeviceListContains(deviceList, deviceCell) == -1) {
            deviceList.add(deviceCell);
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    updateExpandAbleDeviceView();
                }
            });
        }
        if(deviceCell.getName().equals(currentDevice.getName())){
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    socketList.clear();
                    for(int i = 0; i < listData.size(); i++) {
                        socketList.add(listData.get(i).index.toString());
                    }
                    refreshCurrentDeviceUI = true;
                    socketListAdapter.notifyDataSetChanged();
                    Log.d(TAG, "socketList" + socketList.toString());
                }
            });
        }
    }
    private void parseDeviceInfo(JSONObject jsonObj) throws Exception{
        final DeviceCell deviceCell = new DeviceCell(jsonObj.getString("id"), jsonObj.getString("room"));
        ArrayList<Socket> listData = new ArrayList<Socket>();
        JSONArray sockets = jsonObj.getJSONArray("sockets");
        if (sockets != null) {
            for (int i = 0; i < sockets.length(); i++) {
                JSONObject s = sockets.getJSONObject(i);
                Socket socket;
                if(s.isNull("alias"))
                    socket = new Socket(null,  new Integer(s.getInt("index")), s.getBoolean("state"), s.getBoolean("disable"));
                else
                    socket = new Socket(s.getString("alias"),  new Integer(s.getInt("index")), s.getBoolean("state"), s.getBoolean("disable"));
                listData.add(socket);
            }
        }
        Log.d(TAG, deviceCell.getName()+" "+deviceCell.getCategory());
        updateDeviceLinkedHashMap(deviceCell, listData);
        Log.d(TAG, "deviceLinkedHashMap:" + deviceLinkedHashMap.size());
        if (isDeviceListContains(deviceList, deviceCell) == -1) {
            deviceList.add(deviceCell);
        }
    }
    private void parseDevicesInfo(MqttMessage message) throws Exception{
        String jsonString = new String(message.getPayload());
        JSONObject jsonObj = new JSONObject(jsonString);
        Log.d(TAG, jsonString);
        JSONArray devices = jsonObj.getJSONArray("devices");

//        new Handler(Looper.getMainLooper()).post(new Runnable() {
//            @Override
//            public void run() {
////                if (currentDevice != null) {
////                    refreshCurrentDeviceUI = true;
//                    // update socketList for device
//                    socketList.clear();
//                    socketListAdapter.notifyDataSetChanged();
//                    deviceList.clear();
////                    expandAbleDeviceViewAdapter.notifyDataSetChanged();
////                } else {
////                    refreshCurrentDeviceUI = false;
////                }
////                currentDevice = null;
////                updateExpandAbleDeviceView();
////                updateExpandAbleDeviceView();
//            }
//        });

        for (int i = 0; i < devices.length(); i++) {
            JSONObject deviceObj = devices.getJSONObject(i);
            parseDeviceInfo(deviceObj);
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                updateExpandAbleDeviceView();
                if (currentDevice != null && isDeviceListContains(deviceList, currentDevice) != -1) {
                    refreshCurrentDeviceUI = true;
                    // update socketList for device
                    ArrayList<Socket> sl = new ArrayList<Socket>(getDeviceLinkedHashMapByKey(currentDevice));
                    socketList.clear();
                    for (int i = 0; i < sl.size(); i++) {
                        socketList.add(sl.get(i).index.toString());
                    }
                    Log.d(TAG, socketList.toString());
                    socketListAdapter.notifyDataSetChanged();
                } else {
                    currentDevice = null;
                    refreshCurrentDeviceUI = false;
                }

            }
        });

    }
    private void parseSwitchesInfo(MqttMessage message) throws Exception {
        String jsonString = new String(message.getPayload());
        final JSONObject jsonObj = new JSONObject(jsonString);
        Log.d(TAG, jsonString);
        if(currentDevice.getName().equals(jsonObj.getString("id"))){
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        JSONArray sockets = jsonObj.getJSONArray("sockets");
                        ListView socketListView = (ListView) findViewById(R.id.socket_list_view);
                        for (int i = 0; i < socketListView.getChildCount(); i++) {
                            TextView socketRowTextView = (TextView) socketListView.getChildAt(i).findViewById(R.id.socket_row_text_view);
                            String index = socketRowTextView.getText().toString();
                            for (int j = 0; j < sockets.length(); j++) {
                                JSONObject socket = sockets.getJSONObject(j);
                                if (index.equals(socket.getString("index"))) {
                                    Switch sswitch = (Switch) socketListView.getChildAt(i).findViewById(R.id.sswitch);
                                    if (sswitch.isChecked() != socket.getBoolean("state"))
                                        sswitch.setChecked(socket.getBoolean("state"));
                                }
                            }
                        }
                    }catch (JSONException e){
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    public void publishMessage(String publishTopic, MqttMessage message){
        try {
            mqttClient.publish(publishTopic, message);
            Log.d(TAG, "Message Published");
            if(!mqttClient.isConnected()){
                Log.d(TAG, mqttClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (MqttException e) {
            Log.d(TAG, "Error Publishing: " + e.getMessage());
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
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
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
            MqttMessage message = new MqttMessage();
            message.setPayload("synchronize".getBytes());
            publishMessage(syncDeviceInfoTopic, message);
        }
        // recovery device information
        if(currentDevice == null){
            currentDevice = gson.fromJson(prefs.getString("currentDevice", ""), DeviceCell.class);
            refreshCurrentDeviceUI = false;
        }
        Log.d(TAG, "on:  "+currentDevice);
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause(){
        super.onPause();
        // save device information
        String json = gson.toJson(currentDevice);
        prefsEditor.putString("currentDevice", json);
        prefsEditor.commit();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

}
