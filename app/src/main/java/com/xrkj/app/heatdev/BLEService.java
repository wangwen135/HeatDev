package com.xrkj.app.heatdev;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import com.xrkj.app.heatdev.exception.ProtocolException;
import com.xrkj.app.heatdev.protocol.ProtocolEntity;
import com.xrkj.app.heatdev.protocol.ProtocolUtil;
import com.xrkj.app.heatdev.utils.Converts;
import com.xrkj.app.heatdev.utils.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * 服务
 */
public class BLEService extends Service {

    public final static UUID UUID_MANAGE_SERVICE =
            UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");

    public final static UUID UUID_MANAGE_CHARACTERISTIC =
            UUID.fromString("0000fff6-0000-1000-8000-00805f9b34fb");

    public final static UUID UUID_NOTIFY_DESCRIPTOR =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /**
     * 发现BLE设备
     */
    public static final int WHAT_FIND_BLE = 3001;
    /**
     * 设备已连接
     */
    public static final int WHAT_BLE_CONNECTED = 3002;
    /**
     * 设备已断开连接
     */
    public static final int WHAT_BLE_DISCONNECTED = 3003;
    /**
     * 服务发现
     */
    public static final int WHAT_BLE_SERVICES_DISCOVER = 3004;
    /**
     * 服务未发现
     * 不支持该设备的意思
     */
    public static final int WHAT_BLE_SERVICES_UNDISCOVER = 3005;

    /**
     * 数据返回
     */
    public static final int WHAT_BLE_DATA_RETURN = 3006;

    /**
     * BLE返回数据格式错误
     */
    public static final int WHAT_BLE_DATA_RETURN_ERROR = 3007;

    /**
     * 发送数据失败
     */
    public static final int WHAT_BLE_SEND_DATA_ERROR = 3008;

    /**
     * 扫描时间5秒钟
     */
    public static final long SCAN_PERIOD = 5000;

    private static final String TAG = "BLEService";

    private SimpleBinder sBinder = new SimpleBinder();
    private Handler handler;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic characteristic;

    /**
     * 正在扫描
     */
    private boolean scanning;

    /**
     * 扫描到的设备列表
     */
    private List<BluetoothDevice> deviceList = new ArrayList<>();
    /**
     * 当前连接的设备索引
     */
    private int deviceIndex;
    /**
     * 当前连接的设备
     */
    private BluetoothDevice device;

    /**
     * 已经连接的
     */
    private boolean connected;

    /**
     * 能进行通信的
     */
    private boolean communication;

    /**
     * 查询状态间隔时间
     */
    private static final int QUERY_INTERVAL = 3000;
    /**
     * 发等待时间
     */
    private static final int SEND_WAIT_TIME = 3500;

    /**
     * 重试次数
     */
    private static final int SEND_RETRY_TIME = 2;

    //通过另外的线程发送指令
    private boolean threadRun = true;
    private Long lastTime = 0l;
    private Object sendLock = new Object();
    private Object waitLock = new Object();
    private ArrayBlockingQueue<ProtocolEntity> queue = new ArrayBlockingQueue<>(20);
    private Thread sendThread = new Thread() {
        @Override
        public void run() {
            int sendTime;
            boolean sendSuccess;
            //先发送操作命令，操作命令发送完成之后再发送查询命令
            while (threadRun) {
                //只负责消化队列中的命令
                ProtocolEntity command = getAndRemoveSameCommand();
                if (command != null) {
                    //发送命令，并等待一段时间
                    //如果发送失败了重试一次
                    sendTime = 0;

                    do {
                        sendSuccess = sendCommand2BLE(command);
                        sendTime++;
                        if (sendSuccess) {
                            Log.i(TAG, "线程写入命令到BLE，type = " + command.getFunction());
                            try {
                                synchronized (sendLock) {
                                    sendLock.wait(SEND_WAIT_TIME);
                                }
                            } catch (InterruptedException e) {
                                Log.e(TAG, "发送等待失败", e);
                            }
                        } else {
                            if (sendTime < SEND_RETRY_TIME) {
                                Log.w(TAG, "线程发送命令失败，300ms后重试");
                                try {
                                    Thread.sleep(300);
                                } catch (InterruptedException e) {
                                }
                            } else {
                                //通知UI发送失败
                                Log.e(TAG, "线程发送命令失败，通知UI");
                                handler.sendMessage(Message.obtain(null, WHAT_BLE_SEND_DATA_ERROR, command));
                            }
                        }
                    } while (!sendSuccess && sendTime < SEND_RETRY_TIME);

                } else {//队列中没命令了，判断是否需要发送查询命令
                    long cTime = System.currentTimeMillis();
                    if (cTime - lastTime >= QUERY_INTERVAL) {
                        lastTime = cTime;
                        sendGetStatusCommand();
                    } else {
                        //线程休眠一段时间，等待唤醒
                        try {
                            synchronized (waitLock) {
                                waitLock.wait(800);
                            }
                        } catch (InterruptedException e) {
                            Log.e(TAG, "线程休眠等待失败", e);
                        }
                    }
                }
            }
            Log.i(TAG, "线程停止运行");
        }

        /**
         * 获取最开始的命令，如果存在两个同样的命令则放弃之前的一个
         * @return
         */
        private ProtocolEntity getAndRemoveSameCommand() {
            ProtocolEntity command = queue.poll();
            if (command != null) {
                for (ProtocolEntity pe : queue) {
                    if (pe.getFunction() == command.getFunction()) {
                        //存在相同的命令，则放弃该条命令
                        Log.i(TAG, "//存在相同的命令，则放弃该条命令 = " + command.getFunction());
                        return getAndRemoveSameCommand();
                    }
                }
                return command;
            }
            return null;
        }
    };


