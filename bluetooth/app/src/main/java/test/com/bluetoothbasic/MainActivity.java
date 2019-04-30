package test.com.bluetoothbasic;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemClickListener {

    private static final String TAG = "bluetoothBasic";
    private static final int REQUEST_PERMISSION_ACCESS_LOCATION = 1;
    private ListView mList;
    private ArrayList<BluetoothDevice> bluetoothDeviceArrayList;
    private BluetoothAdapter bluetoothAdapter;
    private ListViewAdapter adapter;
    private ToggleButton mSwitch;
    private Button mGetBindDevice;
    private Button mSearch;
    private UUID uuid = UUID.fromString("00001106-0000-1000-8000-00805F9B34FB");
    private Button mStartService;
    private TextView state;
    private static final int startService = 0;
    private static final int getMessageOk = 1;
    private static final int sendOver = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter != null) {
            state = (TextView) findViewById(R.id.state);
            mGetBindDevice = (Button) findViewById(R.id.getBindDevice);
            mGetBindDevice.setOnClickListener(this);
            mSearch = (Button) findViewById(R.id.search);
            mSearch.setOnClickListener(this);
            mStartService = (Button) findViewById(R.id.startService);
            mStartService.setOnClickListener(this);
            mSwitch = (ToggleButton) findViewById(R.id.btn_switch);
            mSwitch.setOnClickListener(this);
            //When you open the app,you will get the bluetooth status firstly.when
            //the bluetooth is on,the mSwitch is off status.
            mSwitch.setChecked(!bluetoothAdapter.isEnabled());
            mList = (ListView) findViewById(R.id.list);
            bluetoothDeviceArrayList = new ArrayList<>();
            adapter = new ListViewAdapter();
            mList.setAdapter(adapter);
            mList.setOnItemClickListener(this);

            //register broadcast
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
            intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            registerReceiver(new BluetoothReceiver(), intentFilter);
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case startService:
                    state.setText("服务已打开");
                    break;
                case sendOver:
                    Toast.makeText(MainActivity.this, "发送成功", Toast.LENGTH_SHORT).show();
                    break;
                case getMessageOk:
                    state.setText(msg.obj.toString());
                    break;
            }
        }
    };

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            int checkAccessFinePermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
            if (checkAccessFinePermission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_PERMISSION_ACCESS_LOCATION);
                Log.d(TAG, "没有权限，请求权限");
                return;
            }
            Log.d(TAG, "已有定位权限");
            search();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1 : {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "定位权限已经开启");
                    search();
                } else {
                    Log.d(TAG, "没有定位权限，请先开启!");
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void search() {
        if (bluetoothAdapter.isDiscovering()) { bluetoothAdapter.cancelDiscovery(); }
        bluetoothAdapter.startDiscovery();
        Log.e(TAG, "开始搜索");
    }

    public void getBindDevice() {
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
        bluetoothDeviceArrayList.clear();
        bluetoothDeviceArrayList.addAll(bondedDevices);
        adapter.notifyDataSetChanged();
    }

    private void bondDevice(int position) {
        try {
            Method method = BluetoothDevice.class.getMethod("createBond");
            Log.d(TAG, "开始配对");
            method.invoke(bluetoothDeviceArrayList.get(position));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(final int position) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                OutputStream os = null;
                try {
                    BluetoothSocket socket = bluetoothDeviceArrayList.get(position).createRfcommSocketToServiceRecord(uuid);
                    socket.connect();
                    os = socket.getOutputStream();
                    os.write("hello bluetooth".getBytes());
                    os.flush();
                    mHandler.sendEmptyMessage(sendOver);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void getMessage() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream is = null;
                try {
                    BluetoothServerSocket serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("serverSocket", uuid);
                    mHandler.sendEmptyMessage(startService);
                    BluetoothSocket accept = serverSocket.accept();
                    is = accept.getInputStream();

                    byte[] bytes = new byte[1024];
                    int length = is.read(bytes);

                    Message msg = new Message();
                    msg.what = getMessageOk;
                    msg.obj = new String(bytes, 0, length);
                    mHandler.sendMessage(msg);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_switch:
            if (mSwitch.isChecked()) {
                bluetoothAdapter.disable();
            } else {
                //The first way to enable bluetooth
                bluetoothAdapter.enable();
                //The second way to enable bluetooth
//                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//                startActivityForResult(intent, 1);
                //The third way to enable bluetooth
//                    Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
//                    discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
//                    startActivity(discoverableIntent);
            }
            break;
            case R.id.getBindDevice:
                getBindDevice();
                break;
            case R.id.search:
                requestPermission();
                break;
            case R.id.startService:
                getMessage();
                break;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (bluetoothAdapter.isDiscovering()) { bluetoothAdapter.cancelDiscovery();}
        if (bluetoothDeviceArrayList.get(position).getBondState() == BluetoothDevice.BOND_NONE) {
            bondDevice(position);
        } else if (bluetoothDeviceArrayList.get(position).getBondState() == BluetoothDevice.BOND_BONDED) {
            sendMessage(position);
        }
    }

    class ListViewAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return bluetoothDeviceArrayList.size();
        }

        @Override
        public Object getItem(int position) {
            return bluetoothDeviceArrayList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            convertView = LayoutInflater.from(MainActivity.this).inflate(android.R.layout.simple_list_item_1, parent, false);
            BluetoothDevice device = bluetoothDeviceArrayList.get(position);
            ((TextView) convertView).setText(device.getName() + "--------" + (device.getBondState() == BluetoothDevice.BOND_BONDED ? "已经绑定" : "还没绑定"));
            return convertView;
        }
    }

    class BluetoothReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BluetoothDevice.ACTION_FOUND)) {
                Log.d(TAG, "找到新设备");
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                boolean addFlag = true;
                for (BluetoothDevice bluetoothDevice : bluetoothDeviceArrayList) {
                    if (device.getAddress().equals(bluetoothDevice.getAddress())) {
                        addFlag = false;
                    }
                }
                Log.d(TAG, "the size of bluetooth is " + bluetoothDeviceArrayList.size());
                /*sometimes,it will find the bluetooth device has address,but the device name is empty.therefore,
                *we should exclude those device before show them. */
                if (addFlag && (device.getName() != null)) {
                    bluetoothDeviceArrayList.add(device);
                    adapter.notifyDataSetChanged();
                }
            } else if (intent.getAction().equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                switch (device.getBondState()) {
                    case BluetoothDevice.BOND_NONE:
                        Log.d(TAG, "取消配对");
                        break;
                    case BluetoothDevice.BOND_BONDING:
                        Log.d(TAG, "配对中");
                        break;
                    case BluetoothDevice.BOND_BONDED:
                        Log.d(TAG, "配对成功");
                        break;
                }

            }
        }
    }
}

























