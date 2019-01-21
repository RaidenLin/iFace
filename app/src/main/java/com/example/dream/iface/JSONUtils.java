package com.example.dream.iface;

import android.nfc.Tag;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.content.ContentValues.TAG;

public class JSONUtils {
    public void receiveRequestWithOkHttp(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OkHttpClient client = new OkHttpClient();
                    Request request = new Request.Builder().url("http://111.231.249.93/iface/public/index.php/Index/Faceserach/faceserach").build();
                    Response response = client.newCall(request).execute();
                    String responseData = response.body().string();
                    Log.d("MainActivity",responseData);
                    //parseJSONWithJSONObject(responseData);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }).start();
    }
    /*
    * 解析JSON数据
    * */
    private void parseJSONWithJSONObject(String jsonData){
        try{
//            JSONArray jsonArray = new JSONArray(jsonData);
                JSONObject jsonObject = new JSONObject(jsonData);
                String message = jsonObject.getString("message");

        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    public final static int CONNECT_TIMEOUT = 60;
    public final static int READ_TIMEOUT = 100;
    public final static int WRITE_TIMEOUT = 60;
    public static final OkHttpClient client = new OkHttpClient.Builder().readTimeout(READ_TIMEOUT, TimeUnit.SECONDS).writeTimeout(WRITE_TIMEOUT,TimeUnit.SECONDS).connectTimeout(CONNECT_TIMEOUT,TimeUnit.SECONDS).build();
    public void sendJSONByPOST(FacePhoto picture, String url) {
        OkHttpClient client = new OkHttpClient.Builder().readTimeout(READ_TIMEOUT, TimeUnit.SECONDS).writeTimeout(WRITE_TIMEOUT,TimeUnit.SECONDS).connectTimeout(CONNECT_TIMEOUT,TimeUnit.SECONDS).build();
        Gson gson = new Gson();
        String json = gson.toJson(picture);
        Log.d("MainActivity","json : "+json);
        RequestBody requestBody = FormBody.create(MediaType.parse("application/json; charset=utf-8"), json);
        Request request = new Request.Builder().url(url).post(requestBody).build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                System.out.println("连接失败"+e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                System.out.println(response.body().string());
            }
        });

    }
    public void sendOkHttpRequest(FacePhoto picture,String url,okhttp3.Callback callback){
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(callback);
    }
}
