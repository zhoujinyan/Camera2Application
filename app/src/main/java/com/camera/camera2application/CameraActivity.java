package com.camera.camera2application;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import android.support.annotation.NonNull;
import android.view.View;

public class CameraActivity extends Activity {
    TextureView txtView, txtFrontView;
    /*** 相机管理类*/
    CameraManager mCameraManager;

    /*** 指定摄像头ID对应的Camera实体对象*/
    CameraDevice mCameraDevice,mCameraDevice0;
    /*** 打开摄像头的ID{@link CameraDevice}.*/
    private int mCameraId = CameraCharacteristics.LENS_FACING_FRONT;
    private int mCameraFrontId = CameraCharacteristics.LENS_FACING_BACK;

    /*** 处理静态图像捕获的ImageReader。{@link ImageReader}*/
    private ImageReader mImageReader, mImageReader0;

    /*** 用于相机预览的{@Link CameraCaptureSession}。*/
    private CameraCaptureSession mCaptureSession, mCaptureSession0;

    /*** {@link CaptureRequest.Builder}用于相机预览请求的构造器*/
    private CaptureRequest.Builder mPreviewRequestBuilder, mPreviewRequestBuilder0;

    /***预览请求, 由上面的构建器构建出来*/
    private CaptureRequest mPreviewRequest, mPreviewRequest0;
    /**
     * 文件存储路径
     */
    private File mFile, mFile0;
    /*** 用于运行不应阻塞UI的任务的附加线程。*/
    private HandlerThread mBackgroundThread , mBackgroundThread0;