    /**
     * 设备扫描回调
     */
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi,
                                     final byte[] scanRecord) {
                    Log.i(TAG, "发现设备：" + device.getName() + "   RSSI:" + rssi);

                    if (deviceList.contains(device)) {
                        return;
                    }

                    deviceList.add(device);

                    //通过handle 通知UI进行更新
                    int size = deviceList.size();
                    handler.sendMessage(Message.obtain(null, WHAT_FIND_BLE, size - 1, size, device.getName()));

                }
            };


    /**
     * Gatt回调
     */
    private final BluetoothGattCallback mGattCallback =
            new BluetoothGattCallback() {

                //当连接状态发生改变
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    Log.d(TAG, "onConnectionStateChange 触发");

                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.i(TAG, "蓝牙设备已经连接");

                        connected = true;

                        //搜索连接设备所支持的service
                        mBluetoothGatt.discoverServices();

                        handler.sendEmptyMessage(WHAT_BLE_CONNECTED);

                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.w(TAG, "蓝牙设备断开连接");

                        connected = false;
                        communication = false;

                        if (handler != null)
                            handler.sendEmptyMessage(WHAT_BLE_DISCONNECTED);
                    } else {
                        Log.w(TAG, "蓝牙设备连接其他状态：status = " + status + "  newState = " + newState);
                    }
                }

                // 发现新服务端
                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {

                    Log.d(TAG, "onServicesDiscovered 触发  发现新服务端");

                    if (status == BluetoothGatt.GATT_SUCCESS) {

                        try {
                            characteristic = mBluetoothGatt.getService(UUID_MANAGE_SERVICE).getCharacteristic(UUID_MANAGE_CHARACTERISTIC);

                            //启用通知
                            mBluetoothGatt.setCharacteristicNotification(characteristic, true);
                            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID_NOTIFY_DESCRIPTOR);
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            mBluetoothGatt.writeDescriptor(descriptor);


                            handler.sendEmptyMessage(WHAT_BLE_SERVICES_DISCOVER);

                        } catch (Exception e) {
                            communication = false;
                            Log.e(TAG, "与蓝牙设备通信失败", e);

                            handler.sendEmptyMessage(WHAT_BLE_SERVICES_UNDISCOVER);
                        }

                    } else {
                        Log.e(TAG, "状态错误 onServicesDiscovered received: " + status);
                        communication = false;

                        handler.sendEmptyMessage(WHAT_BLE_SERVICES_UNDISCOVER);
                    }
                }

                // 读写特性
                @Override
                public void onCharacteristicRead(BluetoothGatt gatt,
                                                 BluetoothGattCharacteristic characteristic,
                                                 int status) {

                    Log.d(TAG, "onCharacteristicRead 触发");

                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.w(TAG, "characteristic 有数据可读？" + characteristic);
                    }
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

                    communication = true;

                    byte[] value = characteristic.getValue();

                    if (Log.logLevel <= android.util.Log.DEBUG)
                        Log.d(TAG, "onCharacteristicChanged 触发  数据是： " + Converts.bytesToHexString(value));

                    try {
                        ProtocolEntity protocolEntity = ProtocolUtil.resolve(value);
                        Log.d(TAG, "onCharacteristicChanged 解析成对象是： " + protocolEntity);

                        handler.sendMessage(Message.obtain(null, WHAT_BLE_DATA_RETURN, protocolEntity));

                    } catch (ProtocolException e) {
                        Log.e(TAG, "返回参数解析异常", e);
                        handler.sendEmptyMessage(WHAT_BLE_DATA_RETURN_ERROR);
                    }

                    //唤醒发送等待
                    synchronized (sendLock) {
                        sendLock.notify();
                    }

                }

                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

                    //写操作的结果
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if (Log.logLevel <= android.util.Log.DEBUG)
                            Log.d(TAG, "onCharacteristicWrite 写入操作成功，写入的数据是： " + Converts.bytesToHexString(characteristic.getValue()));
                    } else {
                        Log.w(TAG, "onCharacteristicWrite 写入操作失败，状态是：" + status);
                    }

                }

                @Override
                public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    Log.d(TAG, "onDescriptorWrite 触发");
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.i(TAG, "通知开启成功，可以进行通信了");
                        communication = true;

                        //发送一个查询命令
                        sendGetStatusCommand();
                    }
                }
            };


    /**
     * 扫描BLE设备
     *
     * @param enable true 扫描，fasle 停止扫描
     */
    public void scanLeDevice(boolean enable) {
        if (enable) {
            // 在预定义的扫描时间周期后停止扫描.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            //清空列表
            deviceList.clear();
            scanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            scanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    /**
     * 是否正在扫描
     *
     * @return
     */
    public boolean isScaning() {
        return scanning;
    }

    /**
     * 获取BLE设备数量
     *
     * @return
     */
    public int getDeviceSize() {
        return deviceList.size();
    }

    /**
     * 获取设备列表
     *
     * @return
     */
    public List<BluetoothDevice> getDeviceList() {
        return deviceList;
    }

    /**
     * 发送命令到BLE设备
     *
     * @param command
     * @return
     */
    public boolean sendCommand2BLE(ProtocolEntity command) {
        if (!connected || !communication) {
            return false;
        }
        byte[] bytes = ProtocolUtil.calcFill(command);

        if (!characteristic.setValue(bytes)) {
            return false;
        }

        return mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * 将命令放入队列中
     *
     * @param command
     */
    public void sendCommand2Queue(ProtocolEntity command) {
        if (!connected || !communication) {
            return;
        }

        queue.offer(command);

        synchronized (waitLock) {
            waitLock.notify();//唤醒线程
        }
    }

    /**
     * 发送获取状态的指令
     */
    public void sendGetStatusCommand() {
        ProtocolEntity pEntity = new ProtocolEntity(ProtocolEntity.FUNCTION_QUERY_STATUS);

        Log.d(TAG, "发送获取状态的命令到队列中");

        sendCommand2Queue(pEntity);
    }

    /**
     * 发送改变IO状态的命令
     *
     * @param enable
     */
    public void sendChangeIOCommand(boolean enable) {

        ProtocolEntity pEntity = new ProtocolEntity(ProtocolEntity.FUNCTION_IO, (byte) (enable ? 0x00 : 0x01));

        Log.d(TAG, "发送改变IO状态命令到队列中");

        sendCommand2Queue(pEntity);
    }

    /**
     * 发送改变PWM的命令
     *
     * @param pwm
     */
    public void sendChangePWMCommand(int pwm) {
        ProtocolEntity pEntity = new ProtocolEntity(ProtocolEntity.FUNCTION_PWM, (byte) pwm);

        Log.d(TAG, "发送改变PWM命令到队列中");

        sendCommand2Queue(pEntity);
    }


    /**
     * 断开之前的连接
     * 连接Gatt
     * 获取characteristic
     *
     * @param id
     */
    public void connect2BLE(int id) {

        if (device != null && connected) {
            BluetoothDevice _device = deviceList.get(id);
            if (device.equals(_device)) {
                Log.w(TAG, "同一个设备，不重连");
                return;
            }
        }

        //不管怎样先断开之前的连接
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            Log.w(TAG, "先断开之前的蓝牙连接");
        }
        deviceIndex = id;
        device = deviceList.get(id);
        //断开连接
        connected = false;
        communication = false;
        mBluetoothGatt = device.connectGatt(this, true, mGattCallback);

    }

    /**
     * 蓝牙是否正常
     *
     * @return true 正常
     */
    public boolean isBluetoothEnable() {
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }


    /**
     * 设置一个handler，用于处理消息
     *
     * @param handler
     */
    public void initHandler(Handler handler) {
        this.handler = handler;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        //启动发送线程
        sendThread.start();

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand 方法被调用");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        threadRun = false;
        synchronized (waitLock) {
            waitLock.notify();
        }
        Log.i(TAG, "onDestroy 方法被调用，释放蓝牙资源");
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
        }

        super.onDestroy();
    }


    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind 被调用了");
        return sBinder;
    }

    public class SimpleBinder extends Binder {
        public BLEService getService() {
            return BLEService.this;
        }
    }
}
