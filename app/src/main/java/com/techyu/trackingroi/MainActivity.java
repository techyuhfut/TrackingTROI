package com.techyu.trackingroi;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.CamcorderProfile;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.techyu.trackingroi.Camera.Camera2Source;
import com.techyu.trackingroi.Camera.FaceGraphic;
import com.techyu.trackingroi.databinding.ActivityMainBinding;
import com.techyu.trackingroi.Utils.Utils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import com.techyu.trackingroi.Camera.CameraSource;
import com.techyu.trackingroi.Camera.CameraSourcePreview;
import com.techyu.trackingroi.Camera.GraphicOverlay;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Camera2 Vision";
    private Context context;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_STORAGE_PERMISSION = 201;

    private float mEulerX;
    private float mEulerY;
    private float mEulerZ;

    private String image_test;
    private Bitmap mbitmap;
    private boolean bitFlag=false;
    private boolean onceFlag=false;



    // CAMERA VERSION ONE DECLARATIONS
    private CameraSource mCameraSource = null;

    // CAMERA VERSION TWO DECLARATIONS
    private Camera2Source mCamera2Source = null;

    // COMMON TO BOTH CAMERAS
    private FaceDetector previewFaceDetector = null;
    private FaceGraphic mFaceGraphic;
    private boolean wasActivityResumed = false;
    private boolean isRecordingVideo = false;
    private boolean flashEnabled = true;

    // DEFAULT CAMERA BEING OPENED
    private boolean usingFrontCamera = true;

    // MUST BE CAREFUL USING THIS VARIABLE.
    // ANY ATTEMPT TO START CAMERA2 ON API < 21 WILL CRASH.
    private boolean useCamera2 = false;

    private ActivityMainBinding binding;
    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(binding.getRoot());
        context = getApplicationContext();
        String rootDir = this.getExternalFilesDir("pic").getAbsolutePath();
        Log.d(TAG, "onCreate: rootDir"+rootDir);
        image_test = rootDir+"/test.png";


        //设置按键相关的点击事件
        if(checkGooglePlayAvailability()) {
            //请求权限+打开相机
            requestPermissionThenOpenCamera();
            //切换前后相机
            binding.switchButton.setOnClickListener(v -> {
                if(usingFrontCamera) {
                    stopCameraSource();//先停止preview
                    createCameraSourceBack();
                    usingFrontCamera = false;
                } else {
                    stopCameraSource();
                    createCameraSourceFront();
                    usingFrontCamera = true;
                }
            });
            //打开闪光灯选项
            binding.flashButton.setOnClickListener(v -> {
                if(useCamera2) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        if(flashEnabled) {
                            mCamera2Source.setFlashMode(Camera2Source.CAMERA_FLASH_OFF);
                            flashEnabled = false;
                            Toast.makeText(context, "FLASH OFF", Toast.LENGTH_SHORT).show();
                        } else {
                            mCamera2Source.setFlashMode(Camera2Source.CAMERA_FLASH_ON);
                            flashEnabled = true;
                            Toast.makeText(context, "FLASH ON", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    if(flashEnabled) {
                        mCameraSource.setFlashMode(CameraSource.CAMERA_FLASH_OFF);
                        flashEnabled = false;
                        Toast.makeText(context, "FLASH OFF", Toast.LENGTH_SHORT).show();
                    } else {
                        mCameraSource.setFlashMode(CameraSource.CAMERA_FLASH_ON);
                        flashEnabled = true;
                        Toast.makeText(context, "FLASH ON", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            //设置拍照按键
            binding.takePictureButton.setOnClickListener(v -> {
                binding.switchButton.setEnabled(false);
                binding.videoButton.setEnabled(false);
                binding.takePictureButton.setEnabled(false);
                if(useCamera2) {
                    if(mCamera2Source != null) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mCamera2Source.takePicture(camera2SourceShutterCallback, camera2SourcePictureCallback);
                    }
                } else {
                    if(mCameraSource != null)mCameraSource.takePicture(cameraSourceShutterCallback, cameraSourcePictureCallback);
                }
            });
            //设置获取视频按钮
            binding.videoButton.setOnClickListener(v -> {
                binding.switchButton.setEnabled(false);
                binding.takePictureButton.setEnabled(false);
                binding.videoButton.setEnabled(false);
                if(isRecordingVideo) {
                    if(useCamera2) {
                        if(mCamera2Source != null) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mCamera2Source.stopVideo();
                        }
                    } else {
                        if(mCameraSource != null)mCameraSource.stopVideo();
                    }
                }
                else {
                    if(useCamera2){
                        if(mCamera2Source != null) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mCamera2Source.recordVideo(camera2SourceVideoStartCallback, camera2SourceVideoStopCallback, camera2SourceVideoErrorCallback, formatter.format(new Date())+".mp4", true);
                        }
                    } else {
                        if(mCameraSource != null) {
                            if(mCameraSource.canRecordVideo(CamcorderProfile.QUALITY_720P)) {
                                mCameraSource.recordVideo(cameraSourceVideoStartCallback, cameraSourceVideoStopCallback, cameraSourceVideoErrorCallback, formatter.format(new Date())+".mp4", true);
                            }
                        }
                    }
                }
            });
            binding.mPreview.setOnTouchListener(CameraPreviewTouchListener);
        }
//        //此处代码测试用，保存ROI图片到本地查看，仅开发用，发布版本绝不能包含（因为涉嫌侵犯用户隐私）
//        while(true){
//            if(bitFlag&&onceFlag){
//                File file = new File(image_test);
//                onceFlag = false;
//                try {
//                    file.createNewFile();
//                    FileOutputStream fos = new FileOutputStream(file);
//                    mbitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
//                    fos.close();
//                    Log.d(TAG, "onCreate: 执行了保存");
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//            break;
//        }

    }
    //This is the end of onCreatView

    /**
     * 安卓初始Camera api相关回调设置
     */

    //api拍照声回调
    final CameraSource.ShutterCallback cameraSourceShutterCallback = () -> {
        //you can implement here your own shutter triggered animation
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                binding.status.setText("shutter event triggered");
                Log.d(TAG, "Shutter Callback!");
            }
        });
    };
    //拍照回调
    final CameraSource.PictureCallback cameraSourcePictureCallback = new CameraSource.PictureCallback() {
        @Override
        public void onPictureTaken(Bitmap picture) {
            Log.d(TAG, "Taken picture is ready!");
            runOnUiThread(() -> {
                binding.status.setText("picture taken");
                binding.switchButton.setEnabled(true);
                binding.videoButton.setEnabled(true);
                binding.takePictureButton.setEnabled(true);
            });
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "/camera_picture.png"));
                picture.compress(Bitmap.CompressFormat.JPEG, 95, out);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };
    //视频回调
    final CameraSource.VideoStartCallback cameraSourceVideoStartCallback = new CameraSource.VideoStartCallback() {
        @Override
        public void onVideoStart() {
            isRecordingVideo = true;
            runOnUiThread(() -> {
                binding.status.setText("video recording started");
                binding.videoButton.setEnabled(true);
                binding.videoButton.setText(getString(R.string.stop_video));
            });
            Toast.makeText(context, "Video STARTED!", Toast.LENGTH_SHORT).show();
        }
    };
    //关闭相机录制回调
    final CameraSource.VideoStopCallback cameraSourceVideoStopCallback = new CameraSource.VideoStopCallback() {
        @Override
        public void onVideoStop(String videoFile) {
            isRecordingVideo = false;
            runOnUiThread(() -> {
                binding.status.setText("video recording stopped");
                binding.switchButton.setEnabled(true);
                binding.takePictureButton.setEnabled(true);
                binding.videoButton.setEnabled(true);
                binding.videoButton.setText(getString(R.string.record_video));
            });
            Toast.makeText(context, "Video STOPPED!", Toast.LENGTH_SHORT).show();
        }
    };
    //相机error回调
    final CameraSource.VideoErrorCallback cameraSourceVideoErrorCallback = new CameraSource.VideoErrorCallback() {
        @Override
        public void onVideoError(String error) {
            isRecordingVideo = false;
            runOnUiThread(() -> {
                binding.status.setText("video recording error");
                binding.switchButton.setEnabled(true);
                binding.takePictureButton.setEnabled(true);
                binding.videoButton.setEnabled(true);
                binding.videoButton.setText(getString(R.string.record_video));
            });
            Toast.makeText(context, "Video Error: "+error, Toast.LENGTH_LONG).show();
        }
    };

    /**
     * 安卓 camera2 api相关回调设置
     */
    //录制video开始的相机回调
    final Camera2Source.VideoStartCallback camera2SourceVideoStartCallback = new Camera2Source.VideoStartCallback() {
        @Override
        public void onVideoStart() {
            isRecordingVideo = true;
            runOnUiThread(() -> {
                binding.status.setText("video recording started");
                binding.videoButton.setEnabled(true);
                binding.videoButton.setText(getString(R.string.stop_video));
            });
            Toast.makeText(context, "Video STARTED!", Toast.LENGTH_SHORT).show();
        }
    };
    //停止 录制视频的回调
    final Camera2Source.VideoStopCallback camera2SourceVideoStopCallback = new Camera2Source.VideoStopCallback() {
        @Override
        public void onVideoStop(String videoFile) {
            isRecordingVideo = false;
            runOnUiThread(() -> {
                binding.status.setText("video recording stopped");
                binding.switchButton.setEnabled(true);
                binding.takePictureButton.setEnabled(true);
                binding.videoButton.setEnabled(true);
                binding.videoButton.setText(getString(R.string.record_video));
            });
            Toast.makeText(context, "Video STOPPED!", Toast.LENGTH_SHORT).show();
        }
    };
    //录制视频错误的回调函数
    final Camera2Source.VideoErrorCallback camera2SourceVideoErrorCallback = new Camera2Source.VideoErrorCallback() {
        @Override
        public void onVideoError(String error) {
            isRecordingVideo = false;
            runOnUiThread(() -> {
                binding.status.setText("video recording error");
                binding.switchButton.setEnabled(true);
                binding.takePictureButton.setEnabled(true);
                binding.videoButton.setEnabled(true);
                binding.videoButton.setText(getString(R.string.record_video));
            });
            Toast.makeText(context, "Video Error: "+error, Toast.LENGTH_LONG).show();
        }
    };
    //相机拍照声的回调函数
    final Camera2Source.ShutterCallback camera2SourceShutterCallback = () -> {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                binding.status.setText("shutter event triggered");
                Log.d(TAG, "Shutter Callback for CAMERA2");
            }
        });
    };
    //相机拍照的回调函数
    final Camera2Source.PictureCallback camera2SourcePictureCallback = new Camera2Source.PictureCallback() {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onPictureTaken(Bitmap image) {
            Log.d(TAG, "Taken picture is ready!");

            runOnUiThread(() -> {
                binding.status.setText("picture taken");
                binding.switchButton.setEnabled(true);
                binding.videoButton.setEnabled(true);
                binding.takePictureButton.setEnabled(true);
            });
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "/camera2_picture.png"));
                image.compress(Bitmap.CompressFormat.JPEG, 95, out);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };
    //相机开启错误的回调函数
    final Camera2Source.CameraError camera2SourceErrorCallback = new Camera2Source.CameraError() {
        //相机打开正常
        @Override
        public void onCameraOpened() {
            runOnUiThread(() -> binding.status.setText("camera2 open success"));
        }
        @Override
        public void onCameraDisconnected() {}
        @Override
        public void onCameraError(int errorCode) {
            runOnUiThread(() -> {
                binding.status.setText(String.format(getString(R.string.errorCode)+" ", errorCode));
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setCancelable(false);
                builder.setTitle(getString(R.string.cameraError));
                builder.setMessage(String.format(getString(R.string.errorCode)+" ", errorCode));
                builder.setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    binding.switchButton.setEnabled(false);
                    binding.takePictureButton.setEnabled(false);
                    binding.videoButton.setEnabled(false);
                });
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
            });
        }
    };

    /**
     * 检查google play是否支持
     * @return
     */
    private boolean checkGooglePlayAvailability() {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
        if(resultCode == ConnectionResult.SUCCESS) {
            binding.status.setText("google play is available");
            return true;
        } else {
            if(googleApiAvailability.isUserResolvableError(resultCode)) {
                Objects.requireNonNull(googleApiAvailability.getErrorDialog(MainActivity.this, resultCode, 2404)).show();
            }
        }
        return false;
    }

    /**
     * 申请相机和读写权限
     */
    private void requestPermissionThenOpenCamera() {
        if(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                useCamera2 = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
                createCameraSourceFront();//权限判断完后，打开相机
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            }
        } else {
            binding.status.setText("requesting camera permission");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    /**
     * 按照系统版本和支持情况开启前置相机和人脸特征点提取和追踪
     */
    private void createCameraSourceFront() {
        previewFaceDetector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setMode(FaceDetector.FAST_MODE)
                .setProminentFaceOnly(true)//首要识别主要面部
                .setTrackingEnabled(true)
                .build();

        if(previewFaceDetector.isOperational()) {
            previewFaceDetector.setProcessor(new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory()).build());
        } else {
            Toast.makeText(context, "FACE DETECTION NOT AVAILABLE", Toast.LENGTH_SHORT).show();
            binding.status.setText("face detector not available");
        }

        if(useCamera2) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mCamera2Source = new Camera2Source.Builder(context, previewFaceDetector)
                        .setFocusMode(Camera2Source.CAMERA_AF_AUTO)//原始为CAMERA_AF_CONTINUOUS_PICTURE,自动对焦太频繁
                        .setFlashMode(Camera2Source.CAMERA_FLASH_AUTO)
                        .setFacing(Camera2Source.CAMERA_FACING_FRONT)
                        .build();

                //IF CAMERA2 HARDWARE LEVEL IS LEGACY, CAMERA2 IS NOT NATIVE.
                //WE WILL USE CAMERA1.
                if(mCamera2Source.isCamera2Native()) {
                    startCameraSource();
                } else {
                    useCamera2 = false;
                    if(usingFrontCamera) createCameraSourceFront(); else createCameraSourceBack();
                }
            }
        } else {
            mCameraSource = new CameraSource.Builder(context, previewFaceDetector)
                    .setFacing(CameraSource.CAMERA_FACING_FRONT)
                    .setFlashMode(CameraSource.CAMERA_FLASH_AUTO)
                    .setFocusMode(CameraSource.CAMERA_FOCUS_MODE_AUTO)//原始CAMERA_FOCUS_MODE_CONTINUOUS_PICTURE
                    .setRequestedFps(30.0f)
                    .build();

            startCameraSource();
        }
    }

    /**
     * 按照系统版本和支持情况开启后置
     * 相机和人脸特征点提取和追踪
     */
    private void createCameraSourceBack() {
        previewFaceDetector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setMode(FaceDetector.FAST_MODE)
                .setProminentFaceOnly(true)
                .setTrackingEnabled(true)
                .build();

        if(previewFaceDetector.isOperational()) {
            previewFaceDetector.setProcessor(new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory()).build());
        } else {
            binding.status.setText("face detector not available");
            Toast.makeText(context, "FACE DETECTION NOT AVAILABLE", Toast.LENGTH_SHORT).show();
        }

        if(useCamera2) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mCamera2Source = new Camera2Source.Builder(context, previewFaceDetector)
                        .setFocusMode(Camera2Source.CAMERA_AF_OFF)
                        .setFlashMode(Camera2Source.CAMERA_FLASH_AUTO)
                        .setFacing(Camera2Source.CAMERA_FACING_BACK)
                        .build();

                //IF CAMERA2 HARDWARE LEVEL IS LEGACY, CAMERA2 IS NOT NATIVE.
                //WE WILL USE CAMERA1.
                if(mCamera2Source.isCamera2Native()) {
                    startCameraSource();
                } else {
                    useCamera2 = false;
                    if(usingFrontCamera) createCameraSourceFront(); else createCameraSourceBack();
                }
            }
        } else {
            mCameraSource = new CameraSource.Builder(context, previewFaceDetector)
                    .setFacing(CameraSource.CAMERA_FACING_BACK)
                    .setFocusMode(CameraSource.CAMERA_FOCUS_MODE_CONTINUOUS_PICTURE)
                    .setFlashMode(CameraSource.CAMERA_FLASH_AUTO)
                    .setRequestedFps(30.0f)
                    .build();

            startCameraSource();
        }
    }
    /*
    开启相机预览，判断使用camera API还是camera2
     */
    private void startCameraSource() {
        if(useCamera2) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {//安卓版本大于5.0启用Camera2
                if (mCamera2Source != null) {
                    binding.cameraVersion.setText(context.getString(R.string.cameraTwo));
                    binding.mPreview.start(mCamera2Source, binding.mGraphicOverlay, camera2SourceErrorCallback);
                }
            }
        } else {
            if (mCameraSource != null) {
                binding.cameraVersion.setText(context.getString(R.string.cameraOne));
                binding.mPreview.start(mCameraSource, binding.mGraphicOverlay);
            }
        }
    }
    /*
    停止相机预览
     */
    private void stopCameraSource() {
        binding.mPreview.stop();
    }

    /*
    人脸跟踪，工厂类
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(@NonNull Face face) {
            return new GraphicFaceTracker(binding.mGraphicOverlay);
        }
    }
    /*
    内部类，人脸特征点追踪
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private final GraphicOverlay mOverlay;
        /*
        构造函数
         */
        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay, context);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, @NonNull Face item) {
            mFaceGraphic.setId(faceId);
            Log.d(TAG, "NEW FACE ID: "+faceId);
        }

        /**
         * 更新人脸特征点位置
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(@NonNull FaceDetector.Detections<Face> detectionResults, @NonNull Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
            mFaceGraphic.getPositions();
            mbitmap = mCamera2Source.getBitmap();
            Log.d(TAG, "onUpdate: mbitmap是否为null"+mbitmap.getHeight());
            bitFlag =true;
            System.out.println("face位置"+face.getWidth()+"---"+face.getHeight()+"---"+face.getPosition());
            BigDecimal b0= new BigDecimal(face.getEulerX());//获取欧拉角
            BigDecimal b1= new BigDecimal(face.getEulerY());
            BigDecimal b2= new BigDecimal(face.getEulerZ());
            mEulerX = b0.setScale(2,BigDecimal.ROUND_HALF_UP).floatValue();//保留两位小数，方便阅读
            mEulerY = b1.setScale(2,BigDecimal.ROUND_HALF_UP).floatValue();
            mEulerZ = b2.setScale(2,BigDecimal.ROUND_HALF_UP).floatValue();
            runOnUiThread(() -> {
                binding.eulerX.setText("上下点头:"+mEulerX);
                binding.eulerY.setText("左右转头:"+mEulerY);
                binding.eulerZ.setText("左右摆头:"+mEulerZ);
            });
            Log.d(TAG, "NEW KNOWN FACE UPDATE: "+face.getId());
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(@NonNull FaceDetector.Detections<Face> detectionResults) {
            mFaceGraphic.goneFace();
            mOverlay.remove(mFaceGraphic);
            runOnUiThread(() -> {
                binding.eulerX.setText("EulerX:"+0);//丢失
                binding.eulerY.setText("EulerY:"+0);
                binding.eulerZ.setText("EulerZ:"+0);
                Toast.makeText(MainActivity.this,"FACE MISSING",Toast.LENGTH_SHORT).show();
            });
            Log.d(TAG, "FACE MISSING");
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mFaceGraphic.goneFace();
            mOverlay.remove(mFaceGraphic);
            mOverlay.clear();
            Log.d(TAG, "FACE GONE");
        }
    }

    /**
     * 设置触摸点击对焦事件
     */
    private final CameraSourcePreview.OnTouchListener CameraPreviewTouchListener = new CameraSourcePreview.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent pEvent) {
            v.onTouchEvent(pEvent);
            if (pEvent.getAction() == MotionEvent.ACTION_DOWN) {
                int autoFocusX = (int) (pEvent.getX() - Utils.dpToPx(60)/2);
                int autoFocusY = (int) (pEvent.getY() - Utils.dpToPx(60)/2);
                binding.ivAutoFocus.setTranslationX(autoFocusX);
                binding.ivAutoFocus.setTranslationY(autoFocusY);
                binding.ivAutoFocus.setVisibility(View.VISIBLE);
                binding.ivAutoFocus.bringToFront();
                binding.status.setText("focusing...");
                if(useCamera2) {
                    if(mCamera2Source != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            //needs to know in which zone of the screen is auto focus requested
                            // some Camera2 devices support multi-zone focusing.
                            mCamera2Source.autoFocus(success -> runOnUiThread(() -> {
                                binding.ivAutoFocus.setVisibility(View.GONE);
                                binding.status.setText("focus OK");
                            }), pEvent, v.getWidth(), v.getHeight());
                        }
                    } else {
                        binding.ivAutoFocus.setVisibility(View.GONE);
                    }
                } else {
                    if(mCameraSource != null) {
                        mCameraSource.autoFocus(success -> runOnUiThread(() -> {
                            binding.ivAutoFocus.setVisibility(View.GONE);
                            binding.status.setText("focus OK");
                        }));
                    } else {
                        binding.ivAutoFocus.setVisibility(View.GONE);
                    }
                }
            }
            if(pEvent.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
                return true;
            }
            return false;
        }
    };

    /**
     * 权限申请结果判断
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestPermissionThenOpenCamera();
            } else {
                Toast.makeText(MainActivity.this, "CAMERA PERMISSION REQUIRED", Toast.LENGTH_LONG).show();
                finish();
            }
        }
        if(requestCode == REQUEST_STORAGE_PERMISSION) {
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestPermissionThenOpenCamera();
            } else {
                Toast.makeText(MainActivity.this, "STORAGE PERMISSION REQUIRED", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(wasActivityResumed)
            //If the CAMERA2 is paused then resumed, it won't start again unless creating the whole camera again.
            if(useCamera2) {
                if(usingFrontCamera) {
                    createCameraSourceFront();
                } else {
                    createCameraSourceBack();
                }
            } else {
                startCameraSource();
            }
    }

    @Override
    protected void onPause() {
        super.onPause();
        wasActivityResumed = true;
        stopCameraSource();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCameraSource();
        if(previewFaceDetector != null) {
            previewFaceDetector.release();
        }
    }
}