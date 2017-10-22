/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gms.samples.vision.face.facetracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.samples.vision.face.facetracker.ui.camera.CameraSourcePreview;
import com.google.android.gms.samples.vision.face.facetracker.ui.camera.GraphicOverlay;
import com.google.android.youtube.player.YouTubeBaseActivity;
import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerView;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.IOException;

/**
 * Activity for the face tracker app.  This app detects faces with the rear facing camera, and draws
 * overlay graphics to indicate the position, size, and ID of each face.
 */
public final class FaceTrackerActivity extends YouTubeBaseActivity implements YouTubePlayer.OnInitializedListener, View.OnClickListener {
    private static final String TAG = "FaceTracker";

    private CameraSource mCameraSource = null;

    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private WebView mHomeView;
    private WebView mMapView;
    private YouTubePlayerView mInstructionsView;
    private WebView mReviewsView;
    private RelativeLayout mScanView;
    private WebView mEndView;
    private WebView mWaitingView;

    private TextView tvScanFormat;
    private TextView tvScanContent;
    private LinearLayout llSearch;

    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    private static YouTubePlayer mPlayer;

    private boolean scanned = false;
    private boolean finished = false;

    //==============================================================================================
    // Activity Methods
    //==============================================================================================

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.main);

        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);
        mHomeView = (WebView) findViewById(R.id.homeView);
        mMapView = (WebView) findViewById(R.id.mapView);
        mReviewsView = (WebView) findViewById(R.id.reviewsView);
        mScanView = (RelativeLayout) findViewById(R.id.activity_main);
        mEndView = (WebView) findViewById(R.id.endView);
        mWaitingView = (WebView) findViewById(R.id.waitingView);

        mInstructionsView = (YouTubePlayerView) findViewById(R.id.videoView1);

        View decorView = getWindow().getDecorView();
        // Hide both the navigation bar and the status bar.
        // SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
        // a general rule, you should design your app to hide the status bar whenever you
        // hide the navigation bar.
        int uiOptions = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            decorView.setSystemUiVisibility(uiOptions);
        }

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }

        final Button camera = (Button) findViewById(R.id.show_camera);
        camera.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showCamera();
            }
        });

        final Button home = (Button) findViewById(R.id.home);
        home.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showHome();
            }
        });

        final Button mapButton = (Button) findViewById(R.id.map);
        mapButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showMap();
            }
        });

        final Button instructions = (Button) findViewById(R.id.instructions);
        instructions.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showInstructions();
            }
        });

        final Button reviews = (Button) findViewById(R.id.reviews);
        reviews.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showReviews();
            }
        });

        final Button scanButton = (Button) findViewById(R.id.show_scanner);
        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showScanner();
            }
        });

        mHomeView.setWebViewClient(new WebViewClient());
        mHomeView.getSettings().setJavaScriptEnabled(true);
        mHomeView.loadUrl("http://emilytlam.com/ShopGenie/home.html");

        mMapView.setWebViewClient(new WebViewClient());
        mMapView.getSettings().setJavaScriptEnabled(true);
        mMapView.loadUrl("http://emilytlam.com/ShopGenie/index.html");

        mReviewsView.setWebViewClient(new WebViewClient());
        mReviewsView.getSettings().setJavaScriptEnabled(true);
        mReviewsView.loadUrl("http://emilytlam.com/ShopGenie/reviews.html");

        mEndView.setWebViewClient(new WebViewClient());
        mEndView.getSettings().setJavaScriptEnabled(true);
        mEndView.loadUrl("http://emilytlam.com/ShopGenie/confirmation.html");

        mWaitingView.setWebViewClient(new WebViewClient());
        mWaitingView.getSettings().setJavaScriptEnabled(true);
        mWaitingView.loadUrl("http://emilytlam.com/ShopGenie/waiting.html");

        Button scanBtn = (Button) findViewById(R.id.scan_button);
        tvScanFormat = (TextView) findViewById(R.id.tvScanFormat);
        tvScanContent = (TextView) findViewById(R.id.tvScanContent);
        llSearch = (LinearLayout) findViewById(R.id.llSearch);

        scanBtn.setOnClickListener(this);
    }

    public void onClick(View v) {

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() == null) {
                llSearch.setVisibility(View.GONE);
                Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show();
            } else {
                llSearch.setVisibility(View.VISIBLE);
                tvScanContent.setText(result.getContents());
                tvScanFormat.setText(result.getFormatName());
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
        mScanView.setVisibility(View.GONE);
    }

    public void showCamera() {
        mPreview.setVisibility(View.VISIBLE);
        mHomeView.setVisibility(View.GONE);
        mMapView.setVisibility(View.GONE);
        mInstructionsView.setVisibility(View.GONE);
        mReviewsView.setVisibility(View.GONE);
        mScanView.setVisibility(View.GONE);
        if(mPlayer != null && mPlayer.isPlaying()) {
            mPlayer.pause();
            mPlayer.release();
        }
        startCameraSource();
        Handler handler = new Handler();
        handler.postDelayed(new Runnable(){
            @Override
            public void run(){
                finished = true;
                mPreview.setVisibility(View.GONE);
                mHomeView.setVisibility(View.GONE);
                mMapView.setVisibility(View.GONE);
                mInstructionsView.setVisibility(View.GONE);
                mReviewsView.setVisibility(View.GONE);
                mScanView.setVisibility(View.GONE);
                mEndView.setVisibility(View.VISIBLE);
            }
        }, 7000);
    }

    public void showHome() {
        mPreview.setVisibility(View.GONE);
        mHomeView.setVisibility(View.VISIBLE);
        mMapView.setVisibility(View.GONE);
        mInstructionsView.setVisibility(View.GONE);
        mReviewsView.setVisibility(View.GONE);
        mScanView.setVisibility(View.GONE);
        if(mPlayer != null && mPlayer.isPlaying()) {
            mPlayer.pause();
        }
    }

    public void showMap() {
        mPreview.setVisibility(View.GONE);
        mHomeView.setVisibility(View.GONE);
        mMapView.setVisibility(View.VISIBLE);
        mInstructionsView.setVisibility(View.GONE);
        mReviewsView.setVisibility(View.GONE);
        mScanView.setVisibility(View.GONE);
        if(mPlayer != null && mPlayer.isPlaying()) {
            mPlayer.pause();
        }
    }

    public void showInstructions() {
        mPreview.setVisibility(View.GONE);
        mHomeView.setVisibility(View.GONE);
        mMapView.setVisibility(View.GONE);
        mInstructionsView.setVisibility(View.VISIBLE);
        mReviewsView.setVisibility(View.GONE);
        mScanView.setVisibility(View.GONE);
        mInstructionsView.initialize("AIzaSyCaEVGG2aR575Qxtg2UGZ6RUZWy7YsfoVY", this);
    }

    public void showReviews() {
        mPreview.setVisibility(View.GONE);
        mHomeView.setVisibility(View.GONE);
        mMapView.setVisibility(View.GONE);
        mInstructionsView.setVisibility(View.GONE);
        mReviewsView.setVisibility(View.VISIBLE);
        mScanView.setVisibility(View.GONE);
        if(mPlayer != null && mPlayer.isPlaying()) {
            mPlayer.pause();
        }
    }

    public void showScanner() {
        mPreview.setVisibility(View.GONE);
        mHomeView.setVisibility(View.GONE);
        mMapView.setVisibility(View.GONE);
        mInstructionsView.setVisibility(View.GONE);
        mReviewsView.setVisibility(View.GONE);
        mScanView.setVisibility(View.VISIBLE);
        mWaitingView.setVisibility(View.VISIBLE);
        if (mPlayer != null && mPlayer.isPlaying()) {
            mPlayer.pause();
        }
        llSearch.setVisibility(View.GONE);
        if (!scanned) {
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.setPrompt("Scan a barcode or QRcode");
            integrator.setOrientationLocked(false);
            integrator.initiateScan();

            integrator.setCameraId(CameraSource.CAMERA_FACING_FRONT);  // Use a specific camera of the device
            integrator.setBeepEnabled(true);
            scanned = true;
        } else {
            mScanView.setVisibility(View.GONE);
        }
    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        detector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                        .build());

        if (!detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        mCameraSource = new CameraSource.Builder(context, detector)
                .setRequestedPreviewSize(640, 480)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    // Create the Handler object (on the main thread by default)
    Handler handler = new Handler();
    // Define the code block to be executed
    private Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            // Instantiate the RequestQueue.
            RequestQueue queue = Volley.newRequestQueue(getApplication().getBaseContext());
            String url ="https://www.myroom360.com/api/v1/money.php";
            // Request a string response from the provided URL.
            StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            String page = response.substring(response.indexOf("cat") + 6, response.indexOf("num")-3);
                            Log.d(TAG, "Response is: "+ page);
                            switch (page) {
                                case "location":
                                    showMap();
                                    break;
                                case "instruction":
                                    showInstructions();
                                    break;
                                case "reviews":
                                    showReviews();
                                    break;
                                case "checkout":
                                    showScanner();
                                    break;
                                case "payment":
                                    showCamera();
                                    break;
                                default:
                                    showHome();
                            }
                            // Repeat this the same runnable code block again in another 2 seconds
                            if (!finished) {
                                handler.postDelayed(runnableCode, 2000);
                            }
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.d(TAG, "That didn't work!");

                    // Repeat this the same runnable code block again in another 2 seconds
                    if (!finished) {
                        handler.postDelayed(runnableCode, 2000);
                    }
                }
            });
            // Add the request to the RequestQueue.
            queue.add(stringRequest);
        }
    };

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();
        handler.post(runnableCode);
        mScanView.setVisibility(View.GONE);
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Face Tracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }

    @Override
    public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer player, boolean wasRestored) {
        mPlayer = player;
        if (!wasRestored) mPlayer.loadVideo("WYnOjQWX72Q"); // your video to play
    }
    @Override
    public void onInitializationFailure(YouTubePlayer.Provider arg0,
                                        YouTubeInitializationResult arg1){
    }
}
