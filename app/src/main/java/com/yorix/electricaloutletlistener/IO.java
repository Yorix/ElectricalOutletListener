package com.yorix.electricaloutletlistener;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.pengrad.telegrambot.model.PhotoSize;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;

class IO {
    private final Context context;

    IO(Context context) {
        this.context = context;
    }


    ArrayList<String[]> readFromFileToListStringArray(String filename) {
        LinkedList<String[]> linkedList = new LinkedList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.openFileInput(filename)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() < 2)
                    continue;
                String[] strings = line
                        .substring(1, line.length() - 1)
                        .replace("\\n", "\n")
                        .replace("\\t", "\t")
                        .split("\",\\s*\"");
                linkedList.add(strings);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>(linkedList);
    }

    ArrayList<String> readFromFileToListString(String filename) {
        LinkedList<String> linkedList = new LinkedList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.openFileInput(filename)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                linkedList.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>(Collections.singleton(""));
        }
        if (linkedList.isEmpty()) linkedList.add("");
        return new ArrayList<>(linkedList);
    }

    HashSet<Long> readFromFileToSetLong(String filename) {
        LinkedList<Long> linkedList = new LinkedList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.openFileInput(filename)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                linkedList.add(Long.parseLong(line));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new HashSet<>(linkedList);
    }

    boolean fromResToFileDir(int resource, String filename) {
        boolean result = false;
        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeResource(context.getResources(), resource);
        } catch (Exception e) {
            e.printStackTrace();
            bitmap = null;
        }
        if (bitmap != null) {
            try (OutputStream out = new FileOutputStream(new File(context.getFilesDir(), filename))) {
                result = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                bitmap.recycle();
            }
            return result;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getResources().openRawResource(resource)))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
                builder.append(line.concat("\n"));
            result = saveData(filename, builder.toString(), Context.MODE_PRIVATE);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    boolean savePicture(Bitmap bitmap, String filename) {
        boolean result = false;
        try (OutputStream out = new FileOutputStream(new File(context.getFilesDir(), filename))) {
            result = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bitmap != null)
                bitmap.recycle();
        }
        return result;
    }

    boolean saveMessages(String data) {
        boolean result = false;
        try {
            String filename = context.getString(R.string.messages_filename);
            result = saveData(filename, data, Context.MODE_APPEND);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    boolean saveUsers(HashSet<Long> userList) {
        String filename = context.getString(R.string.user_list_filename);
        StringBuilder builder = new StringBuilder();

        for (long userId : userList)
            builder.append(userId).append(System.lineSeparator());
        if (builder.length() > 0)
            builder.deleteCharAt(builder.length() - 1);

        return saveData(filename, builder.toString(), Context.MODE_PRIVATE);
    }

    boolean saveTime(Date date) {
        boolean result = false;
        try {
            String filename = context.getString(R.string.time_filename);
            result = saveData(filename, String.valueOf(date.getTime()), Context.MODE_PRIVATE);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    void readTime(Date date) {
        String filename = context.getString(R.string.time_filename);
        try {
            date.setTime(Long.parseLong(readData(filename)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    boolean pushDocument(String fileId, String filename) {
        String botToken = context.getString(R.string.bot_token);

        URL url = null;
        try {
            url = new URL("https://api.telegram.org/bot" + botToken + "/getFile?file_id=" + fileId);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        assert url != null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
            String res = reader.readLine();
            reader.close();
            JSONObject jresult = new JSONObject(res);
            JSONObject path = jresult.getJSONObject("result");
            String filePath = path.getString("file_path");
            url = new URL("https://api.telegram.org/file/bot" + botToken + "/" + filePath);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
                builder.append(line).append(System.lineSeparator());
            builder.deleteCharAt(builder.length() - 1);

            return saveData(filename, builder.toString(), Context.MODE_PRIVATE);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    boolean pushScheduleImage(PhotoSize[] photo, String imageFilename) {
        String botToken = context.getString(R.string.bot_token);
        String fileId = photo[photo.length - 1].fileId();

        URL url = null;
        try {
            url = new URL("https://api.telegram.org/bot" + botToken + "/getFile?file_id=" + fileId);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        assert url != null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
            String res = reader.readLine();
            reader.close();
            JSONObject jresult = new JSONObject(res);
            JSONObject path = jresult.getJSONObject("result");
            String filePath = path.getString("file_path");
            url = new URL("https://api.telegram.org/file/bot" + botToken + "/" + filePath);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream(url.openStream());
            return bitmap.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(new File(context.getFilesDir(), imageFilename)));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
        return false;
    }

    boolean fileDelete(String filename) {
        boolean result = false;
        try {
            File file = new File(context.getFilesDir(), filename);
            result = file.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }


    boolean saveData(String filename, String data, int mode) {
        try (FileOutputStream fos = context.openFileOutput(filename, mode)) {
            fos.write(data.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    String readData(String filename) {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.openFileInput(filename)))) {
            String line;
            while ((line = reader.readLine()) != null)
                builder.append(line.concat("\n"));
            builder.deleteCharAt(builder.length() - 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return builder.toString();
    }

    long getMs(String delay) {
        if (delay.isEmpty() || !delay.matches("^\\d{1,8}[sSmhHdDwWMyY]?$"))
            return 0;
        if (delay.matches("^\\d{1,8}$"))
            return Long.parseLong(delay);
        long seconds = Long.parseLong(delay.substring(0, delay.length() - 1)) * 1000;
        if (delay.matches("^\\d{1,8}[sS]$"))
            return seconds;
        if (delay.matches("^\\d{1,8}m$"))
            return seconds * 60;
        if (delay.matches("^\\d{1,8}[hH]$"))
            return seconds * 3_600;
        if (delay.matches("^\\d{1,8}[dD]$"))
            return seconds * 86_400;
        if (delay.matches("^\\d{1,8}[wW]$"))
            return seconds * 604_800;
        if (delay.matches("^\\d{1,8}M$"))
            return seconds * 2_628_003;
        if (delay.matches("^\\d{1,8}[Yy]$"))
            return seconds * 31_536_035;
        return 0;
    }
}
