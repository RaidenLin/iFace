package com.example.dream.iface;

import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class BitmapToBase64Utils {
    public String bitmapToBase64(Bitmap bitmap){
        String result = null;
        ByteArrayOutputStream baos = null;
        try{
            if (bitmap != null){
                baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG,100,baos);
                baos.flush();
                baos.close();
                byte[] bitmapBytes = baos.toByteArray();
                result = android.util.Base64.encodeToString(bitmapBytes, android.util.Base64.DEFAULT);
            }
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            try {
                if (baos != null) {
                    baos.flush();
                    baos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }
}
