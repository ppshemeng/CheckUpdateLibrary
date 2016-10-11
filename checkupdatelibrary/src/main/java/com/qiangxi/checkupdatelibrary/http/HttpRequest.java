package com.qiangxi.checkupdatelibrary.http;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;

import com.qiangxi.checkupdatelibrary.callback.CheckUpdateCallback;
import com.qiangxi.checkupdatelibrary.callback.DownloadCallback;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by qiang_xi on 2016/10/9 20:36.
 */

public class HttpRequest {
    private static final int hasUpdate = 0;
    private static final int noUpdate = 1;
    private static final int downloadSuccess = 2;
    private static final int downloading = 3;
    private static final int downloadFailure = 4;
    private static final int error = -1;

    private static CheckUpdateCallback updateCallback;
    private static DownloadCallback downloadCallback;

    private static Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bundle data = msg.getData();
            if (msg.what == hasUpdate) {
                updateCallback.onCheckUpdateSuccess((String) msg.obj, true);
            } else if (msg.what == noUpdate) {
                updateCallback.onCheckUpdateSuccess((String) msg.obj, false);
            } else if (msg.what == error) {
                updateCallback.onCheckUpdateFailure((String) msg.obj, -1);
            } else if (msg.what == downloadSuccess) {
                File file = (File) data.getSerializable("file");
                downloadCallback.onDownloadSuccess(file);
            } else if (msg.what == downloading) {
                int current = data.getInt("current");
                int fileLength = data.getInt("fileLength");
                downloadCallback.onProgress(current, fileLength);
            } else if (msg.what == downloadFailure) {
                downloadCallback.onDownloadFailure((String) msg.obj);
            }
        }
    };

    private HttpRequest() {
    }

    /**
     * post请求
     *
     * @param urlPath  请求地址
     * @param callback 请求回调
     */
    public static void post(@NonNull final String urlPath, @NonNull final CheckUpdateCallback callback) {
        updateCallback = callback;
        final Message message = new Message();
        new Thread() {
            @Override
            public void run() {
                super.run();
                BufferedReader bufferedReader = null;
                try {
                    URL httpUrl = new URL(urlPath);
                    HttpURLConnection httpURLConnection = (HttpURLConnection) httpUrl.openConnection();
                    //设置请求头header
                    httpURLConnection.setRequestProperty("test-header", "post-header-value");
                    httpURLConnection.setRequestMethod("POST");
                    httpURLConnection.setReadTimeout(5000);
                    //获取内容
                    InputStream inputStream = httpURLConnection.getInputStream();
                    bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                    final StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        sb.append(line);
                    }
                    String json = sb.toString();
                    JSONObject object = new JSONObject(json);
                    int newAppVersionCode = object.getInt("newAppVersionCode");
                    int oldAppVersionCode = object.getInt("oldAppVersionCode");
                    message.obj = json;
                    //有更新
                    if (newAppVersionCode > oldAppVersionCode) {
                        message.what = hasUpdate;
                        handler.sendMessage(message);
                    }
                    //无更新
                    else {
                        message.what = noUpdate;
                        handler.sendMessage(message);
                    }
                } catch (Exception e) {
                    message.obj = e.toString();
                    message.what = error;
                    handler.sendMessage(message);
                } finally {
                    if (bufferedReader != null) {
                        try {
                            bufferedReader.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }.start();
    }

    public static void download(@NonNull final String downloadPath, @NonNull final String filePath, @NonNull final String fileName, @NonNull final DownloadCallback callback) {
        downloadCallback = callback;
        final Message message = new Message();
        new Thread() {
            @Override
            public void run() {
                super.run();
                InputStream input = null;
                OutputStream output = null;
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(downloadPath);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.connect();
                    int fileLength = connection.getContentLength();
                    // 文件总长度
                    input = connection.getInputStream();
                    File file = new File(filePath, fileName);
                    output = new FileOutputStream(file);
                    byte data[] = new byte[4096];
                    //当前下载进度
                    int current = 0;
                    int count;
                    Bundle bundle = new Bundle();
                    while ((count = input.read(data)) != -1) {
                        current += count;
                        if (fileLength > 0) {
                            message.what = downloading;
                            bundle.putInt("current", current);
                            bundle.putInt("fileLength", fileLength);
                            message.setData(bundle);
                            handler.sendMessage(message);
                        }
                        output.write(data, 0, count);
                    }
                    message.what = downloadSuccess;
                    bundle.putSerializable("file", file);
                    message.setData(bundle);
                    handler.sendMessage(message);
                } catch (Exception e) {
                    message.what = downloadFailure;
                    message.obj = e.toString();
                    handler.sendMessage(message);
                } finally {
                    try {
                        if (output != null)
                            output.close();
                        if (input != null)
                            input.close();
                    } catch (IOException ignored) {
                    }
                    if (connection != null)
                        connection.disconnect();
                }
            }
        }.start();
    }
}