    /*** 用于在后台运行任务的{@link Handler}。*/
    private Handler mBackgroundHandler, mFrontgroundHandler;
    private Size mPreviewSize, mPreviewSize0;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    /**
     * 从屏幕旋转图片转换方向。
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        initView();
    }

    private void initView() {
        txtView = (TextureView)findViewById(R.id.camera_texture_view);
        txtFrontView = (TextureView)findViewById(R.id.camera_front_texture_view);
        // 获取CameraManager 相机设备管理器
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    }
    /**
     * Closes the current {@link CameraDevice}.
     * 关闭正在使用的相机
     */
    private void closeCamera() {
        // 关闭捕获会话
        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        // 关闭当前相机
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        // 关闭拍照处理器
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
        // 关闭捕获会话
        if (null != mCaptureSession0) {
            mCaptureSession0.close();
            mCaptureSession0 = null;
        }
        // 关闭当前相机
        if (null != mCameraDevice0) {
            mCameraDevice0.close();
            mCameraDevice0 = null;
        }
        // 关闭拍照处理器
        if (null != mImageReader0) {
            mImageReader0.close();
            mImageReader0 = null;
        }
    }
    /**
     * TextureView 生命周期响应
     */
    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override //创建
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //当TextureView创建完成，打开指定摄像头相机
            openCamera(width, height, mCameraId);
            Log.d("zjy","onSurfaceTextureAvailable width"+width+",height:"+height+",mCameraId:"+mCameraId);
        }

        @Override //尺寸改变
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override //销毁
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override //更新
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };
    private final TextureView.SurfaceTextureListener textureFrontListener = new TextureView.SurfaceTextureListener() {
        @Override //创建
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //当TextureView创建完成，打开指定摄像头相机
            openCamera(width, height, mCameraFrontId);
            Log.d("zjy","onSurfaceTextureAvailable width"+width+",height:"+height+",mCameraId:"+mCameraFrontId);
        }

        @Override //尺寸改变
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override //销毁
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override //更新
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            mFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".jpg");
            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
        }

    };
    private final ImageReader.OnImageAvailableListener mOnFrontImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            mFile0 = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/" + new SimpleDateFormat("yyyy-MMddHHmmss").format(new Date()) + ".jpg");
            mFrontgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile0));
        }

    };

    /*** {@link CameraDevice.StateCallback}打开指定摄像头回调{@link CameraDevice}*/
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            String id = cameraDevice.getId();
            mCameraDevice = cameraDevice;
            createCameraPreview(mCameraId);
            Log.d("zjy","onOpened id:"+id);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
            Log.d("zjy","onDisconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            cameraDevice = null;
            Log.d("zjy","onError error:"+error);
        }

    };
    private final CameraDevice.StateCallback mFrontStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            String id = cameraDevice.getId();
            mCameraDevice0 = cameraDevice;
            createCameraPreview(mCameraFrontId);
            Log.d("zjy","onOpened id:"+id);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
            Log.d("zjy","onDisconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            cameraDevice = null;
            Log.d("zjy","onError error:"+error);
        }

    };

    /**
     * 打开指定摄像头ID的相机
     *
     * @param width
     * @param height
     * @param cameraId
     */
    private void openCamera(int width, int height, int cameraId) {

        try {
            mSurfaceWidth = width;
            mSurfaceHeight = height;
//            getCameraId(cameraId);
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId + "");
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            // 获取设备方向
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int totalRotation = sensorToDeviceRotation(characteristics, rotation);
            boolean swapRotation = totalRotation == 90 || totalRotation == 270;
            int rotatedWidth = mSurfaceWidth;
            int rotatedHeight = mSurfaceHeight;
            if (swapRotation) {
                rotatedWidth = mSurfaceHeight;
                rotatedHeight = mSurfaceWidth;
            }
            // 获取最佳的预览尺寸
            mPreviewSize = getPreferredPreviewSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
            if(cameraId == CameraCharacteristics.LENS_FACING_FRONT) {
                if (mImageReader == null) {
                    // 创建一个ImageReader对象，用于获取摄像头的图像数据,maxImages是ImageReader一次可以访问的最大图片数量
                    mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                            ImageFormat.JPEG, 2);
                    mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
                }
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    Activity#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for Activity#requestPermissions for more details.
                    Log.d("zjy","no camera permission");
                    return;
                }
                Log.d("zjy","opencamera");
                mCameraManager.openCamera(cameraId + "", mStateCallback, null);
            } else {
                if (mImageReader0 == null) {
                    // 创建一个ImageReader对象，用于获取摄像头的图像数据,maxImages是ImageReader一次可以访问的最大图片数量
                    mImageReader0 = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                            ImageFormat.JPEG, 2);
                    mImageReader0.setOnImageAvailableListener(mOnFrontImageAvailableListener, mFrontgroundHandler);
                }
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    Activity#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for Activity#requestPermissions for more details.
                    Log.d("zjy","no camera permission");
                    return;
                }
                Log.d("zjy","opencamera");
                mCameraManager.openCamera(cameraId + "", mFrontStateCallback, null);
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * 创建预览对话
     */
    private void createCameraPreview(int cameraId) {

        try {
            if(cameraId ==  CameraCharacteristics.LENS_FACING_FRONT) {
                // 获取texture实例
                SurfaceTexture surfaceTexture = txtView.getSurfaceTexture();
                assert surfaceTexture != null;
                //我们将默认缓冲区的大小配置为我们想要的相机预览的大小。
                surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                // 用来开始预览的输出surface
                Surface surface = new Surface(surfaceTexture);
                //创建预览请求构建器
                mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                //将TextureView的Surface作为相机的预览显示输出
                mPreviewRequestBuilder.addTarget(surface);
                //在这里，我们为相机预览创建一个CameraCaptureSession。
                mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                // 相机关闭时, 直接返回
                                if (null == mCameraDevice) {
                                    return;
                                }
                                //会话准备就绪后，我们开始显示预览。
                                // 会话可行时, 将构建的会话赋给field
                                mCaptureSession = cameraCaptureSession;

                                //相机预览应该连续自动对焦。
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                Log.d("zjy","createCaptureSession onConfigured");
                                // 构建上述的请求
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                // 重复进行上面构建的请求, 用于显示预览
                                try {
                                    mCaptureSession.setRepeatingRequest(mPreviewRequest, null, mBackgroundHandler);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {


                            }
                        }, null
                );
            } else {

                // 获取texture实例
                SurfaceTexture surfaceTexture = txtFrontView.getSurfaceTexture();
                assert surfaceTexture != null;
                //我们将默认缓冲区的大小配置为我们想要的相机预览的大小。
                surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                // 用来开始预览的输出surface
                Surface surface = new Surface(surfaceTexture);
                //创建预览请求构建器
                mPreviewRequestBuilder0 = mCameraDevice0.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                //将TextureView的Surface作为相机的预览显示输出
                mPreviewRequestBuilder0.addTarget(surface);
                //在这里，我们为相机预览创建一个CameraCaptureSession。
                mCameraDevice0.createCaptureSession(Arrays.asList(surface, mImageReader0.getSurface()), new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                // 相机关闭时, 直接返回
                                if (null == mCameraDevice0) {
                                    return;
                                }
                                //会话准备就绪后，我们开始显示预览。
                                // 会话可行时, 将构建的会话赋给field
                                mCaptureSession0 = cameraCaptureSession;

                                //相机预览应该连续自动对焦。
                                mPreviewRequestBuilder0.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                Log.d("zjy","createCaptureSession onConfigured");
                                // 构建上述的请求
                                mPreviewRequest0 = mPreviewRequestBuilder0.build();
                                // 重复进行上面构建的请求, 用于显示预览
                                try {
                                    mCaptureSession0.setRepeatingRequest(mPreviewRequest0, null, mFrontgroundHandler);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {


                            }
                        }, null
                );
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private static class ImageSaver implements Runnable {

        /**
         * The JPEG image
         * 要保存的图片
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         * 图片存储的路径
         */
        private final File mFile;

        ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    /**
     * 获取设备方向
     *
     * @param characteristics
     * @param deviceOrientation
     * @return
     */
    private static int sensorToDeviceRotation(CameraCharacteristics characteristics, int deviceOrientation) {
        int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        return (sensorOrientation + deviceOrientation + 360) % 360;
    }

    /**
     * 获取可用设备可用摄像头列表
     */
    private void getCameraId(int ID) {
        try {
            for (String cameraId : mCameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == ID) {
                    continue;
                }
                mCameraId = Integer.valueOf(cameraId);
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (txtView.isAvailable()) {
            openCamera(txtView.getWidth(), txtView.getHeight(), mCameraId);
        } else {
            txtView.setSurfaceTextureListener(textureListener);
        }
        if (txtFrontView.isAvailable()) {
            openCamera(txtFrontView.getWidth(), txtFrontView.getHeight(), mCameraFrontId);
        } else {
            txtFrontView.setSurfaceTextureListener(textureFrontListener);
        }
        startBackgroundThread();
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    /**
     * 初试化拍照线程
     */
    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        mBackgroundThread0 = new HandlerThread("Camera Frontground");
        mBackgroundThread0.start();
        mFrontgroundHandler = new Handler(mBackgroundThread0.getLooper());
    }

    public void stopBackgroundThread() {
        if (mBackgroundThread != null) {
            mBackgroundThread.quitSafely();
            try {
                mBackgroundThread.join();
                mBackgroundThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (mBackgroundThread0 != null) {
            mBackgroundThread0.quitSafely();
            try {
                mBackgroundThread0.join();
                mBackgroundThread0 = null;
                mFrontgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    public void onViewClicked(View view) {
        captureStillPicture();
    }

    /**
     * 拍照时调用方法
     */
    private void captureStillPicture() {
        try {
            if (mCameraDevice == null) {
                return;
            }
            // 创建作为拍照的CaptureRequest.Builder
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            // 将imageReader的surface作为CaptureRequest.Builder的目标
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());
/*            // 设置自动对焦模式
            mBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 设置自动曝光模式
            mBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);*/
            //设置为自动模式
//            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            // 停止连续取景
            mCaptureSession.stopRepeating();
            // 捕获静态图像
            mCaptureSession.capture(mPreviewRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                // 拍照完成时激发该方法
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    //重新打开预览
                    createCameraPreview(mCameraId);
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    /**
     * 设置最佳尺寸
     *
     * @param sizes
     * @param width
     * @param height
     * @return
     */
    private Size getPreferredPreviewSize(Size[] sizes, int width, int height) {
        List<Size> collectorSizes = new ArrayList<>();
        for (Size option : sizes) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    collectorSizes.add(option);
                }
            } else {
                if (option.getHeight() > width && option.getWidth() > height) {
                    collectorSizes.add(option);
                }
            }
        }
        if (collectorSizes.size() > 0) {
            return Collections.min(collectorSizes, new Comparator<Size>() {
                @Override
                public int compare(Size s1, Size s2) {
                    return Long.signum(s1.getWidth() * s1.getHeight() - s2.getWidth() * s2.getHeight());
                }
            });
        }
        return sizes[0];
    }


}
