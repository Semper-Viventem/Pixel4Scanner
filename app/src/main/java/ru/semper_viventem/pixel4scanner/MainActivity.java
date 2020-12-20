package ru.semper_viventem.pixel4scanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.StateCallback;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureRequest.Builder;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.common.primitives.Floats;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import ru.semper_viventem.pixel4scanner.libs.renderer.DefaultRenderConfiguration;
import ru.semper_viventem.pixel4scanner.libs.renderer.OnTouchEventListener;
import ru.semper_viventem.pixel4scanner.libs.renderer.PointCloudRenderer;
import ru.semper_viventem.pixel4scanner.libs.renderer.PointCloudRendererView;
import ru.semper_viventem.pixel4scanner.ultradepth.apps.frame.Frame;
import ru.semper_viventem.pixel4scanner.ultradepth.apps.frame.FrameReader;

/**
 * This is class based on decompiled sources of uDepth demo.
 * This is a non-commercial project implemented for educational purposes only.
 *
 * @see "https://ai.googleblog.com/2020/04/udepth-real-time-3d-depth-sensing-on.html"
 */
public class MainActivity extends AppCompatActivity implements OnTouchEventListener {
    private static final float CAMERA_TO_DEPTH_SCALE = 0.195f;
    private static final String DEPTH_CAMERA_ID = "1";
    private static final float MAX_VISIBLE_DEPTH_MM = 750.0f;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final String TAG = "UltradepthDemoActivity";
    private static final float[] kCmapBlueCoefs = {-0.08097428f, 4.7317576f, 7.2276936f, -60.616283f, 82.75632f, -34.06507f};
    private static final float[] kCmapGreenCoefs = {0.05766247f, -2.962136f, 36.10218f, -75.6867f, 50.487343f, -7.858784f};
    private static final float[] kCmapRedCoefs = {-0.04055352f, 2.991828f, -25.613377f, 98.59276f, -133.13583f, 57.30011f};
    private static int numBufferedFrames = 1;
    private static int numBufferedImages = 20;
    public Handler backgroundHandler;
    private HandlerThread backgroundThread;
    public CameraCaptureSession cameraCaptureSessions;
    public CameraDevice cameraDevice;
    private TextureView cameraView;
    private ImageReader depthImageReader;
    private ImageView depthMapView;
    private long depthTimestamp = 0;
    private float distortionK1 = 0.0f;
    private float distortionK2 = 0.0f;
    private float distortionK3 = 0.0f;
    private float distortionP1 = 0.0f;
    private float distortionP2 = 0.0f;
    private float focalLengthX = 2053.0f;
    private float focalLengthY = 2053.0f;
    private Frame[] frameBuffer;
    private int frameBufferCounter = 0;
    private FrameReader frameReader;
    private PointCloudRendererView glView;
    private final Object imageCallbackMutex = new Object();
    private Boolean isGlViewInitialized = Boolean.FALSE;
    private float principalPointX = 320.0f;
    private float principalPointY = 240.0f;
    private ImageReader rgbImageReader;
    private boolean isCaptured = false;
    private Bitmap actualDepthBitmap = null;
    private Bitmap actualPhotoBitmap = null;
    private float[] actualpoints;
 

