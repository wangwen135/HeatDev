package com.xrkj.app.heatdev;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import com.xrkj.app.heatdev.utils.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.xrkj.app.heatdev.protocol.ProtocolEntity;
import com.xrkj.app.heatdev.utils.Converts;

import java.text.MessageFormat;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, SeekBar.OnSeekBarChangeListener, Spinner.OnItemSelectedListener {
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int WHAT_FIND_BLE_TEXT = 1;
    private static final String TAG = "MainActivity";

    //##### 控件 #####
    private Toolbar toolbar;
    private TextView txtTemperature;
    private Switch switchIO;
    private TextView txtPWM;
    private SeekBar seekBarPWM;
    private SeekBar seekBarMin;
    private SeekBar seekBarMax;
    private TextView txtMin;
    private TextView txtMax;
    private Spinner spinner;

    /**
     * 查找标记
     */
    private boolean searching = false;

    private BluetoothStateListener bluetoothStateListener;
    private BLEService bleService;
    private Handler handler = new Handler() {
        //
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case WHAT_FIND_BLE_TEXT:
                    TextView textView = (TextView) findViewById(R.id.findText);
                    textView.setText(MessageFormat.format(getString(R.string.scan_BLE_tips), msg.obj));
                    break;
                case BLEService.WHAT_FIND_BLE:
                    NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
                    Menu menu = navigationView.getMenu();

                    menu.add(Menu.NONE, msg.arg1, msg.arg2, msg.obj.toString()).setIcon(R.drawable.ic_bluetooth).setCheckable(true);
                    break;

                case BLEService.WHAT_BLE_CONNECTED://连接
                    toolbar.setLogo(R.drawable.ic_link_orange);
                    Toast.makeText(MainActivity.this, R.string.device_connect, Toast.LENGTH_SHORT).show();
                    break;
                case BLEService.WHAT_BLE_DISCONNECTED://断开连接
                    toolbar.setLogo(R.drawable.ic_link_gray);
                    Toast.makeText(MainActivity.this, R.string.device_disconnect, Toast.LENGTH_LONG).show();
                    break;
                case BLEService.WHAT_BLE_SERVICES_DISCOVER://服务发现
                    toolbar.setLogo(R.drawable.ic_link_green);
                    toolbar.setSubtitle(null);
                    break;
                case BLEService.WHAT_BLE_SERVICES_UNDISCOVER://服务未发现
                    toolbar.setSubtitle(R.string.device_unsupported);
                    toolbar.setLogo(R.drawable.ic_link_orange);
                    break;
                case BLEService.WHAT_BLE_DATA_RETURN_ERROR://返回数据格式错误
                    Toast.makeText(MainActivity.this, R.string.ble_return_data_error, Toast.LENGTH_LONG).show();
                    break;
                case BLEService.WHAT_BLE_DATA_RETURN://数据返回
                    ProtocolEntity pEntity = (ProtocolEntity) msg.obj;
                    switch (pEntity.getFunction()) {
                        case ProtocolEntity.FUNCTION_QUERY_STATUS:
                            int pwm = pEntity.getData(0);
                            //还需要进行判断
                            seekBarPWM.setProgress(100 - pwm);
                            byte temp_h = pEntity.getData(1);
                            byte temp_l = pEntity.getData(2);
                            String temp = Converts.temperature(temp_h, temp_l);
                            txtTemperature.setText(temp);

                            int p11 = pEntity.getData(3);
                            switchIO.setChecked(p11 == 0);
                            break;
                        case ProtocolEntity.FUNCTION_PWM:
                            Toast.makeText(MainActivity.this, R.string.pwm_changed, Toast.LENGTH_SHORT).show();
                            break;
                        case ProtocolEntity.FUNCTION_IO:
                            Toast.makeText(MainActivity.this, R.string.io_changed, Toast.LENGTH_SHORT).show();
                            break;
                        case ProtocolEntity.FUNCTION_TEMP_THRESHOLD:
                            Toast.makeText(MainActivity.this, R.string.temp_threshold_changed, Toast.LENGTH_SHORT).show();
                            break;
                        case ProtocolEntity.FUNCTION_BROADCAST:
                            Toast.makeText(MainActivity.this, R.string.broadcast_period_changed, Toast.LENGTH_SHORT).show();
                            break;
                    }

                    break;
                case BLEService.WHAT_BLE_SEND_DATA_ERROR:
                    ProtocolEntity command = (ProtocolEntity) msg.obj;
                    switch (command.getFunction()) {
                        case ProtocolEntity.FUNCTION_QUERY_STATUS:
                            Toast.makeText(MainActivity.this, R.string.status_query_fail, Toast.LENGTH_SHORT).show();
                            break;
                        case ProtocolEntity.FUNCTION_PWM:
                            Toast.makeText(MainActivity.this, R.string.pwm_change_fail, Toast.LENGTH_SHORT).show();
                            break;
                        case ProtocolEntity.FUNCTION_IO:
                            Toast.makeText(MainActivity.this, R.string.io_change_fail, Toast.LENGTH_SHORT).show();
                            break;
                        case ProtocolEntity.FUNCTION_TEMP_THRESHOLD:
                            Toast.makeText(MainActivity.this, R.string.temp_threshold_change_fail, Toast.LENGTH_SHORT).show();
                            break;
                        case ProtocolEntity.FUNCTION_BROADCAST:
                            Toast.makeText(MainActivity.this, R.string.broadcast_period_change_fail, Toast.LENGTH_SHORT).show();
                            break;
                    }
                    break;
            }
        }
    };

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "ServiceConnection onServiceConnected 方法被调用");

            //获取bleService
            bleService = ((BLEService.SimpleBinder) service).getService();
            //设置handler
            bleService.initHandler(handler);
            //启动一次蓝牙状态判断
            bluetoothStatusChanged(bleService.isBluetoothEnable());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "ServiceConnection onServiceDisconnected 被调用");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //设置标题
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        toolbar.setTitle(R.string.please_connect_device);
        toolbar.setLogo(R.drawable.ic_link_gray);


        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, R.string.bluetooth_closed, Snackbar.LENGTH_LONG)
                        .setAction(R.string.enable, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                final BluetoothManager bluetoothManager =
                                        (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                                BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();
                                if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                                    //开启蓝牙
                                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                                }
                            }
                        }).setActionTextColor(getResources().getColor(R.color.snackbar_action)).show();
            }
        });

        //先隐藏
        fab.setVisibility(INVISIBLE);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        //############# 控件 ###############
        txtTemperature = (TextView) findViewById(R.id.txt_temperature);
        txtPWM = (TextView) findViewById(R.id.txt_pwm);
        seekBarPWM = (SeekBar) findViewById(R.id.seekBar_pwm);
        seekBarPWM.setOnSeekBarChangeListener(this);
        txtMin = (TextView) findViewById(R.id.txt_min);
        txtMax = (TextView) findViewById(R.id.txt_max);
        seekBarMin = (SeekBar) findViewById(R.id.seekBar_min);
        seekBarMin.setOnSeekBarChangeListener(this);
        seekBarMax = (SeekBar) findViewById(R.id.seekBar_max);
        seekBarMax.setOnSeekBarChangeListener(this);
        spinner = (Spinner) findViewById(R.id.spinner);
        spinner.setOnItemSelectedListener(this);
        switchIO = (Switch) findViewById(R.id.switch_IO);
        switchIO.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    switchIO.setText(R.string.on);
                } else {
                    switchIO.setText(R.string.off);
                }

            }
        });

        switchIO.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bleService.sendChangeIOCommand(switchIO.isChecked());
            }
        });


        //########## 启动服务 ##########
        Intent intent = new Intent(this, BLEService.class);
        //startService(intent);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);

        //注册蓝牙状态监听
        bluetoothStateListener = new BluetoothStateListener();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothStateListener, filter);
    }


    @Override
    public void onBackPressed() {

        Log.d(TAG, "onBackPressed 方法执行");

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume 方法执行");
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy 方法执行");
        //取消注册蓝牙广播监听
        unregisterReceiver(bluetoothStateListener);

        //
        unbindService(serviceConnection);

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu 方法执行");

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        Log.d(TAG, "onOptionsItemSelected 方法执行");

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

    /**
     * 查找BLE设备
     *
     * @param view view
     */
    public void findBLE(View view) {
        Log.d(TAG, "查找蓝牙功能");

        //查找蓝牙的过程是否可以终止
        //调用取消方法

        if (searching) {//已经开始查找则点击无效
            return;
        }

        if (!bleService.isBluetoothEnable()) {
            //开启蓝牙
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        //开始查找
        searching = true;
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        Menu menu = navigationView.getMenu();
        //先清空，当前连接的应该除外？
        //应该有断开连接的功能？
        //可以连接多个设备，保存之前已经连接的？
        menu.clear();

        final ImageButton findBtn = (ImageButton) findViewById(R.id.findBLEBtn);

        float curTranslationX = findBtn.getTranslationX();
        //先取消背景
        findBtn.setBackground(null);
        //设置图片
        findBtn.setImageResource(R.drawable.ic_search2);
        ObjectAnimator animator = ObjectAnimator.ofFloat(findBtn, "translationX", curTranslationX, -300f, curTranslationX, 300f, curTranslationX);
        animator.setDuration(5000);
        //animator.setRepeatCount(2);
        animator.start();
        //倒计时
        handler.sendMessage(Message.obtain(null, WHAT_FIND_BLE_TEXT, "5"));
        handler.sendMessageDelayed(Message.obtain(null, WHAT_FIND_BLE_TEXT, "4"), 1000);
        handler.sendMessageDelayed(Message.obtain(null, WHAT_FIND_BLE_TEXT, "3"), 2000);
        handler.sendMessageDelayed(Message.obtain(null, WHAT_FIND_BLE_TEXT, "2"), 3000);
        handler.sendMessageDelayed(Message.obtain(null, WHAT_FIND_BLE_TEXT, "1"), 4000);

        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                Log.d(TAG, "Animation 开始");
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                Log.d(TAG, "Animation 结束");

                findBtn.setBackgroundResource(R.drawable.search_btn_shape_selector);
                findBtn.setImageResource(R.drawable.search_btn_src_selector);

                searching = false;

                //设置扫描到设备数量
                TextView textView = (TextView) findViewById(R.id.findText);
                textView.setText(MessageFormat.format(getString(R.string.scanning_device), bleService.getDeviceSize()));
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                Log.d(TAG, "Animation 取消");
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
                Log.d(TAG, "Animation 重复");
            }
        });

        //调用服务进行扫描
        bleService.scanLeDevice(true);

    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {

        Log.d(TAG, "onNavigationItemSelected 方法调用");

        int id = item.getItemId();

        Log.d(TAG, " 这个的title 是：" + item.getTitle() + " id: " + id);

        if(item.isChecked()){
            return false;
        }

        //设置标题
        toolbar.setTitle(item.getTitle());
        toolbar.setSubtitle(null);

        toolbar.setLogo(R.drawable.ic_link_orange);

        //连接到蓝牙设备
        bleService.connect2BLE(id);

        //**********************
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * 蓝牙状态改变
     *
     * @param beOn 运行
     */
    public void bluetoothStatusChanged(boolean beOn) {
        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        TextView textView = (TextView) findViewById(R.id.findText);

        if (beOn) {
            fab.setVisibility(INVISIBLE);
            textView.setText(R.string.bluetooth_enable);
        } else {
            fab.setVisibility(VISIBLE);
            textView.setText(R.string.bluetooth_closed);
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        Log.d(TAG, "seekBar ID = " + seekBar.getId() + "  progress = " + progress + "  fromUser = " + fromUser);
        switch (seekBar.getId()) {
            case R.id.seekBar_pwm:
                txtPWM.setText(progress + "");
                break;
            case R.id.seekBar_min:
                txtMin.setText(progress + "");
                if (progress > seekBarMax.getProgress()) {
                    seekBarMax.setProgress(progress);
                }
                break;
            case R.id.seekBar_max:
                txtMax.setText(progress + "");
                if (progress < seekBarMin.getProgress()) {
                    seekBarMin.setProgress(progress);
                }
                break;
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        Log.d(TAG, "onStartTrackingTouch " + seekBar.getId());
        switch (seekBar.getId()) {
            case R.id.seekBar_pwm:
                break;
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        Log.d(TAG, "onStopTrackingTouch " + seekBar.getId());
        int progress = seekBar.getProgress();
        switch (seekBar.getId()) {
            case R.id.seekBar_pwm:
                txtPWM.setText(progress + "");
                bleService.sendChangePWMCommand(100 - progress);
                break;
            case R.id.seekBar_min:
                txtMin.setText(progress + "");
                if (progress > seekBarMax.getProgress()) {
                    seekBarMax.setProgress(progress);
                }
                break;
            case R.id.seekBar_max:
                txtMax.setText(progress + "");
                if (progress < seekBarMin.getProgress()) {
                    seekBarMin.setProgress(progress);
                }
                break;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Log.d(TAG, "下拉列表事件 OnItemSelectedListener  position=" + position + " id=" + id);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    /**
     * 蓝牙状态
     */
    class BluetoothStateListener extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
            String msg = null;
            switch (state) {
                case BluetoothAdapter.STATE_TURNING_ON:
                    msg = "turning on";
                    break;
                case BluetoothAdapter.STATE_ON:
                    msg = "on";
                    bluetoothStatusChanged(true);
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    msg = "turning off";
                    break;
                case BluetoothAdapter.STATE_OFF:
                    msg = "off";
                    bluetoothStatusChanged(false);
                    break;
            }
            Log.i(TAG, "蓝牙状态改变：" + msg);
        }
    }
}


