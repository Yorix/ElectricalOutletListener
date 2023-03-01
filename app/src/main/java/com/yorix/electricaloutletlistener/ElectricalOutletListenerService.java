package com.yorix.electricaloutletlistener;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class ElectricalOutletListenerService extends Service {
    private final IO io = new IO(this);
    private ArrayList<String[]> keyboard;
    private String keyboardFilename;
    private String jsRequestsFilename;
    private String userListFilename;
    private String catListFilename;
    private String messagesFilename;
    private String stickerListFilename;
    private String scheduleImageFilename;
    private String wrapperHtmlFilename;
    private String imageUrl;
    private String imageAcOn;
    private String imageAcOff;
    private String messageAcOn;
    private String messageAcOff;
    private int acStatus;
    private SimpleDateFormat dateFormat;
    private String timeString;
    private String messageAcOnOff;
    private long adminId;
    private HashSet<Long> userList;
    private ArrayList<String> catList;
    private ArrayList<String> stickerList;
    private TelegramBot telegramBot;
    private int batteryLevel;
    private Handler handler;
    private long startCheckScheduleDelay;
    private long checkScheduleDelay;
    private long scheduleInputDelay;
    private String scheduleUrl;
    private String tableParentId;
    private String cellClass;
    private final Date date = new Date();
    private final CheckSchedule checkSchedule = new CheckSchedule();
    private final AcReceiver acReceiver = new AcReceiver();
    private final FailListHandler failListHandler = new FailListHandler();
    private final UpdateHandler updateHandler = new UpdateHandler();


    @SuppressLint("SimpleDateFormat")
    @Override
    public void onCreate() {
        super.onCreate();

        sendNotification();

        telegramBot = new TelegramBot(getString(R.string.bot_token));
        adminId = Long.parseLong(getString(R.string.admin_id));
        dateFormat = new SimpleDateFormat(getString(R.string.date_format));
        imageAcOn = getString(R.string.image_ac_on);
        imageAcOff = getString(R.string.image_ac_off);
        messageAcOn = getString(R.string.message_ac_on);
        messageAcOff = getString(R.string.message_ac_off);
        scheduleUrl = getString(R.string.schedule_url);
        tableParentId = getString(R.string.table_wrapper_id);
        cellClass = getString(R.string.cell_class);
        startCheckScheduleDelay = io.getMs(getString(R.string.start_check_schedule_delay));
        checkScheduleDelay = io.getMs(getString(R.string.check_schedule_delay));
        scheduleInputDelay = io.getMs(getString(R.string.schedule_input_delay));
        userListFilename = getString(R.string.user_list_filename);
        catListFilename = getString(R.string.cat_list_filename);
        stickerListFilename = getString(R.string.sticker_list_filename);
        messagesFilename = getString(R.string.messages_filename);
        scheduleImageFilename = getString(R.string.schedule_image_filename);
        jsRequestsFilename = getString(R.string.js_requests_filename);
        keyboardFilename = getString(R.string.keyboard_filename);
        wrapperHtmlFilename = getString(R.string.wrapper_html_filename);

        keyboard = io.readFromFileToListStringArray(keyboardFilename);
        userList = io.readFromFileToSetLong(userListFilename);
        catList = io.readFromFileToListString(catListFilename);
        stickerList = io.readFromFileToListString(stickerListFilename);

        handler = new Handler();
        handler.postDelayed(checkSchedule, startCheckScheduleDelay);

        registerReceiver(checkSchedule, new IntentFilter("checkSchedule"));
        registerReceiver(acReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        registerReceiver(failListHandler, new IntentFilter("failList"));

        telegramBot.setUpdatesListener(updates -> {
            if (date.getTime() == new Date(0).getTime())
                io.readTime(date);
            timeString = dateFormat.format(date);
            for (Update update : updates)
                updateHandler.handle(update);
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.hasExtra("time")) {
            date.setTime(Long.parseLong(intent.getStringExtra("time")));
            io.saveTime(date);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        io.saveUsers(userList);
        io.saveTime(date);
        telegramBot.removeGetUpdatesListener();
        telegramBot.shutdown();
        unregisterReceiver(acReceiver);
        unregisterReceiver(failListHandler);
        stopSelf();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private void updateAcStatus(boolean acOn) {
        if (acOn) {
            messageAcOnOff = messageAcOn;
            imageUrl = imageAcOn;
        } else {
            messageAcOnOff = messageAcOff;
            imageUrl = imageAcOff;
        }
    }

    private void sendResponse(boolean isOk) {
        startService(new Intent(this, SendMessageService.class)
                .putExtra("keyboard", keyboard)
                .putExtra("userList", new HashSet<>(Collections.singleton(adminId)))
                .putExtra("message", isOk ? getString(R.string.done) : getString(R.string.fail)));
    }

    private void sendNotification() {
        String CHANNEL_ID = "channel_01";

        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "EOL service", NotificationManager.IMPORTANCE_DEFAULT);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Telegram bot")
                .setSmallIcon(R.drawable.bulb_on)
                .build();

        startForeground(1, notification);
    }


    private class CheckSchedule extends BroadcastReceiver implements Runnable {

        @Override
        public void onReceive(Context context, Intent intent) {
            run();
        }

        @Override
        public void run() {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            final KeyguardManager.KeyguardLock kl = km.newKeyguardLock("MyKeyguardLock");
            kl.disableKeyguard();

            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            @SuppressLint("InvalidWakeLockTag")
            PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
                    | PowerManager.ON_AFTER_RELEASE, "MyWakeLock");
            wakeLock.acquire();

            try {
                startActivity(new Intent(ElectricalOutletListenerService.this, WebActivity.class)
                        .putExtra("scheduleUrl", scheduleUrl)
                        .putExtra("tableParentId", tableParentId)
                        .putExtra("cellClass", cellClass)
                        .putExtra("delay", scheduleInputDelay)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } finally {
                handler.postDelayed(checkSchedule, checkScheduleDelay);
            }
        }
    }

    private class AcReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);

            boolean acOn = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) == BatteryManager.BATTERY_PLUGGED_AC;
            updateAcStatus(acOn);

            int newAcStatus = acOn ? 1 : -1;

            if (acStatus == 0) {
                acStatus = newAcStatus;
                io.readTime(date);
            }

            if (newAcStatus != acStatus) {
                acStatus = newAcStatus;
                date.setTime(System.currentTimeMillis());
                io.saveTime(date);
                timeString = dateFormat.format(date);

                startService(new Intent(ElectricalOutletListenerService.this, SendMessageService.class)
                        .putExtra("keyboard", keyboard)
                        .putExtra("userList", userList)
                        .putExtra("caption", messageAcOnOff.concat(timeString))
                        .putExtra("imageUrl", imageUrl));
            }
        }
    }

    private class FailListHandler extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            String caption = intent.getStringExtra("caption");
            String imageUrl = intent.getStringExtra("imageUrl");
            String sticker = intent.getStringExtra("sticker");
            String filename = intent.getStringExtra("filename");
            HashSet<Long> failList = new HashSet<>((Set<Long>) intent.getSerializableExtra("failList"));
            startService(new Intent(ElectricalOutletListenerService.this, SendMessageService.class)
                    .putExtra("keyboard", keyboard)
                    .putExtra("userList", failList)
                    .putExtra("message", message)
                    .putExtra("caption", caption)
                    .putExtra("imageUrl", imageUrl)
                    .putExtra("sticker", sticker)
                    .putExtra("filename", filename));
        }
    }

    private class UpdateHandler {

        public void handle(Update update) {
            if (update.message() == null) return;

            if (update.message().text() != null)
                textRequest(update.message());

            if (update.message().document() != null)
                documentRequest(update.message());

            if (update.message().photo() != null)
                photoRequest(update.message());

            if (update.message().sticker() != null)
                stickerRequest(update.message());
        }

        private void stickerRequest(Message message) {
            long userId = parseMessage(message);

            String sticker;
            if (stickerList.size() < 12)
                sticker = getString(R.string.sticker_default);
            else
                sticker = stickerList.get(Calendar.getInstance().get(Calendar.MONTH));

            startService(new Intent(ElectricalOutletListenerService.this, SendMessageService.class)
                    .putExtra("keyboard", keyboard)
                    .putExtra("userList", new HashSet<>(Collections.singleton(userId)))
                    .putExtra("sticker", sticker));
        }

        private void photoRequest(Message message) {
            long userId = parseMessage(message);
            if (userId != adminId) return;

            boolean result = io.pushScheduleImage(message.photo(), scheduleImageFilename);
            sendResponse(result);
        }

        private void documentRequest(Message message) {
            long userId = parseMessage(message);
            if (userId != adminId) return;

            boolean result = false;
            if (message.document().fileName().equals(userListFilename)
                    || message.caption() != null && message.caption().toLowerCase().matches(getString(R.string.user_caption))) {
                result = io.pushDocument(message.document().fileId(), userListFilename);
                userList = io.readFromFileToSetLong(userListFilename);
            } else if (message.document().fileName().equals(catListFilename)
                    || message.caption() != null && message.caption().toLowerCase().matches(getString(R.string.cat_caption))) {
                result = io.pushDocument(message.document().fileId(), catListFilename);
                catList = io.readFromFileToListString(catListFilename);
            } else if (message.document().fileName().equals(stickerListFilename)
                    || message.caption() != null && message.caption().toLowerCase().matches(getString(R.string.sticker_caption))) {
                result = io.pushDocument(message.document().fileId(), stickerListFilename);
                stickerList = io.readFromFileToListString(stickerListFilename);
            } else if (message.document().fileName().equals(keyboardFilename)
                    || message.caption() != null && message.caption().toLowerCase().matches(getString(R.string.keyboard_caption))) {
                result = io.pushDocument(message.document().fileId(), keyboardFilename);
                keyboard = io.readFromFileToListStringArray(keyboardFilename);
            } else if (message.document().fileName().equals(jsRequestsFilename)
                    || message.caption() != null && message.caption().toLowerCase().matches(getString(R.string.js_caption))) {
                result = io.pushDocument(message.document().fileId(), jsRequestsFilename);
            } else if (message.document().fileName().equals(wrapperHtmlFilename)
                    || message.caption() != null && message.caption().toLowerCase().matches(getString(R.string.html_caption))) {
                result = io.pushDocument(message.document().fileId(), wrapperHtmlFilename);
            }

            sendResponse(result);
        }

        private void textRequest(Message message) {
            String request = message.text();
            String url = imageUrl;
            long userId = parseMessage(message);

            Intent sendMessage = new Intent(ElectricalOutletListenerService.this, SendMessageService.class)
                    .putExtra("keyboard", keyboard)
                    .putExtra("userList", new HashSet<>(Collections.singleton(userId)))
                    .putExtra("caption", messageAcOnOff.concat(timeString))
                    .putExtra("imageUrl", url);

            if (message.from().id() == adminId)
                commandHandler(request, sendMessage);

            if (request.toLowerCase().matches(getString(R.string.show_pussy)))
                sendMessage.putExtra("imageUrl", catList.get((int) (Math.random() * catList.size())));

            if (request.toLowerCase().matches(getString(R.string.schedule)))
                sendMessage.putExtra("imageFilename", scheduleImageFilename);

            startService(sendMessage);
        }

        private void commandHandler(String command, Intent sendMessage) {
            boolean result;
            switch (command.toLowerCase()) {
                case "$msg":
                    sendMessage.putExtra("filename", messagesFilename);
                    break;
                case "$msgdlt":
                    result = io.fileDelete(messagesFilename);
                    sendMessage.putExtra("message", result ? getString(R.string.done) : getString(R.string.fail));
                    break;
                case "$usr":
                    sendMessage.putExtra("filename", userListFilename);
                    break;
                case "$usrnum":
                    sendMessage.putExtra("message", String.valueOf(userList.size()));
                    break;
                case "$usrsv":
                    result = io.saveUsers(userList);
                    sendMessage.putExtra("message", result ? getString(R.string.done) : getString(R.string.fail));
                    break;
                case "$log":
                    sendMessage.putExtra("filename", getString(R.string.log_filename));
                    break;
                case "$logdlt":
                    result = io.fileDelete(getString(R.string.log_filename));
                    sendMessage.putExtra("message", result ? getString(R.string.done) : getString(R.string.fail));
                    break;
                case "$btr":
                    sendMessage.putExtra("message", batteryLevel + " %");
                    break;
                default: {
                    if (command.toLowerCase().matches("^\\$\\d+\\s[\\w\\W]*")) {
                        long chatId = Long.parseLong(command.substring(1).split("\\s")[0]);
                        String answer = command.replaceFirst("^\\$\\d+\\s", "");

                        sendMessage
                                .putExtra("userList", new HashSet<>(Collections.singleton(chatId)))
                                .putExtra("message", answer);
                        break;
                    }
                    if (command.toLowerCase().matches("^\\$all[\\w\\W]*")) {
                        String answer = command.replaceFirst("^\\$all", "");

                        sendMessage
                                .putExtra("userList", userList)
                                .putExtra("message", answer);
                        break;
                    }
                    if (command.toLowerCase().matches("^\\$aconimg\\s*https?://\\S+")) {
                        imageAcOn = command.replaceFirst("^[\\s\\S]+(?=https?://)", "");
                        updateAcStatus(acStatus == 1);
                        sendMessage.putExtra("message", getString(R.string.done));
                        break;
                    }
                    if (command.toLowerCase().matches("^\\$acoffimg\\s*https?://\\S+")) {
                        imageAcOff = command.replaceFirst("^[\\s\\S]+(?=https?://)", "");
                        updateAcStatus(acStatus == 1);
                        sendMessage.putExtra("message", getString(R.string.done));
                        break;
                    }
                    if (command.toLowerCase().matches("^\\$aconmsg\\s+[\\w\\W]*")) {
                        messageAcOn = command
                                .replaceFirst("^\\S+\\s+", "")
                                .replace("\\n", "\n");
                        updateAcStatus(acStatus == 1);
                        sendMessage.putExtra("message", getString(R.string.done));
                        break;
                    }
                    if (command.toLowerCase().matches("^\\$acoffmsg\\s+[\\w\\W]*")) {
                        messageAcOff = command
                                .replaceFirst("^\\S+\\s+", "")
                                .replace("\\n", "\n");
                        updateAcStatus(acStatus == 1);
                        sendMessage.putExtra("message", getString(R.string.done));
                        break;
                    }
                    if (command.toLowerCase().matches("^\\$timeptn\\s[\\w\\W]*")) {
                        String pattern = command
                                .replaceFirst("^\\S+\\s", "")
                                .replace("\\n", "\n");
                        try {
                            dateFormat.applyPattern(pattern);
                        } catch (IllegalArgumentException e) {
                            e.printStackTrace();
                            dateFormat.applyPattern(getString(R.string.date_format));
                        }
                        sendMessage.putExtra("message", getString(R.string.done));
                        break;
                    }
                    if (command.toLowerCase().matches("^\\$startchkdelay\\s*\\d{1,8}[smhdwy]?$")) {
                        String delay = command.replaceAll("^\\D+", "");
                        startCheckScheduleDelay = io.getMs(delay);
                        handler.removeCallbacks(checkSchedule);
                        handler.postDelayed(checkSchedule, startCheckScheduleDelay);
                        sendMessage.putExtra("message", getString(R.string.done));
                        break;
                    }
                    if (command.toLowerCase().matches("^\\$chkdelay\\s*\\d{1,8}[smhdwy]?$")) {
                        String delay = command.replaceAll("^\\D+", "");
                        checkScheduleDelay = io.getMs(delay);
                        sendMessage.putExtra("message", getString(R.string.done)
                                .concat(getString(R.string.describe_check_schedule_command)));
                        break;
                    }
                    if (command.toLowerCase().matches("^\\$inputdelay\\s*\\d{1,8}[smhdwy]?$")) {
                        String delay = command.replaceAll("^\\D+", "");
                        scheduleInputDelay = io.getMs(delay);
                        sendMessage.putExtra("message", getString(R.string.done)
                                .concat(getString(R.string.describe_check_schedule_command)));
                        break;
                    }
                    if (command.toLowerCase().matches("^\\$schdlurl\\s*https?://\\S+")) {
                        scheduleUrl = command.replaceFirst("[\\s\\S]+(?=https?://)", "");
                        sendMessage.putExtra("message", getString(R.string.done)
                                .concat(getString(R.string.describe_check_schedule_command)));
                        break;
                    }
                    if (command.toLowerCase().matches("^\\$tblid\\s*\\S+")) {
                        tableParentId = command.replaceFirst("^\\S+\\s*", "");
                        sendMessage.putExtra("message", getString(R.string.done)
                                .concat(getString(R.string.describe_check_schedule_command)));
                        break;
                    }
                    if (command.toLowerCase().matches("^\\$cellcls\\s*\\S+")) {
                        cellClass = command.replaceFirst("^\\S+\\s*", "");
                        sendMessage.putExtra("message", getString(R.string.done)
                                .concat(getString(R.string.describe_check_schedule_command)));
                        break;
                    }
                    if (command.toLowerCase().matches("^\\$[\\w\\W]*")) {
                        String commandsList = getString(R.string.commands_list);
                        sendMessage.putExtra("message", commandsList);
                        break;
                    }
                    return;
                }
            }
            sendMessage.removeExtra("imageUrl");
        }

        private long parseMessage(Message message) {
            Date messageDate = new Date();
            String messageTime = dateFormat.format(messageDate).replace("\n", " ");
            long userId = message.from().id();
            int listSize = userList.size();
            userList.add(userId);
            if (userList.size() > listSize)
                io.saveUsers(userList);

            String stickerId = null;
            if (message.sticker() != null)
                stickerId = message.sticker().fileId();

            StringBuilder builder = new StringBuilder();
            try {
                builder
                        .append("messageId=").append(message.messageId()).append(System.lineSeparator())
                        .append("date=").append(messageTime).append(System.lineSeparator())
                        .append("text=").append(message.text()).append(System.lineSeparator())
                        .append("sticker=").append(stickerId).append(System.lineSeparator())
                        .append("userId=").append(userId).append(System.lineSeparator())
                        .append("username=").append(message.from().username()).append(System.lineSeparator())
                        .append("firstName=").append(message.from().firstName()).append(System.lineSeparator())
                        .append("lastName=").append(message.from().lastName()).append(System.lineSeparator())
                        .append("isBot=").append(message.from().isBot()).append(System.lineSeparator())
                        .append(System.lineSeparator());
            } catch (Exception e) {
                e.printStackTrace();
            }

            io.saveMessages(builder.toString());
            return userId;
        }
    }
}
