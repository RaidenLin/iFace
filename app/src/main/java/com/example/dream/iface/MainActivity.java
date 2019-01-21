package com.example.dream.iface;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Time;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.example.dream.iface.JSONUtils.CONNECT_TIMEOUT;
import static com.example.dream.iface.JSONUtils.READ_TIMEOUT;
import static com.example.dream.iface.JSONUtils.WRITE_TIMEOUT;

public class MainActivity extends AppCompatActivity {
    private static void Log(String message){
        Log.i(MainActivity.class.getName(),message);
    }
    //使照片竖直显示
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);
    }
    private TextureView cView;//相机预览
    private TextureView rView;//标注人脸
    private ImageView imageView;//拍照照片显示
    private TextView textView;
    private Button btnFront;
    private Button btnBack;
    private Button btnClose;
    private Button btnCapture;
    private Surface previewSurface;//预览Surface
    private ImageReader cImageReader;
    private Surface captureSurface;//拍照Surface
    HandlerThread cHandlerThread;//相机处理线程
    Handler cHandler;//相机处理
    CameraDevice cDevice;
    CameraCaptureSession cSession;
    CameraDevice.StateCallback cDeviceOpenCallback = null;//相机开启回调
    CaptureRequest.Builder previewRequestBuilder;//预览请求构建
    CaptureRequest previewRequest;//预览请求
    CameraCaptureSession.CaptureCallback previewCallback;//预览回调
    CaptureRequest.Builder captureRequestBuiler;
    CaptureRequest captureRequest;
    CameraCaptureSession.CaptureCallback captureCallback;
    int[] faceDetectModes;
    Size cPixelSize;//相机成像尺寸
    int cOrientation;
    Size captureSize;
    boolean isFront;
    Paint pb;
    Bitmap bitmap;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }
    /*
    * 初始化界面
    * */
    private void initView(){
        cView = findViewById(R.id.cView);//拍照预览画面
        rView = findViewById(R.id.rView);//人脸检测框
        imageView = findViewById(R.id.imageView);
        textView = findViewById(R.id.textView);
        btnFront = findViewById(R.id.btnFront);//切换至前置镜头
        btnBack = findViewById(R.id.btnBack);//切换至后置镜头
        btnClose = findViewById(R.id.btnClose);//关闭按钮
        btnCapture = findViewById(R.id.btnCapture);//拍照按钮
        //隐藏背景色，避免标注人脸时挡住预览画面
        rView.setAlpha(0.9f);
        btnFront.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera(true);
            }
        });
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera(false);
            }
        });
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeCamera();
            }
        });
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                executeCapture();
            }
        });
    }

    private void openCamera(boolean isFront){
        closeCamera();
        this.isFront = isFront;
        String cId = null;
        if(isFront)//摄像头相对于屏幕的方向
            cId = CameraCharacteristics.LENS_FACING_BACK + "";
        else
            cId = CameraCharacteristics.LENS_FACING_FRONT + "";
        CameraManager cManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);//获得可用的相机设备
        try{
            CameraCharacteristics characteristics = cManager.getCameraCharacteristics(cId);//获得当前摄像头的相关信息
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);//获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
            Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);//获取预览尺寸
            Size[] captureSizes = map.getOutputSizes(ImageFormat.JPEG);//获取拍照尺寸
            cOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);//获取相机角度
            Rect cRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);//获取成像区域
            cPixelSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);//获取成像尺寸，同上
            //可用于判断是否支持人脸检测，以及支持到哪种程度
            faceDetectModes = characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);//支持的人脸检测模式
            int maxFaceCount = characteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);//支持的最大检测人脸数量
            //此处写死640*480，实际从预览尺寸列表选择
            Size sSize = new Size(640,480);//previewSizes[0];
            //设置预览尺寸（避免控件尺寸与预览画面尺寸不一致时画面变形）
            cView.getSurfaceTexture().setDefaultBufferSize(sSize.getWidth(),sSize.getHeight());
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {//检查是否获取到摄像头权限
                Toast.makeText(this,"请授予摄像头权限", Toast.LENGTH_LONG).show();
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA}, 0);
                return;
            }
            //根据摄像头ID，开启摄像头
            try{
                cManager.openCamera(cId, getCDeviceOpenCallback(), getCHandler());
            }catch (CameraAccessException e){
                Log(Log.getStackTraceString(e));

            }
        }catch (CameraAccessException e){
            Log(Log.getStackTraceString(e));
        }
    }
    private void closeCamera(){
        if(cSession!=null){
            cSession.close();
            cSession = null;
        }
        if(cDevice!=null){
            cDevice.close();
            cDevice=null;
        }
        if(cImageReader != null){
            cImageReader.close();
            cImageReader = null;
            captureRequestBuiler = null;
        }
        if(cHandlerThread!=null){
            cHandlerThread.quitSafely();
            try{
                cHandlerThread.join();
                cHandlerThread = null;
                cHandler = null;
            }catch (InterruptedException e){
                Log(Log.getStackTraceString(e));
            }
        }
    }
    /*
    * 初始化并获取相机开启回调对象。当准备就绪后，发起预览请求
    * */
    private CameraDevice.StateCallback getCDeviceOpenCallback(){
        if(cDeviceOpenCallback == null){
            cDeviceOpenCallback = new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cDevice =camera;
                    try{
                        //创建Session，需先完成画面呈现目标（此处为预览和拍照Surface）的初始化
                        camera.createCaptureSession(Arrays.asList(getPreviewSurface(), getCaptureSurface()), new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                cSession = session;
                                //构建预览请求，并发起请求
                                Log("发出预览请求");
                                try{
                                    session.setRepeatingRequest(getPreviewRequest(), getPreviewCallback(), getCHandler());
                                }catch (CameraAccessException e){
                                    Log(Log.getStackTraceString(e));
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                session.close();
                            }
                            },getCHandler());
                        }catch (CameraAccessException e){
                        Log(Log.getStackTraceString(e));
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                }
            };
        }
        return cDeviceOpenCallback;
    }
    /*
    * 初始化并获取相机线程处理
    * */
    private Handler getCHandler(){
        if ((cHandler == null)){
            //单独开一个线程给相机使用
            cHandlerThread = new HandlerThread("cHandlerThread");
            cHandlerThread.start();
            cHandler = new Handler(cHandlerThread.getLooper());
        }
        return cHandler;
    }
    /*
    * 获取支持的最高人脸检测级别
    * */
    private int getFaceDetectMode(){
        if(faceDetectModes == null)
            return CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL;
        else
            return faceDetectModes[faceDetectModes.length-1];
    }
    /*
    * 初始化并获取预览回调对象
    * */
    private CameraCaptureSession.CaptureCallback getPreviewCallback(){
        if (previewCallback == null){
            previewCallback = new CameraCaptureSession.CaptureCallback() {
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result){
                    MainActivity.this.onCameraImagePreviewed(result);
                }
            };
        }
            return previewCallback;
    }
    /*
    * 生成并获取预览请求
    * */
    private CaptureRequest getPreviewRequest(){
        previewRequest = getPreviewRequestBuilder().build();
        return previewRequest;
    }
    /*
    * 初始化并获取预览请求构建对象，进行通用配置，并每次获取时进行人脸检测级别配置
    * */
    private CaptureRequest.Builder getPreviewRequestBuilder(){
        if(previewRequestBuilder == null){
            try{
                previewRequestBuilder = cSession.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                previewRequestBuilder.addTarget(getPreviewSurface());
                previewRequestBuilder.set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_MODE_AUTO);//自动曝光、白平衡、对焦
            }catch (CameraAccessException e){
                Log(Log.getStackTraceString(e));
            }
        }
        previewRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,getFaceDetectMode());//设置人脸检测级别
        return previewRequestBuilder;
    }
    /*
    * 获取预览Surface
    * */
    private Surface getPreviewSurface(){
        if (previewSurface == null)
            previewSurface = new Surface(cView.getSurfaceTexture());
        return previewSurface;
    }
    /*
    * 处理相机画面，处理完成事件，获取检测到的人脸坐标，换算并绘制方框
    * */
    private void onCameraImagePreviewed(CaptureResult result){
        Face faces[] = result.get(CaptureResult.STATISTICS_FACES);
        //showMessage(false,"人脸个数：["+faces.length+"]");
        Canvas canvas = rView.lockCanvas();
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);//旧画面清理覆盖
        if(faces.length>0){
            for (int i=0;i<faces.length;i++){
                Rect fRect = faces[i].getBounds();
                //人脸检测坐标基于相机成像画面尺寸以及坐标原点。此处进行比例换算
                //成像画面与方框绘制画布长宽比比例（同画面角度情况下的长宽比例（此处前后摄像头成像画面相对预览画面倒置（±90°），计算比例时长宽互换））
                float scaleWidth = canvas.getHeight()*1.0f/cPixelSize.getWidth();
                float scaleHeight = canvas.getWidth()*1.0f/cPixelSize.getHeight();
                //坐标缩放
                int l = (int) (fRect.left*scaleWidth);
                int t = (int) (fRect.top*scaleHeight);
                int r = (int) (fRect.right*scaleWidth);
                int b = (int) (fRect.bottom*scaleHeight);
                //人脸检测坐标基于相机成像画面尺寸以及坐标原点。此处进行坐标转换以及原点(0,0)换算
                //人脸检测：坐标原点为相机成像画面的左上角，left、top、bottom、right以成像画面左上下右为基准
                //画面旋转后：原点位置不一样，根据相机成像画面的旋转角度需要换算到画布的左上角，left、top、bottom、right基准也与原先不一样，
                //如相对预览画面相机成像画面角度为90°那么成像画面坐标的top，在预览画面就为left。如果再翻转，那成像画面的top就为预览画面的right，且坐标起点为右，需要换算到左边
                if(isFront){
                    //此处前置摄像头成像画面相对于预览画面顺时针90°+翻转。left、top、bottom、right变为bottom、right、top、left，并且由于坐标原点由左上角变为右下角，X,Y方向都要进行坐标换算
                    canvas.drawRect(canvas.getWidth()-b,canvas.getHeight()-r,canvas.getWidth()-t,canvas.getHeight()-l,getPaint());
                }else{
                    //此处后置摄像头成像画面相对于预览画面顺时针270°，left、top、bottom、right变为bottom、left、top、right，并且由于坐标原点由左上角变为左下角，Y方向需要进行坐标换算
                    canvas.drawRect(canvas.getWidth()-b,l,canvas.getWidth()-t,r,getPaint());
                }
            }
        }
        rView.unlockCanvasAndPost(canvas);
    }
    /*
    * 初始化画笔
    * */
    private Paint getPaint(){
        if (pb == null){
            pb = new Paint();
            pb.setColor(Color.YELLOW);
            pb.setStrokeWidth(10);
            pb.setStyle(Paint.Style.STROKE);//使绘制的矩形中空
        }
        return pb;
    }
    /*
    * 初始化拍照
    * */
    private Surface getCaptureSurface(){
        if(cImageReader == null){
            cImageReader = ImageReader.newInstance(getCaptureSize().getWidth(), getCaptureSize().getHeight(), ImageFormat.JPEG, 2);
            cImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    onCaptureFinished(reader);
                }
            },getCHandler());
            captureSurface = cImageReader.getSurface();
        }
        return captureSurface;
    }
    /*
    * 获取拍照尺寸
    * */
    private Size getCaptureSize(){
        if(captureSize!=null)
            return captureSize;
        else
            return cPixelSize;
    }
    /*
    * 执行拍照
    * */
    private void executeCapture(){
       try{
           Log.i(this.getClass().getName(),"发出请求");
           cSession.capture(getCaptureRequest(),getCaptureCallback(),getCHandler());
       }catch (CameraAccessException e){
           Log(Log.getStackTraceString(e));
       }
    }
    private CaptureRequest getCaptureRequest(){
        captureRequest = getCaptureRequestBuiler().build();
        return captureRequest;
    }
    private CaptureRequest.Builder getCaptureRequestBuiler(){
        if (captureRequestBuiler == null){
            try{
                captureRequestBuiler = cSession.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                captureRequestBuiler.set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_AF_MODE_AUTO);
                captureRequestBuiler.addTarget(getCaptureSurface());
                //照片旋转
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                int rotationTo = getOrientation(rotation);
                captureRequestBuiler.set(CaptureRequest.JPEG_ORIENTATION,rotationTo);
            }catch (CameraAccessException e){
                Log(Log.getStackTraceString(e));
            }
        }
        return captureRequestBuiler;
    }
    private CameraCaptureSession.CaptureCallback getCaptureCallback(){
        if(captureCallback == null){
            captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    MainActivity.this.onCameraImagePreviewed(result);
                }
            };
        }
        return captureCallback;
    }
    private int getOrientation(int rotation){
        return (ORIENTATIONS.get(rotation)+cOrientation+270)%360;
    }
    /*
    * 处理相机拍照完成的数据
    * */
    private void onCaptureFinished(ImageReader reader){
        Image image = reader.acquireLatestImage();
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        image.close();
        buffer.clear();
        if (bitmap!=null){
            bitmap.recycle();
            bitmap = null;
        }
        bitmap = BitmapFactory.decodeByteArray(data,0,data.length);
        data = null;
        if (bitmap!=null){
            //前置镜头翻转照片
            if(isFront){
                Matrix matrix = new Matrix();
                matrix.postScale(-1,1);
                Bitmap imgToShow = Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,false);
                bitmap.recycle();
                showImage(imgToShow);
                BitmapToBase64Utils bitmapToBase64Utils = new BitmapToBase64Utils();
                String base64Code =  bitmapToBase64Utils.bitmapToBase64(imgToShow);
                Log.d("MainActivity","Base64Code:"+base64Code);
                FacePhoto facePhoto = new FacePhoto();
                facePhoto.setFace_photo(base64Code);
                sendJSONByPOST(facePhoto,"http://111.231.249.93/iface/public/index.php/Index/Faceserach/faceserach");
            }else{
                showImage(bitmap);
                BitmapToBase64Utils bitmapToBase64Utils = new BitmapToBase64Utils();
                String base64Code =  bitmapToBase64Utils.bitmapToBase64(bitmap);
                Log.d("MainActivity","Base64Code:"+base64Code);
                FacePhoto facePhoto = new FacePhoto();
                facePhoto.setFace_photo(base64Code);
                sendJSONByPOST(facePhoto,"http:/111.231.249.93/iface/public/index.php/Index/Faceserach/faceserach");
            }
        }
        Runtime.getRuntime().gc();
    }
    private void showImage(final Bitmap image){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                imageView.setImageBitmap(image);
            }
        });
    }
    private void showInfo(boolean add,String message){
        if(add)
            textView.setText(textView.getText()+"\n"+message);
        else
            textView.setText(message);
    }
    private void showMessage(final boolean add,final String message){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(add)
                    textView.setText(textView.getText()+"\n"+message);
                else
                    textView.setText(message);
            }
        });
    }
    private void sendJSONByPOST(FacePhoto picture, final String url) {
        OkHttpClient client = new OkHttpClient.Builder().readTimeout(READ_TIMEOUT, TimeUnit.SECONDS).writeTimeout(WRITE_TIMEOUT,TimeUnit.SECONDS).connectTimeout(CONNECT_TIMEOUT,TimeUnit.SECONDS).build();
        Gson gson = new Gson();
        final String json = gson.toJson(picture);
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
            public void onResponse(Call call, final Response response){
                //System.out.println(response.body().string());
                MainActivity.this.runOnUiThread(new Runnable() {//线程切换回主线程更新UI
                    @Override
                    public void run() {
                        try {
                            String jsonMsg = response.body().string();
                            Log.d("MainActivity",jsonMsg);
                            JSONObject jsonObject = new JSONObject(jsonMsg);
                            String message = jsonObject.getString("message");
                            if (message.equals("用户识别成功")){
                                User user = new User();
                                JSONObject jsonObject1 = jsonObject.getJSONObject("info");
                                String stu_id = jsonObject1.getString("stu_id");
                                String user_name = jsonObject1.getString("user_name");
                                String sex = jsonObject1.getString("sex");
                                String user_class = jsonObject1.getString("class");
                                user.setStu_id(stu_id);
                                user.setUser_name(user_name);
                                user.setSex(sex);
                                user.setUser_class(user_class);
                                showInfo(false,"ID："+user.getStu_id()+"\n"+"姓名："+user.getUser_name()+"\n"+"性别："+user.getSex()+"\n"+"班级："+user.getUser_class());
                                TimerTask task = new TimerTask() {
                                    @Override
                                    public void run() {
                                        showMessage(false,"");//Android系统中的视图组件并不是线程安全的，如果要更新视图，必须在主线程中更新，不可以在子线程中执行更新的操作
                                    }
                                };
                                Timer timer = new Timer();
                                timer.schedule(task,10000);//10秒后清空TextView
                            }else if (message.equals("用户不存在")){
                                textView.setText("");//置空textview，防止信息残留
                                Toast.makeText(MainActivity.this,"用户不存在",Toast.LENGTH_SHORT).show();
                            }else if (message.equals("请用活体进行人脸识别")){
                                textView.setText("");//置空textview，防止信息残留
                                Toast.makeText(MainActivity.this,"请用活体进行人脸识别",Toast.LENGTH_SHORT).show();
                            } else {
                                textView.setText("");
                                Toast.makeText(MainActivity.this,"请求参数为空",Toast.LENGTH_SHORT).show();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });

    }
}
