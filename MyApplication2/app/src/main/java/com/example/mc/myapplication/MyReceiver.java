package com.example.mc.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.util.Log;

public class MyReceiver extends BroadcastReceiver {

    private static final int _productId = 0x3803;
    private static final int _vendorId = 0x40B;

    private final static String TAG = "MyReceiver";
    private String data;
    private static AudioManager mAudioManager;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "action: " + action);

        if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            UsbDevice devices = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            Log.d(TAG, "attached devices: " + devices);
            if(devices.getProductId() == _productId && devices.getVendorId() == _vendorId) {

                Intent intent_startService = new Intent(context, MyService.class);

                context.startService(intent_startService);
            }
        }
        else if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            UsbDevice devices = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            Log.d(TAG, "detached devices:" + devices);

            if(devices.getProductId() == _productId && devices.getVendorId() == _vendorId) {
                Intent intent_stopService = new Intent(context, MyService.class);
                context.stopService(intent_stopService);
            }
        }
        //am broadcast android.intent.action.DEF1 -f 0x1000000
        else if ("android.intent.action.DEF1".equals(action)) {

            mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            mAudioManager.setWiredDeviceConnectionState(AudioSystem.DEVICE_OUT_USB_HEADSET,
                    AudioSystem.DEVICE_STATE_AVAILABLE, "card=1;device=0;", "USB-Audio - Wireless headset");
        }
        else if ("android.intent.action.DEF2".equals(action)) {

            mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            mAudioManager.setWiredDeviceConnectionState(AudioSystem.DEVICE_OUT_USB_HEADSET,
                    AudioSystem.DEVICE_STATE_UNAVAILABLE, "card=1;device=0;", "USB-Audio - Wireless headset");
        }
    }
}
