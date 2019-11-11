package com.example.mc.myapplication;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class MyService extends Service {

    private final static String TAG = "MyService";
    private static UsbManager _usbManager;
    private static AudioManager mAudioManager;
    private static Context _context;

    private static final int _productId = 0x3803;//Haier 2.4G headphone
    private static final int _vendorId = 0x40B;
    private static final int HP_ONOFF_MASK = 0x80;

    private Object _locker = new Object();

    // intent action to start hidraw device data check
    private final static String ACTION_OPEN_HIDDEVICE = "ss";

    // Can be used for debugging.
    @SuppressWarnings("unused")
    //private HidBridgeLogSupporter _logSupporter = new HidBridgeLogSupporter();
    private static final String ACTION_USB_PERMISSION =
            "com.example.company.app.testhid.USB_PERMISSION";

    private Thread _readingThread = null;
    private String _deviceName;

    private UsbDevice _usbDevice = null;
    private UsbEndpoint mUsbEndpointIn;

    // The queue that contains the read data.
    private Queue<byte[]> _receivedQueue;

    public MyService() {
        Log.d(TAG, "construct hidraw read service");
        _receivedQueue = new LinkedList<byte[]>();
        _context = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "start hidraw read service");

        //OpenDevice();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        //_productId = intent.getIntExtra("pid", 0);
        //_vendorId = intent.getIntExtra("vid", 0);
        Log.d(TAG, "onStartCommand, pid:" + _productId + " vid:" + _vendorId);

        startReadingThread();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "stop hidraw read service");
        stopReadingThread();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
    /**
     * Starts the thread that continuously reads the data from the device.
     * Should be called in order to be able to talk with the device.
     */
    private void startReadingThread() {
        if (_readingThread == null) {
            _readingThread = new Thread(readerReceiver);
            _readingThread.start();
        } else {
            Log.d(TAG, "Reading thread already started");
        }
    }

    // The thread that continuously receives data from the dongle and put it to the queue.
    private Runnable readerReceiver = new Runnable() {
        public void run() {

            UsbDeviceConnection readConnection = null;
            UsbInterface readIntf = null;
            boolean readerStartedMsgWasShown = false;

            // We will continuously ask for the data from the device and store it in the queue.
            while (true) {
                // Lock that is common for read/write methods.
                synchronized (_locker) {
                    try
                    {
                        if (_usbDevice == null) {
                            if (false == OpenDevice()) {
                                Log.d(TAG, "No device. Recheking in 10 sec...");
                                Sleep(10000);
                            }
                            continue;
                        }

                        int interfaceCount = _usbDevice.getInterfaceCount();
                        Log.v(TAG, "interfaceCount: " + interfaceCount);

                        for (int interfaceIndex = 0; interfaceIndex < interfaceCount; interfaceIndex++) {
                            UsbInterface usbInterface = _usbDevice.getInterface(interfaceIndex);
                            Log.v(TAG, "interface[" + interfaceIndex + "] =" + usbInterface.getInterfaceClass());
                            if (UsbConstants.USB_CLASS_HID != usbInterface.getInterfaceClass()) {
                                continue;
                            }

                            for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                                UsbEndpoint ep = usbInterface.getEndpoint(i);
                                Log.v(TAG, "endPoint[" + i + "] =" + ep.getType() + "direction[" + i + "] =" + ep.getDirection());

                                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT && ep.getDirection() == UsbConstants.USB_DIR_IN) {
                                    mUsbEndpointIn = ep;
                                }

                            }

                            if (null == mUsbEndpointIn) {
                                Log.d(TAG, "endpoint is null\n");
                                mUsbEndpointIn = null;
                                readIntf = null;
                            } else {
                                Log.v(TAG, "\nendpoint in: " + mUsbEndpointIn + ",endpoint in: " + mUsbEndpointIn.getAddress()+"\n");
                                readIntf = usbInterface;
                                break;
                            }

                        }

                        try
                        {
                            readConnection = _usbManager.openDevice(_usbDevice);

                            if (readConnection == null) {
                                Log.d(TAG, "Cannot start reader because the user didn't gave me permissions or the device is not present. Retrying in 2 sec...");
                                Sleep(2000);
                                continue;
                            }

                            // Claim and lock the interface in the android system.
                            readConnection.claimInterface(readIntf, true);
                        }
                        catch (SecurityException e) {
                            Log.d(TAG, "Cannot start reader because the user didn't gave me permissions. Retrying in 2 sec...");

                            Sleep(2000);
                            continue;
                        }

                        // Show the reader started message once.
                        if (!readerStartedMsgWasShown) {
                            Log.d(TAG, "!!! Reader was started !!!");
                            readerStartedMsgWasShown = true;
                        }

                        // Read the data as a bulk transfer with the size = MaxPacketSize
                        int packetSize = mUsbEndpointIn.getMaxPacketSize();

                        UsbRequest req = new UsbRequest();
                        req.initialize(readConnection, mUsbEndpointIn);

                        ByteBuffer buffer = ByteBuffer.allocate(packetSize);

                        boolean ret = req.queue(buffer);
                        if (readConnection.requestWait() == req) {

                            byte[] b = new byte[packetSize];
                            buffer.rewind();
                            int i;
                            for(i=0; i<packetSize; i++) {
                                //Log.d(TAG, "position: " + buffer.position() );
                                b[i] = buffer.get();
                                Log.d(TAG, "Message received  content: " + Integer.toHexString(b[i]));
                            }

                            Log.d(TAG, "content: " + Arrays.toString(b) );

                            if ( ((int)(b[0]) & HP_ONOFF_MASK) == 0x80  ||
                                    ((int)(b[1]) & HP_ONOFF_MASK) == 0x80 ||
                                    ((int)(b[2]) & HP_ONOFF_MASK) == 0x80 ||
                                    ((int)(b[3]) & HP_ONOFF_MASK) == 0x80) {
                                mAudioManager = (AudioManager) _context.getSystemService(Context.AUDIO_SERVICE);
                                mAudioManager.setWiredDeviceConnectionState(AudioSystem.DEVICE_OUT_USB_HEADSET,
                                        AudioSystem.DEVICE_STATE_AVAILABLE, "card=1;device=0;", "USB-Audio - Wireless headset");
                            }
                            else {
                                mAudioManager = (AudioManager) _context.getSystemService(Context.AUDIO_SERVICE);
                                mAudioManager.setWiredDeviceConnectionState(AudioSystem.DEVICE_OUT_USB_HEADSET,
                                        AudioSystem.DEVICE_STATE_UNAVAILABLE, "card=1;device=0;", "USB-Audio - Wireless headset");
                            }
                        }

                        // Release the interface lock.
                        readConnection.releaseInterface(readIntf);
                        readConnection.close();
                    }

                    catch (NullPointerException e) {
                        Log.d(TAG, "Error happened while reading. No device or the connection is busy");
                        Log.e("HidBridge", Log.getStackTraceString(e));
                    }
                    catch (ThreadDeath e) {
                        if (readConnection != null) {
                            readConnection.releaseInterface(readIntf);
                            readConnection.close();
                        }

                        throw e;
                    }
                }

                // Sleep for 10 ms to pause, so other thread can write data or anything.
                // As both read and write data methods lock each other - they cannot be run in parallel.
                // Looks like Android is not so smart in planning the threads, so we need to give it a small time
                // to switch the thread context.
                //Sleep(10000);
            }
        }
    };


    private boolean OpenDevice() {
        _usbManager = (UsbManager) _context.getSystemService(Context.USB_SERVICE);

        HashMap<String, UsbDevice> deviceList = _usbManager.getDeviceList();

        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

        // Iterate all the available devices and find ours.
        while(deviceIterator.hasNext()){
            UsbDevice device = deviceIterator.next();

            Log.d(TAG, "DeviceName="+device.getDeviceName()+
                        "DeviceId="+device.getDeviceId()+
                        "VendorId="+device.getVendorId()+
                        "ProductId="+device.getProductId()+
                        "DeviceClass="+device.getDeviceClass()+"\n");

            if (device.getProductId() == _productId && device.getVendorId() == _vendorId) {
                _usbDevice = device;
                _deviceName = _usbDevice.getDeviceName();
            }
        }

        if (_usbDevice == null) {
            Log.d(TAG, "Cannot find the device. Did you forgot to plug it?");
            return false;
        }

        if (!_usbManager.hasPermission(_usbDevice)) {
            // Create and intent and request a permission.
            PendingIntent mPermissionIntent = PendingIntent.getBroadcast(_context, 0, new Intent(ACTION_USB_PERMISSION), 0);
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            _context.registerReceiver(mUsbReceiver, filter);

            _usbManager.requestPermission(_usbDevice, mPermissionIntent);
        }
        Log.d(TAG, "Found the device");
        return true;
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //call method to set up device communication
                        }
                    }
                    else {
                        Log.d("TAG", "permission denied for the device " + device);
                    }
                }
            }
        }
    };

    private void stopReadingThread() {
        if (_readingThread != null) {
            // Just kill the thread. It is better to do that fast if we need that asap.
            _readingThread.stop();
            _readingThread = null;
        } else {
            Log.d(TAG, "No reading thread to stop");
        }
    }

    private void Sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
/*
    private void requestUserPermission() {
        Intent intent = new Intent();
        intent.setAction(ACTION_USB_PERMISSION);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mReceiver, filter);
        // Request permission
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            intent.putExtra(UsbManager.EXTRA_DEVICE, device);
            intent.putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true);
            final PackageManager pm = getPackageManager();
            try {
                ApplicationInfo aInfo = pm.getApplicationInfo(getPackageName(),
                        0);
                try {
                    IBinder b = ServiceManager.getService(USB_SERVICE);
                    IUsbManager service = IUsbManager.Stub.asInterface(b);
                    service.grantDevicePermission(device, aInfo.uid);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            sendBroadcast(intent);  //伪装授权成功代码之后，再发送一条广播
        }
*/
    }