    SurfaceTextureListener textureListener = new SurfaceTextureListener() {
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            MainActivity.this.openCamera();
        }

        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
        }

        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }
    };
    private final float[] undistortionMaps = new float[614400];
    private VisualizationMode visualizationMode = VisualizationMode.RENDER_POINT_CLOUD;
    private long yuvTimestamp = 0;

    private enum VisualizationMode {
        RENDER_POINT_CLOUD_COLOR,
        RENDER_POINT_CLOUD,
        RENDER_DEPTH_MAP_WITH;

        public VisualizationMode getNext() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public void onTouchEvent(MotionEvent event, float x, float y) {
        if (!this.isGlViewInitialized) {
            Log.i(TAG, "onTouch placholder for interactions.");
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        parseAppExtras();
        setGlobals();
        requestWindowFeature(1);
        getWindow().setFlags(1024, 1024);
        setContentView(R.layout.main);
        findViewById(R.id.mainView).setBackgroundColor(getPerceptColoring(0.0f));
        findViewById(R.id.modeButton).setOnClickListener(v -> switchMode());
        findViewById(R.id.captureButton).setOnClickListener(v -> switchCaptured());
        findViewById(R.id.shareButton).setOnClickListener(v -> shareDepthMap());
        findViewById(R.id.licenseButton).setOnClickListener(v -> startActivity(LicenseActivity.Companion.getInstance(this)));
        ImageView imageView = findViewById(R.id.depthMapView);
        this.depthMapView = imageView;
        imageView.setMaxHeight(DefaultRenderConfiguration.IMAGE_WIDTH);
        this.depthMapView.setMaxWidth(DefaultRenderConfiguration.IMAGE_HEIGHT);
        TextureView textureView = findViewById(R.id.cameraView);
        this.cameraView = textureView;
        textureView.setSurfaceTextureListener(this.textureListener);
        this.glView = findViewById(R.id.pointcloudView);
        PointCloudRenderer.Config rendererConfig = new PointCloudRenderer.Config();
        rendererConfig.showPictureInPicture = true;
        rendererConfig.minDepth = 0.2f;
        rendererConfig.maxDepth = 0.6f;
        this.glView.setDepthProperties(rendererConfig);
        this.glView.setCaptureMode(false);
        this.glView.registerOnTouchEventListener(this);
        this.isGlViewInitialized = Boolean.valueOf(true);
        getWindow().addFlags(128);
    }

    public void onResume() {
        Log.i(TAG, "onResume");
        super.onResume();
        if (this.cameraView.isAvailable()) {
            openCamera();
        } else {
            this.cameraView.setSurfaceTextureListener(this.textureListener);
        }
        this.glView.onResume();
    }

    public void onPause() {
        Log.i(TAG, "onPause");
        super.onPause();
        closeCamera();
        this.glView.onPause();
    }

    public void onDestroy() {
        closeCamera();
        super.onDestroy();
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults[0] != 0) {
            Toast.makeText(this, "Camera denied.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void parseAppExtras() {
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String str = TAG;
            Log.i(str, "App extras found.");
            String str2 = "num_buffered_frames";
            if (extras.containsKey(str2)) {
                int parseInt = Integer.parseInt(extras.getString(str2));
                numBufferedFrames = parseInt;
                Log.i(str, "Setting numBufferedFrames: " + parseInt);
            }
            String str3 = "num_buffered_images";
            if (extras.containsKey(str3)) {
                int parseInt2 = Integer.parseInt(extras.getString(str3));
                numBufferedImages = parseInt2;
                Log.i(str, "Setting numBufferedImages: " + parseInt2);
            }
        }
    }

    private void setGlobals() {
        this.frameBuffer = new Frame[numBufferedFrames];
    }

    private void stepThroughRgbFrames() {
        if (this.yuvTimestamp <= this.depthTimestamp) {
            Image image = this.rgbImageReader.acquireNextImage();
            while (image != null) {
                this.yuvTimestamp = image.getTimestamp();
                this.frameReader.onYuvImageAvailable(image);
                image.close();
                if (this.yuvTimestamp <= this.depthTimestamp) {
                    image = this.rgbImageReader.acquireNextImage();
                } else {
                    image = null;
                }
            }
        }
    }

    public void createCameraPreview() {
        try {
            FrameReader newInstance = FrameReader.newInstance(DefaultRenderConfiguration.IMAGE_WIDTH, DefaultRenderConfiguration.IMAGE_HEIGHT, DefaultRenderConfiguration.IMAGE_WIDTH, DefaultRenderConfiguration.IMAGE_HEIGHT, numBufferedFrames);
            this.frameReader = newInstance;
            newInstance.setOnFrameAvailableListener(this::onFrameAvailable);
            this.rgbImageReader = ImageReader.newInstance(DefaultRenderConfiguration.IMAGE_WIDTH, DefaultRenderConfiguration.IMAGE_HEIGHT, 35, numBufferedImages);
            this.depthImageReader = ImageReader.newInstance(DefaultRenderConfiguration.IMAGE_WIDTH, DefaultRenderConfiguration.IMAGE_HEIGHT, 1144402265, numBufferedImages);
            this.rgbImageReader.setOnImageAvailableListener(this::onRgbImageAvilable, this.backgroundHandler);
            this.depthImageReader.setOnImageAvailableListener(this::onDepthImageAvilable, this.backgroundHandler);
            SurfaceTexture cameraTexture = this.cameraView.getSurfaceTexture();
            cameraTexture.setDefaultBufferSize(this.depthImageReader.getWidth(), this.depthImageReader.getHeight());
            Surface previewSurface = new Surface(cameraTexture);
            Surface depthSurface = this.depthImageReader.getSurface();
            Surface rgbSurface = this.rgbImageReader.getSurface();
            final Builder captureRequestBuilder = this.cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(rgbSurface);
            captureRequestBuilder.addTarget(depthSurface);
            captureRequestBuilder.addTarget(previewSurface);
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, Integer.valueOf(1));
            captureRequestBuilder.set(CaptureRequest.DISTORTION_CORRECTION_MODE, Integer.valueOf(0));
            this.cameraDevice.createCaptureSession(Arrays.asList(new Surface[]{rgbSurface, depthSurface, previewSurface}), new StateCallback() {
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    if (MainActivity.this.cameraDevice == null) {
                        Toast.makeText(MainActivity.this, "NO CAMERA", Toast.LENGTH_LONG).show();
                        return;
                    }
                    MainActivity.this.cameraCaptureSessions = cameraCaptureSession;
                    try {
                        MainActivity.this.cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, MainActivity.this.backgroundHandler);
                    } catch (CameraAccessException e) {
                        MainActivity.logError(e);
                    }
                }

                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration Failed", Toast.LENGTH_LONG).show();
                }
            }, this.backgroundHandler);
        } catch (CameraAccessException e) {
            logError(e);
        }
    }

    private void onFrameAvailable(FrameReader reader) {
        Frame frame = reader.acquireLatestFrame();

        if (frame != null) {
            if (!isCaptured) {
                Bitmap bitmap = processDepthMap(frame);
                Matrix matrix = new Matrix();
                matrix.postRotate(270.0f);
                matrix.postScale(-1.0f, 1.0f, ((float) bitmap.getWidth()) / 2.0f, ((float) bitmap.getHeight()) / 2.0f);
                Bitmap oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                bitmap.recycle();
                runOnUiThread(() -> depthMapView.setImageBitmap(oriented));
            }

            Frame[] frameArr = this.frameBuffer;
            int i = this.frameBufferCounter;
            int i2 = i + 1;
            this.frameBufferCounter = i2;
            frameArr[i] = frame;
            if (i2 == numBufferedFrames) {
                for (int i3 = 0; i3 < numBufferedFrames; i3++) {
                    this.frameBuffer[i3].close();
                }
                this.frameBufferCounter = 0;
            }
        }
    }

    private void onRgbImageAvilable(ImageReader reader) {
        synchronized (this.imageCallbackMutex) {
            try {
                stepThroughRgbFrames();
            } catch (IllegalStateException ex) {
                logError(ex);
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    private void onDepthImageAvilable(ImageReader reader) {
        synchronized (this.imageCallbackMutex) {
            try {
                stepThroughRgbFrames();
                if (this.depthTimestamp <= this.yuvTimestamp) {
                    Image image = this.depthImageReader.acquireLatestImage();
                    if (image != null) {
                        this.depthTimestamp = image.getTimestamp();
                        this.frameReader.onDepth16ImageAvailable(image);
                        image.close();
                    }
                }
                stepThroughRgbFrames();
            } catch (IllegalStateException ex) {
                logError(ex);
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public float getDepthFromRawData(short rawDepth) {
        if (rawDepth <= 0) {
            return 1.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, ((float) rawDepth) / MAX_VISIBLE_DEPTH_MM));
    }

    public int getMonochromeColoring(float value) {
        float x = Math.max(0.0f, Math.min(0.3f, value - 0.3f));
        float channel = x / 0.3f;
        return Color.rgb(channel, channel, channel);
    }

    public int getMonochromeColoringWithRedFactors(float value) {
        float x = Math.max(0.0f, Math.min(0.3f, value - 0.3f));
        float channel = x / 0.3f;
        if (channel <= 0.0f || channel >= 1.0f) return Color.RED;
        return Color.rgb(channel, channel, channel);
    }

    public int getPerceptColoring(float value) {
        float x = Math.max(0.0f, Math.min(1.0f, value));
        float x2 = x * x;
        float x4 = x2 * x2;
        float[] xPowers = {1.0f, x, x2, x * x2, x4, x * x4};
        float red = 0.0f;
        float green = 0.0f;
        float blue = 0.0f;
        for (int i = 0; i < 6; i++) {
            red += kCmapRedCoefs[i] * xPowers[i];
            green += kCmapGreenCoefs[i] * xPowers[i];
            blue += kCmapBlueCoefs[i] * xPowers[i];
        }
        return Color.argb(1.0f, Floats.constrainToRange(red, 0.0f, 1.0f), Floats.constrainToRange(green, 0.0f, 1.0f), Floats.constrainToRange(blue, 0.0f, 1.0f));
    }

    private Bitmap processDepthMap(Frame frame) {
        short[] depth16Data;
        int width = frame.getDepth16Width();
        int height = frame.getDepth16Height();
        short[] depth16Data2 = frame.getDepth16Data();
        int[] imgArray = new int[(width * height)];
        int[] depthMapArray = new int[(width * height)];
        int[] originalPhotoArray = new int[(width * height)];
        float[] points = new float[(width * height * 7)];
        int numPoints = 0;
        int i = 0;
        boolean isCameraSize = frame.getYuvWidth() == width && frame.getYuvHeight() == height;
        int y = 0;
        while (true) {
            float f = 0.0f;
            if (y >= Math.max(i, height - 100)) {
                break;
            }
            int x = 0;
            while (x < width) {
                int pixel = (y * width) + x;
                short y16 = depth16Data2[pixel];
                short rawDepth = (short) (y16 & 8191);
                float scaledDepth = 1.0f - Floats.constrainToRange(((float) rawDepth) / MAX_VISIBLE_DEPTH_MM, f, 1.0f);
                float depth = getDepthFromRawData(rawDepth);
                if ((depth > 0.6f && depth < 0.3f && visualizationMode == VisualizationMode.RENDER_DEPTH_MAP_WITH) || depth > 0.8f) {
                    depth16Data = depth16Data2;
                } else {
                    int index = numPoints * 7;
                    int mapXPos = ((y * DefaultRenderConfiguration.IMAGE_WIDTH) + x) * 2;
                    int mapYPos = mapXPos + 1;
                    float[] fArr = this.undistortionMaps;
                    points[index] = fArr[mapXPos] * depth;
                    points[index + 1] = fArr[mapYPos] * depth;
                    depth16Data = depth16Data2;
                    points[index + 1] = -points[index + 1];
                    points[index + 2] = depth;
                    if (isCameraSize && visualizationMode == VisualizationMode.RENDER_POINT_CLOUD_COLOR) {
                        byte[] yuvData = frame.getYuvData();
                        float yVal = yuvData[pixel];
                        float uVal = yuvData[pixel + (width * height)];
                        float vVal = yuvData[pixel + (width * height * 2)];
                        float yVal2 = (yVal < 0.0f ? yVal + 256.0f : yVal) - 16.0f;
                        float uVal2 = (uVal < 0.0f ? uVal + 256.0f : uVal) - 128.0f;
                        float vVal2 = (vVal < 0.0f ? vVal + 256.0f : vVal) - 128.0f;
                        float rVal = (yVal2 * 1.164f) + (1.596f * vVal2);
                        float gVal = ((yVal2 * 1.164f) - (0.813f * vVal2)) - (0.391f * uVal2);
                        float bVal = (1.164f * yVal2) + (2.018f * uVal2);

                        points[index + 3] = Floats.constrainToRange(rVal / 255.0f, 0.0f, 1.0f);
                        points[index + 4] = Floats.constrainToRange(gVal / 255.0f, 0.0f, 1.0f);
                        points[index + 5] = Floats.constrainToRange(bVal / 255.0f, 0.0f, 1.0f);
                        originalPhotoArray[pixel] = Color.rgb(points[index + 3], points[index + 4], points[index + 5]);
                        imgArray[pixel] = getPerceptColoring(scaledDepth);
                        depthMapArray[pixel] = getMonochromeColoring(scaledDepth);
                    } else if (isCameraSize && visualizationMode == VisualizationMode.RENDER_DEPTH_MAP_WITH) {
                        byte[] yuvData = frame.getYuvData();
                        float yVal = yuvData[pixel];
                        float uVal = yuvData[pixel + (width * height)];
                        float vVal = yuvData[pixel + (width * height * 2)];
                        float yVal2 = (yVal < 0.0f ? yVal + 256.0f : yVal) - 16.0f;
                        float uVal2 = (uVal < 0.0f ? uVal + 256.0f : uVal) - 128.0f;
                        float vVal2 = (vVal < 0.0f ? vVal + 256.0f : vVal) - 128.0f;
                        float rVal = (yVal2 * 1.164f) + (1.596f * vVal2);
                        float gVal = ((yVal2 * 1.164f) - (0.813f * vVal2)) - (0.391f * uVal2);
                        float bVal = (1.164f * yVal2) + (2.018f * uVal2);

                        float r = Floats.constrainToRange(rVal / 255.0f, 0.0f, 1.0f);
                        float g = Floats.constrainToRange(gVal / 255.0f, 0.0f, 1.0f);
                        float b = Floats.constrainToRange(bVal / 255.0f, 0.0f, 1.0f);
                        originalPhotoArray[pixel] = Color.rgb(r, g, b);

                        imgArray[pixel] = getMonochromeColoringWithRedFactors(scaledDepth);
                        depthMapArray[pixel] = getMonochromeColoring(scaledDepth);
                        points[index + 3] = ((float) Color.red(imgArray[pixel])) / 255.0f;
                        points[index + 4] = ((float) Color.green(imgArray[pixel])) / 255.0f;
                        points[index + 5] = ((float) Color.blue(imgArray[pixel])) / 255.0f;
                    } else {
                        imgArray[pixel] = getPerceptColoring(scaledDepth);
                        depthMapArray[pixel] = getMonochromeColoring(scaledDepth);
                        points[index + 3] = ((float) Color.red(imgArray[pixel])) / 255.0f;
                        points[index + 4] = ((float) Color.green(imgArray[pixel])) / 255.0f;
                        points[index + 5] = ((float) Color.blue(imgArray[pixel])) / 255.0f;
                    }
                    points[index + 6] = 1.0f;
                    numPoints++;
                }
                x++;
                depth16Data2 = depth16Data;
                f = 0.0f;
            }
            y++;
            i = 0;
        }
        if (numPoints > 0) {
            actualpoints = Arrays.copyOf(points, numPoints * 7);
            this.glView.setPoints(Arrays.copyOf(points, numPoints * 7));
        } else {
            Arrays.fill(points, 0.0f);
            this.glView.setPoints(points);
        }

        Bitmap photoBitmap = Bitmap.createBitmap(originalPhotoArray, width, height, Bitmap.Config.ARGB_8888);
        Matrix matrix = new Matrix();
        matrix.postRotate(270.0f);
        matrix.postScale(-1.0f, 1.0f, ((float) photoBitmap.getWidth()) / 2.0f, ((float) photoBitmap.getHeight()) / 2.0f);
        actualPhotoBitmap = Bitmap.createBitmap(photoBitmap, 0, 0, photoBitmap.getWidth(), photoBitmap.getHeight(), matrix, true);


        Bitmap depthBitmap = Bitmap.createBitmap(depthMapArray, width, height, Bitmap.Config.ARGB_8888);
        matrix.reset();
        matrix.postRotate(270.0f);
        matrix.postScale(-1.0f, 1.0f, ((float) depthBitmap.getWidth()) / 2.0f, ((float) depthBitmap.getHeight()) / 2.0f);
        actualDepthBitmap = Bitmap.createBitmap(depthBitmap, 0, 0, depthBitmap.getWidth(), depthBitmap.getHeight(), matrix, true);

        return Bitmap.createBitmap(imgArray, width, height, Bitmap.Config.ARGB_8888);
    }

    public void openCamera() {
        String str = ", ";
        String str2 = TAG;
        String str3 = "android.permission.CAMERA";
        String str4 = DEPTH_CAMERA_ID;
        HandlerThread handlerThread = new HandlerThread("Camera background");
        this.backgroundThread = handlerThread;
        handlerThread.start();
        this.backgroundHandler = new Handler(this.backgroundThread.getLooper());
        this.cameraView.setSurfaceTextureListener(this.textureListener);
        this.frameBufferCounter = 0;
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!Arrays.asList(manager.getCameraIdList()).contains(str4)) {
                Toast.makeText(this, "Depth camera not available. Wait 3s.", Toast.LENGTH_SHORT).show();
            } else if (ActivityCompat.checkSelfPermission(this, str3) != 0) {
                ActivityCompat.requestPermissions(this, new String[]{str3}, REQUEST_CAMERA_PERMISSION);
            } else {
                CameraDevice.StateCallback r3 = new CameraDevice.StateCallback() {
                    public void onOpened(@NotNull CameraDevice camera) {
                        MainActivity.this.cameraDevice = camera;
                        MainActivity.this.createCameraPreview();
                    }

                    public void onDisconnected(@NotNull CameraDevice camera) {
                        Toast.makeText(MainActivity.this, "Depth camera closed.", Toast.LENGTH_SHORT).show();
                        MainActivity.this.cameraDevice.close();
                    }

                    public void onError(@NotNull CameraDevice camera, int error) {
                        Toast.makeText(MainActivity.this, "Cannot open depth camera.", Toast.LENGTH_SHORT).show();
                        if (MainActivity.this.cameraDevice != null) {
                            MainActivity.this.cameraDevice.close();
                            MainActivity.this.cameraDevice = null;
                        }
                    }
                };
                manager.openCamera(str4, r3, null);
                try {
                    CameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics("0");
                    if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) != 0) {
                        try {
                            cameraCharacteristics = manager.getCameraCharacteristics(str4);
                        } catch (CameraAccessException e2) {
                            Exception e = e2;
                            String str5 = "Failed to get intrinsics: ";
                            String valueOf = String.valueOf(e.getMessage());
                            Log.e(str2, valueOf.length() == 0 ? str5.concat(valueOf) : str5);
                        }
                    }
                    float[] fArr = cameraCharacteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION);
                    float f = fArr[0] * CAMERA_TO_DEPTH_SCALE;
                    this.focalLengthX = f;
                    float f2 = fArr[1] * CAMERA_TO_DEPTH_SCALE;
                    this.focalLengthY = f2;
                    float f3 = fArr[2] * CAMERA_TO_DEPTH_SCALE;
                    this.principalPointX = f3;
                    float f4 = fArr[3] * CAMERA_TO_DEPTH_SCALE;
                    this.principalPointY = f4;
                    Log.i(str2, "Intrinsics: Focal length: " + f + str + f2 + "; Principal point: " + f3 + str + f4);
                    float[] distortionCoeffs = (float[]) cameraCharacteristics.get(CameraCharacteristics.LENS_DISTORTION);
                    this.distortionK1 = distortionCoeffs[0];
                    this.distortionK2 = distortionCoeffs[1];
                    this.distortionK3 = distortionCoeffs[2];
                    this.distortionP1 = distortionCoeffs[3];
                    this.distortionP2 = distortionCoeffs[4];
                    for (int y = 0; y < 480; y++) {
                        int x = 0;
                        while (x < 640) {
                            int mapXPos = ((y * DefaultRenderConfiguration.IMAGE_WIDTH) + x) * 2;
                            int mapYPos = mapXPos + 1;
                            float xf = (((float) x) - this.principalPointX) / this.focalLengthX;
                            float yf = (((float) y) - this.principalPointY) / this.focalLengthY;
                            float xx = xf * xf;
                            float xy = xf * yf;
                            float yy = yf * yf;
                            float r2 = xx + yy;
                            float r4 = r2 * r2;
                            float[] distortionCoeffs2 = distortionCoeffs;
                            float radialDistortion = (this.distortionK1 * r2) + 1.0f + (this.distortionK2 * r4) + (this.distortionK3 * r4 * r2);
                            CameraDevice.StateCallback r222 = r3;
                            CameraManager manager2 = manager;
                            float tangentialDistortionY = (this.distortionP2 * 2.0f * xy) + (this.distortionP1 * (r2 + (2.0f * yy)));
                            this.undistortionMaps[mapXPos] = (xf * radialDistortion) + (this.distortionP1 * 2.0f * xy) + (this.distortionP2 * (r2 + (xx * 2.0f)));
                            this.undistortionMaps[mapYPos] = (yf * radialDistortion) + tangentialDistortionY;
                            x++;
                            distortionCoeffs = distortionCoeffs2;
                            r3 = r222;
                            manager = manager2;
                        }
                    }
                } catch (CameraAccessException e6) {
                    Exception e = e6;
                    String str5222 = "Failed to get intrinsics: ";
                    String valueOf222 = String.valueOf(e.getMessage());
                    Log.e(str2, valueOf222.length() == 0 ? str5222.concat(valueOf222) : str5222);
                }
            }
        } catch (CameraAccessException e7) {
            Exception e = e7;
            logError(e);
        }
    }

    private void closeCamera() {
        CameraDevice cameraDevice2 = this.cameraDevice;
        if (cameraDevice2 != null) {
            cameraDevice2.close();
            this.cameraDevice = null;
        }
        CameraCaptureSession cameraCaptureSession = this.cameraCaptureSessions;
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
        }
        ImageReader imageReader = this.depthImageReader;
        if (imageReader != null) {
            imageReader.close();
        }
        ImageReader imageReader2 = this.rgbImageReader;
        if (imageReader2 != null) {
            imageReader2.close();
        }
        HandlerThread handlerThread = this.backgroundThread;
        if (handlerThread != null) {
            try {
                handlerThread.quitSafely();
                this.backgroundThread.join();
                this.backgroundThread = null;
                this.backgroundHandler = null;
            } catch (InterruptedException e) {
                logError(e);
            }
        }
    }

    private void switchMode() {
        this.visualizationMode = this.visualizationMode.getNext();
        this.depthMapView.setVisibility(View.INVISIBLE);
        this.glView.setVisibility(View.VISIBLE);
    }

    private void switchCaptured() {
        isCaptured = !isCaptured;
        if (isCaptured) {
            ((Button) findViewById(R.id.captureButton)).setText(R.string.release);

        } else {
            ((Button) findViewById(R.id.captureButton)).setText(R.string.capture);
        }
    }

    private void shareDepthMap() {
        StringBuilder xyz = new StringBuilder();
        for (int i = 0; i < actualpoints.length ; i+= 7) {
            xyz.append(String.valueOf(actualpoints[i+0]));
            xyz.append(";");
            xyz.append(String.valueOf(actualpoints[i+1]));
            xyz.append(";");
            xyz.append(String.valueOf(actualpoints[i+2]));
            xyz.append(";");
            xyz.append(String.valueOf((int)(actualpoints[i+3]*255)));
            xyz.append(";");
            xyz.append(String.valueOf((int)(actualpoints[i+4]*255)));
            xyz.append(";");
            xyz.append(String.valueOf((int)(actualpoints[i+5]*255)));
            xyz.append("\n");

        }
        String xyzstr = xyz.toString();
        ShareUtilsKt.shareBitmapAsImage(this, actualDepthBitmap, actualPhotoBitmap,xyzstr);
    }

    public static void logError(Exception e) {
        String message = e.getMessage();
        String arrays = Arrays.toString(e.getStackTrace());
        Log.e(TAG, message + "\n" + arrays);
    }
}
