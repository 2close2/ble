package com.demo.mybluetoothdemo.activity;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.demo.mybluetoothdemo.R;
import com.demo.mybluetoothdemo.callback.BleConnectionCallBack;
import com.demo.mybluetoothdemo.callback.MyBleCallBack;
import com.demo.mybluetoothdemo.entity.EventMsg;
import com.demo.mybluetoothdemo.utils.CheckUtils;
import com.demo.mybluetoothdemo.utils.Constants;
import com.demo.mybluetoothdemo.utils.bleutils.BleConnectUtil;
import com.kaopiz.kprogresshud.KProgressHUD;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.demo.mybluetoothdemo.utils.bleutils.BleConnectUtil.mBluetoothGattCharacteristic;


/**
 * 思路/步骤：
 * 1、权限问题：先判断手机是否满足android4.3以上版本，再判断手机是否开启蓝牙。
 * 2、搜索蓝牙：搜索蓝牙，回调接口中查看ble设备相关信息，一定时间停止扫描。
 * 3、连接蓝牙：首先获取到ble设备的mac地址，然后调用connect()方法进行连接。
 * 4、获取特征：蓝牙连接成功后，需要获取蓝牙的服务特征等，然后开启接收设置。
 * 5、发送消息：writeCharacteristic()方法，发送数据给ble设备。
 * 6、接收消息：通过蓝牙的回调接口中onCharacteristicRead()方法，接收蓝牙收的消息。
 * 7、释放资源：断开连接，关闭资源。
 * <p>
 * PS:请修改 BleConnectUtil.java文件中serviceUuidStr、writeCharactUuid以及notifyCharactUuid这三个变量值，确保与您设备的uuid相同才可通讯。
 *
 * @author wangheru
 * @version 1.0.3
 * @date 2020/6/3
 */
