package com.example.CameraXCapture;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    //CameraX
    ImageCapture imageCapture;
    ImageAnalysis imageAnalysis;
    ProcessCameraProvider cameraProvider;
    Preview preview;
    CameraSelector cameraSelector;

    //UI
    private PreviewView previewView;
    private ImageView ivPreviewPhoto;
    private LinearLayout llShotResult;
    private ImageView ivPhotoShot;
    private ImageView ivPhotoSave;
    private ImageView ivPhotoCancel;

    //Variable
    private final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
    private final int REQUEST_CODE_PERMISSIONS = 10;
    // path should start with '/'
    // or it will be '/storage/emulated/0CameraTester'
    private final String ROOT_FOLDER_NAME = "/CameraTester";
    private final String FILE_PATH = Environment.getExternalStorageDirectory() + ROOT_FOLDER_NAME + "/temp";
    private int rotateDegree = 0;
    public final ExecutorService service = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindUI();
        setListener();

        //permission check
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.ivPhotoShot) {
            //on shot
            takePhoto(imageCapture);
            showCapture();
            previewView.setVisibility(View.INVISIBLE);
            ivPhotoShot.setVisibility(View.INVISIBLE);
            llShotResult.setVisibility(View.VISIBLE);
            ivPreviewPhoto.setVisibility(View.VISIBLE);
        } else if (v.getId() == R.id.ivPhotoCancel) {
            //on cancel
            cameraProvider.unbind(imageAnalysis);
            ivPreviewPhoto.setImageBitmap(null);
            ivPreviewPhoto.setVisibility(View.INVISIBLE);
            llShotResult.setVisibility(View.INVISIBLE);
            ivPhotoShot.setVisibility(View.VISIBLE);
            previewView.setVisibility(View.VISIBLE);
            // TODO: 2021/4/26 Delete or something else 
        } else if (v.getId() == R.id.ivPhotoSave) {
            //on check
            Toast.makeText(this, "act finish.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void bindUI() {
        previewView = findViewById(R.id.previewView);
        llShotResult = findViewById(R.id.photoResult);
        ivPreviewPhoto = findViewById(R.id.captureView);
        ivPhotoShot = findViewById(R.id.ivPhotoShot);
        ivPhotoSave = findViewById(R.id.ivPhotoSave);
        ivPhotoCancel = findViewById(R.id.ivPhotoCancel);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setListener() {
        ivPhotoShot.setOnClickListener(this);
        ivPhotoCancel.setOnClickListener(this);
        ivPhotoSave.setOnClickListener(this);

        ivPhotoShot.setOnTouchListener((view, motionEvent) -> {
            //shutter
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                ivPhotoShot.setImageResource(R.drawable.shot_active);
            } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                ivPhotoShot.setImageResource(R.drawable.shot);
            }
            return false;
        });
    }

    private boolean allPermissionsGranted() {
        boolean pass = true;
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                pass = false;
            }
        }
        if (checkFolder()) {
            Log.d("???", "Folder existed or unable to create.");
        }
        return pass;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderListenableFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderListenableFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderListenableFuture.get();
                preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.createSurfaceProvider());
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                imageCapture = new ImageCapture.Builder().build();

                //for rotation
                OrientationEventListener orientationEventListener = new OrientationEventListener(this) {
                    @Override
                    public void onOrientationChanged(int orientation) {
                        int rotation;

                        // Monitors orientation values to determine the target rotation value
                        if (orientation >= 45 && orientation < 135) {
                            rotation = Surface.ROTATION_270;
                            rotateDegree = 180;
                        } else if (orientation >= 135 && orientation < 225) {
                            rotation = Surface.ROTATION_180;
                            rotateDegree = 270;
                        } else if (orientation >= 225 && orientation < 315) {
                            rotation = Surface.ROTATION_90;
                            rotateDegree = 0;
                        } else {
                            rotation = Surface.ROTATION_0;
                            rotateDegree = 90;
                        }

                        /**
                         * only save info in EXIF
                         * If you also want to rotate width and height, use processPhoto() and disable this function
                         */
                        // imageCapture.setTargetRotation(rotation);
                    }
                };
                orientationEventListener.enable();

                //init service
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto(ImageCapture imageCapture) {
        String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
        File photoFile = new File(FILE_PATH, new SimpleDateFormat(FILENAME_FORMAT, Locale.TAIWAN).format(System.currentTimeMillis()) + ".jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                service.submit(() -> {
                    processPhoto();
                });
                Uri savedUri = Uri.fromFile(photoFile);
                String msg = "Photo capture succeeded: " + savedUri.getPath();
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                exception.printStackTrace();
            }
        });
    }

    private void showCapture() {
        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), image -> {
            //Two choice:
            //1.Use imageAnalysis, but it will capture preview after shot(won't be same frame)
            //2.Use open file method, preview will be same but takes time.
            ivPreviewPhoto.setImageBitmap(toBitmap(image));
        });
        cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview);
    }


    /**
     * folder check
     *
     * @return isSuccess
     */
    private boolean checkFolder() {
        boolean isSuccess = true;
        String path = Environment.getExternalStorageDirectory() + ROOT_FOLDER_NAME;

        //root folder
        isSuccess = isSuccess && createFolder(path);

        //subFolder
        isSuccess = isSuccess && createFolder(path + "/Camera");
        isSuccess = isSuccess && createFolder(path + "/temp");
        return isSuccess;
    }

    private boolean createFolder(String path) {
        File newDirectory = new File(path);
        if (!newDirectory.exists()) {
            return newDirectory.mkdir();
        } else {
            return false;
        }
    }

    //Util
    private Bitmap toBitmap(ImageProxy image) {
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer vuBuffer = image.getPlanes()[2].getBuffer();

        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = yBuffer.remaining();
        int vuSize = vuBuffer.remaining();

        byte[] nv21 = new byte[ySize + vuSize];
        yBuffer.get(nv21, 0, ySize);
        vuBuffer.get(nv21, ySize, vuSize);
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 50, out);
        byte[] imageBytes = out.toByteArray();

        //rotate
        Bitmap bitmapOrg = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        Matrix matrix = new Matrix();
        matrix.postRotate(rotateDegree);
        return Bitmap.createBitmap(bitmapOrg, 0, 0, bitmapOrg.getWidth(), bitmapOrg.getHeight(), matrix, true);
    }

    private void processPhoto() {
        Bitmap fileBitmap = BitmapFactory.decodeFile(FILE_PATH);
        Matrix matrix = new Matrix();
        matrix.postRotate(rotateDegree);//it will conflict to 'setTargetRotation', choose one which you need
        Bitmap resultBitmap = Bitmap.createBitmap(fileBitmap, 0, 0, fileBitmap.getWidth(), fileBitmap.getHeight(), matrix, true);

        try {
            File file = new File(FILE_PATH);
            FileOutputStream outputStream = new FileOutputStream(file);
            if (resultBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)) {
                outputStream.flush();
                outputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
