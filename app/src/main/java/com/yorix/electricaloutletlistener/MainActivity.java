package com.yorix.electricaloutletlistener;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TimePicker;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private Intent intent;
    private ImageView serviceStatusImageView, powerStatusImageView;
    private BroadcastReceiver acOnOffReceiver;
    private DatePicker datePicker;
    private TimePicker timePicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        intent = new Intent(this, ElectricalOutletListenerService.class);
        serviceStatusImageView = findViewById(R.id.imageViewServiceStatus);
        powerStatusImageView = findViewById(R.id.imageViewPowerStatus);
        datePicker = findViewById(R.id.date_picker);
        timePicker = findViewById(R.id.time_picker);


        addToWhiteList();

        timePicker.setIs24HourView(true);


        acOnOffReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int status = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                setPowerStatusColor(status);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkServiceStatus();

        registerReceiver(acOnOffReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(acOnOffReceiver);
    }

    @SuppressLint("BatteryLife")
    private void addToWhiteList() {
        Intent intent = new
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void setPowerStatusColor(int status) {
        if (status == BatteryManager.BATTERY_PLUGGED_AC)
            powerStatusImageView.setBackgroundResource(R.color.green);
        else
            powerStatusImageView.setBackgroundResource(R.color.red);
    }

    private void checkServiceStatus() {
        ActivityManager am = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> rs = am.getRunningServices(50);

        for (int i = 0; i < rs.size(); i++)
            if (rs.get(i).service.getClassName().equals(ElectricalOutletListenerService.class.getName())) {
                serviceStatusImageView.setBackgroundResource(R.color.green);
                return;
            }
        serviceStatusImageView.setBackgroundResource(R.color.red);
    }

    public void onClickStart(View view) {
        startForegroundService(intent);
        checkServiceStatus();
    }

    public void onClickStop(View view) {
        stopService(intent);
        checkServiceStatus();
    }

    public void onClickSetTime(View view) {
        Calendar calendar = Calendar.getInstance();
        String stringSeconds = ((EditText) findViewById(R.id.time_seconds)).getText().toString();
        int seconds = stringSeconds.isEmpty() ||
                Integer.parseInt(stringSeconds) < 0 ||
                Integer.parseInt(stringSeconds) > 59
                ? 0 : Integer.parseInt(stringSeconds);

        calendar.set(
                datePicker.getYear(),
                datePicker.getMonth(),
                datePicker.getDayOfMonth(),
                timePicker.getHour(),
                timePicker.getMinute(),
                seconds);

        String time = String.valueOf(calendar.getTimeInMillis());
        intent.putExtra("time", time);
        startService(intent);
        checkServiceStatus();
    }
}