public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    private static final String TAG = "MainActivity";
    @BindView(R.id.btn_scan)
    Button btnScan;
    @BindView(R.id.tv_ble_name)
    TextView tvBleName;
    @BindView(R.id.listview)
    ListView listview;
    @BindView(R.id.btn_send)
    Button btnSend;
    @BindView(R.id.btn_disconnect)
    Button btnDisconnect;
    @BindView(R.id.ed_write_order)
    EditText edWriteOrder;
    @BindView(R.id.tv_receiver)
    TextView tvReceiver;
    @BindView(R.id.ll_status_bluetooth)
    LinearLayout llBlueStatus;
    @BindView(R.id.ll_status_locatioin)
    LinearLayout llLocationStatus;


    private List<BluetoothDevice> listDevice;
    private List<String> listDeviceName;
    private ArrayAdapter<String> adapter;

    private int selectPos;
    private KProgressHUD dialog;
    int regainBleDataCount = 0;
    String currentRevice, currentSendOrder;
    byte[] sData = null;

    //
    private BLeBroadcastReceiver mBroadcastReceiver = new BLeBroadcastReceiver();
    private LocationBroadcastReceiver mLocationBroadcastReceiver = new LocationBroadcastReceiver();
    //
    /**
     * 跟ble通信的标志位,检测数据是否在指定时间内返回
     */
    private boolean bleFlag = false;
    BleConnectUtil bleConnectUtil;

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 10:
                    //接收到数据，显示在界面上
                    dialog.dismiss();
                    tvReceiver.append(msg.obj.toString() + "\n");
                    break;
                case 1000:
                    regainBleDataCount = 0;
                    bleFlag = false;
                    handler.removeCallbacks(checkConnetRunnable);

                    if (dialog != null) {
                        dialog.dismiss();
                    }
                    Toast.makeText(MainActivity.this, "超时请重试!", Toast.LENGTH_SHORT).show();
                    break;
                case 1111:
                    tvBleName.setVisibility(View.GONE);
                    tvReceiver.setText("");
                    edWriteOrder.setText("");
                    bleConnectUtil.disConnect();
                    break;
                default:
                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        EventBus.getDefault().register(this);
        initView();

        mLocationBroadcastReceiver.setStatus(true);
        registerReceiver(mLocationBroadcastReceiver, new IntentFilter("android.location.PROVIDERS_CHANGED"));
        mBroadcastReceiver.setStatus(true);
        registerReceiver(mBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        checkDeviceSupport();
        checkBluetoothStatus();
        checkLocationStats();
//        //添加一个关于权限判断的东西
//        scanBle();
    }


    private void initView() {
        listDevice = new ArrayList<>();
        listDeviceName = new ArrayList<>();

        bleConnectUtil = new BleConnectUtil(MainActivity.this);
        listview.setVisibility(View.VISIBLE);
        adapter = new ArrayAdapter(MainActivity.this, android.R.layout.simple_list_item_1, listDeviceName);
        listview.setAdapter(adapter);
        listview.setOnItemClickListener(this);

        dialog = CheckUtils.showDialog(MainActivity.this);
    }

    private void scanBle() {
        bleConnectUtil.bluetoothIsAble(new MyBleCallBack() {
            @Override
            public void callbleBack(BluetoothDevice device) {
                Log.e("--->搜索到的蓝牙名字：", device.getName());
                if (device == null) {
                    return;
                }

                boolean NoAdd = true;
                for (BluetoothDevice oldDevice : listDevice) {
                    if (oldDevice.getAddress().equals(device.getAddress())) {
                        NoAdd = false;
                        break;
                    }
                }
                if (NoAdd) {
                    listDevice.add(device);
//                    listDeviceName.add(device.getName());
                    listDeviceName.add(device.getAddress());
                    adapter.notifyDataSetChanged();
                }
            }
        });
    }

    @OnClick({R.id.btn_scan, R.id.btn_send, R.id.btn_disconnect, R.id.tx_open_ble, R.id.tx_open_location})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.btn_scan:
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                    listview.setVisibility(View.VISIBLE);
                    return;
                }
                String scanStr = btnScan.getText().toString().trim();
                switch (scanStr) {
                    case "开始扫描":
                        listDevice.clear();
                        listDeviceName.clear();
                        adapter.notifyDataSetChanged();
                        listview.setVisibility(View.VISIBLE);
                        scanBle();
                        btnScan.setText("停止扫描");
                        break;
                    case "停止扫描":
                        bleConnectUtil.stopScan();
                        btnScan.setText("开始扫描");
                        break;
                    default:
                        break;
                }

                break;
            case R.id.btn_send:
                if (bleConnectUtil.isConnected()) {
                    currentSendOrder = edWriteOrder.getText().toString().trim();
                    if (!TextUtils.isEmpty(currentSendOrder)) {
                        if (CheckUtils.isHexNum(currentSendOrder)) {
                            dialog.show();
                            bleFlag = false;
                            regainBleDataCount = 0;
                            currentRevice = "";

                            sendDataByBle(currentSendOrder, "");
                            handler.postDelayed(checkConnetRunnable, 3000);
                        } else {
                            Toast.makeText(MainActivity.this, "请输入十六进制指令", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "请输入指令", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "请连接蓝牙", Toast.LENGTH_SHORT).show();
                }

                break;
            case R.id.btn_disconnect:
                if (bleConnectUtil.isConnected()) {
                    handler.sendEmptyMessage(1111);
                } else {
                    Toast.makeText(MainActivity.this, "请连接蓝牙", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.tx_open_ble:
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
                break;
            case R.id.tx_open_location:
//            请求打开软件的设置页面
                Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                this.startActivity(settingsIntent);
                break;
            default:
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(EventMsg eventMsg) {
        switch (eventMsg.getMsg()) {
            case Constants.BLE_CONNECTION_FINISH_MSG:
                //蓝牙连接完成
                if (dialog != null) {
                    dialog.dismiss();
                }
                bleConnectUtil.stopScan();
                btnScan.setText("开始扫描");

                if (bleConnectUtil.isConnected()) {
                    Toast.makeText(MainActivity.this, "连接成功", Toast.LENGTH_SHORT).show();
                    tvBleName.setVisibility(View.VISIBLE);
                    tvBleName.setText("您所连接的设备是:" + listDeviceName.get(selectPos));
                    listview.setVisibility(View.GONE);
                    bleConnectUtil.setCallback(blecallback);
                } else {
                    Toast.makeText(MainActivity.this, "连接失败", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }

    /**
     * 设置回调方法
     */
    private BleConnectionCallBack blecallback = new BleConnectionCallBack() {

        @Override
        public void onRecive(BluetoothGattCharacteristic data_char) {
            bleFlag = true;

            //收到的数据
            byte[] receive_byte = data_char.getValue();
            String str = CheckUtils.byte2hex(receive_byte).toString();

            Message message = new Message();
            message.obj = str;
            message.what = 10;
            handler.sendMessage(message);
        }

        @Override
        public void onSuccessSend() {
            //数据发送成功
            Log.e(TAG, "onSuccessSend: ");

        }

        @Override

        public void onDisconnect() {
            //设备断开连接
            Log.e(TAG, "onDisconnect: ");
            Message message = new Message();
            message.what = 1111;
            handler.sendMessage(message);
        }
    };

    /**
     * android ble 发送
     * 每条数据长度应保证在20个字节以内
     * 2条数据至少要空15ms
     *
     * @param currentSendAllOrder
     * @param title
     */
    private void sendDataByBle(final String currentSendAllOrder, String title) {
        if (currentSendAllOrder.length() > 0) {
            if (!title.equals("")) {
//                showDialog(title);
                Log.d("--->", title);
            }
            currentSendOrder = currentSendAllOrder;
            final boolean[] isSuccess = new boolean[1];
            //sd
            if (currentSendAllOrder.length() <= 100) {
                sData = CheckUtils.hex2byte(currentSendOrder);
                mBluetoothGattCharacteristic.setValue(sData);
                isSuccess[0] = bleConnectUtil.sendData(mBluetoothGattCharacteristic);
            } else {
                for (int i = 0; i < currentSendAllOrder.length(); i = i + 40) {
                    final String[] shortOrder = {""};
                    final int finalI = i;

                    if (currentSendAllOrder.length() - i >= 40) {
                        shortOrder[0] = currentSendAllOrder.substring(finalI, finalI + 40);
                    } else {
                        shortOrder[0] = currentSendAllOrder.substring(finalI, currentSendAllOrder.length());
                    }

                    Log.e("--->", "shortOrder[0]2：" + shortOrder[0]);
                    sData = CheckUtils.hex2byte(shortOrder[0]);
                    mBluetoothGattCharacteristic.setValue(sData);
                    isSuccess[0] = bleConnectUtil.sendData(mBluetoothGattCharacteristic);
                }
            }
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isSuccess[0]) {
                        dialog.dismiss();
                        handler.sendEmptyMessage(1111);
                    }
                    Log.e("--->", "是否发送成功：" + isSuccess[0]);
                }
            }, (currentSendAllOrder.length() / 40 + 1) * 15);
        }
    }

    /**
     * 蓝牙连接检测线程
     */
    Runnable checkConnetRunnable = new Runnable() {
        @Override
        public void run() {
            // TODO Auto-generated method stub
            if (!bleFlag) {
                //没有在指定时间收到回复
                if (regainBleDataCount > 2) {
                    handler.sendEmptyMessage(1000);
                } else {
                    regainBleDataCount++;

                    sendDataByBle(currentSendOrder, "");
                    handler.postDelayed(checkConnetRunnable, 3000);
                }
            }
        }
    };

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        bleConnectUtil.stopScan();
        selectPos = position;
        dialog.show();

        bleConnectUtil.connectBle(listDevice.get(position));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleConnectUtil != null) {
            bleConnectUtil.disConnect();
        }
        EventBus.getDefault().unregister(this);
    }


    private long exitTime = 0;

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if ((System.currentTimeMillis() - exitTime) > 2000) {
                    Toast.makeText(getApplicationContext(), "再按一次退出程序", Toast.LENGTH_LONG).show();
                    exitTime = System.currentTimeMillis();
                } else {
                    MainActivity.this.finish();
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }


    private class BLeBroadcastReceiver extends BroadcastReceiver {
        private boolean status = false;

        public void setStatus(boolean status) {
            this.status = status;
        }

        public boolean getStatus() {
            return status;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            checkBluetoothStatus();
        }
    }

    private class LocationBroadcastReceiver extends BroadcastReceiver {
        private boolean status = false;

        public void setStatus(boolean status) {
            this.status = status;
        }

        public boolean getStatus() {
            return status;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            checkLocationStats();
        }
    }

    //检测设备是否支持BLE
    private void checkDeviceSupport() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "设备不支持", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    //检测蓝牙状态
    private void checkBluetoothStatus() {
        if (BleConnectUtil.mBluetoothAdapter == null || !BleConnectUtil.mBluetoothAdapter.isEnabled()) {
            llBlueStatus.setVisibility(View.VISIBLE);
        } else {
            llBlueStatus.setVisibility(View.GONE);
            if (mBroadcastReceiver.getStatus() == true) {
                unregisterReceiver(mBroadcastReceiver);
                mBroadcastReceiver.setStatus(false);
            }
        }
    }

    //    检测定位状态
    private void checkLocationStats() {
        if (!((LocationManager) (this.getSystemService(Context.LOCATION_SERVICE))).isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            llLocationStatus.setVisibility(View.VISIBLE);
        } else {
            llLocationStatus.setVisibility(View.GONE);
            if (mLocationBroadcastReceiver.getStatus() == true) {
                unregisterReceiver(mLocationBroadcastReceiver);
                mLocationBroadcastReceiver.setStatus(false);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                llLocationStatus.setVisibility(View.GONE);
                onViewClicked(findViewById(R.id.btn_scan));
            } else {
                Toast.makeText(this, "拒绝将导致软件无法运行", Toast.LENGTH_LONG).show();
            }
        }
    }
}
