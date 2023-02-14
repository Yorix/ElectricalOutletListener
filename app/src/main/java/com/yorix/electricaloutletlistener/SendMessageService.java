package com.yorix.electricaloutletlistener;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.Intent;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.Keyboard;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ReplyKeyboardRemove;
import com.pengrad.telegrambot.request.SendDocument;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.request.SendSticker;
import com.pengrad.telegrambot.response.SendResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;


public class SendMessageService extends IntentService {
    @SuppressLint("SimpleDateFormat")
    private final SimpleDateFormat format = new SimpleDateFormat("d.MM.yyyy HH:mm:ss");

    public SendMessageService() {
        super("SendMessageService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        TelegramBot telegramBot = new TelegramBot(getString(R.string.bot_token));
        ArrayList<String[]> keyboardList = (ArrayList<String[]>) intent.getSerializableExtra("keyboard");
        String[][] keyboard = keyboardList.toArray(new String[][]{});
        Set<Long> userList = (Set<Long>) intent.getSerializableExtra("userList");
        HashSet<Long> failList = new HashSet<>();
        String message = intent.getStringExtra("message");
        String caption = intent.getStringExtra("caption");
        String imageUrl = intent.getStringExtra("imageUrl");
        String imageFilename = intent.getStringExtra("imageFilename");
        String sticker = intent.getStringExtra("sticker");
        String filename = intent.getStringExtra("filename");
        File imageFile = imageFilename != null
                ? new File(getFilesDir().getAbsolutePath(), imageFilename)
                : null;

        Keyboard replyKeyboardMarkup = keyboard.length != 0
                ? new ReplyKeyboardMarkup(keyboard).resizeKeyboard(true)
                : new ReplyKeyboardRemove();

        SendResponse response;
        for (long chatId : userList) {
            if (filename != null) {
                try {
                    File file = new File(getFilesDir().getAbsolutePath(), filename);
                    SendDocument sendDocument = new SendDocument(chatId, file)
                            .replyMarkup(replyKeyboardMarkup)
                            .parseMode(ParseMode.HTML);
                    response = telegramBot.execute(sendDocument);
                    log(chatId, response.isOk() ? "ok" : response.description());
                } catch (Exception e) {
                    failList.add(chatId);
                    log(chatId, e.getMessage());
                    e.printStackTrace();
                }
            } else if (imageUrl != null || imageFile != null) {
                SendPhoto sendPhoto = imageFile == null ? new SendPhoto(chatId, imageUrl) : new SendPhoto(chatId, imageFile);
                sendPhoto
                        .caption(caption)
                        .replyMarkup(replyKeyboardMarkup)
                        .parseMode(ParseMode.HTML);
                try {
                    response = telegramBot.execute(sendPhoto);
                    log(chatId, response.isOk() ? "ok" : response.description());
                } catch (Exception e) {
                    failList.add(chatId);
                    log(chatId, e.getMessage());
                    e.printStackTrace();
                }
            } else if (message != null) {
                SendMessage sendMessage = new SendMessage(chatId, message);
                sendMessage
                        .replyMarkup(replyKeyboardMarkup)
                        .parseMode(ParseMode.HTML);
                try {
                    response = telegramBot.execute(sendMessage);
                    log(chatId, response.isOk() ? "ok" : response.description());
                } catch (Exception e) {
                    failList.add(chatId);
                    log(chatId, e.getMessage());
                    e.printStackTrace();
                }
            } else if (sticker != null) {
                SendSticker sendSticker = new SendSticker(chatId, sticker);
                sendSticker.replyMarkup(replyKeyboardMarkup);
                try {
                    response = telegramBot.execute(sendSticker);
                    log(chatId, response.isOk() ? "ok" : response.description());
                } catch (Exception e) {
                    failList.add(chatId);
                    log(chatId, e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(4000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (!failList.isEmpty())
                    sendBroadcast(new Intent("failList")
                            .putExtra("failList", failList)
                            .putExtra("message", message)
                            .putExtra("caption", caption)
                            .putExtra("imageUrl", imageUrl)
                            .putExtra("sticker", sticker)
                            .putExtra("filename", filename));
            }
        }.start();

        stopSelf();
    }

    private void log(long chatId, String info) {
        String logText;
        String time = format.format(new Date(System.currentTimeMillis()));
        try (FileOutputStream fos = openFileOutput(getString(R.string.log_filename), MODE_APPEND)) {
            logText = String.format("%s: %s: %s\n", time, chatId, info);
            fos.write(logText.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
