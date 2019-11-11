package com.example.mc.myapplication;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    private static MyService ms;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        super.onStart();

        //HidTest ht = new HidTest();
        //ht.HidBridge(this, 0x3803, 0x40B);
        //ht.HidBridge(this, 130, 7247);
        //ht.OpenDevice();
        //ht.StartReadingThread();

        //Intent intent = new Intent(this, MyService.class);
        //startService(intent);


    }

    @Override
    protected void onStop() {
        super.onStop();
        //Intent intent =new Intent(this, MyService.class);
        //stopService(intent);
    }
}
