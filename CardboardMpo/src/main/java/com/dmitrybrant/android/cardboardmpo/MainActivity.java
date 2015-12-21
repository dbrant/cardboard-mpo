/*
 * Copyright 2015 Dmitry Brant.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dmitrybrant.android.cardboardmpo;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.os.storage.StorageManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;

public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer {
    private static final String TAG = "MainActivity";
    private static final int READ_PERMISSION_REQUEST = 50;

    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 5.0f;
    private static final float CAMERA_Z = 0.01f;

    private List<File> mpoFileList = new ArrayList<>();
    private int currentFileIndex = 0;

    private CardboardView cardboardView;
    private Vibrator vibrator;

    private ProgressBar progressLeft;
    private ProgressBar progressRight;
    private TextView statusLeft;
    private TextView statusRight;

    private TexturedRect rectLeftEye;
    private TexturedRect rectRightEye;

    private float[] camera;
    private float[] view;
    private float[] headView;
    private float[] modelView;
    private float[] modelViewProjection;

    // final field for synchronizing access to the bitmaps above
    private final Boolean bmpLock = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_ui);

        cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setRestoreGLStateEnabled(false);
        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        progressLeft = (ProgressBar) findViewById(R.id.progress_left);
        progressRight = (ProgressBar) findViewById(R.id.progress_right);
        statusLeft = (TextView) findViewById(R.id.status_text_left);
        statusRight = (TextView) findViewById(R.id.status_text_right);

        setProgress(true);
        setStatus(true, getString(R.string.status_finding_files));

        camera = new float[16];
        view = new float[16];
        modelViewProjection = new float[16];
        modelView = new float[16];
        headView = new float[16];

        checkReadPermissionThenScanImages();
    }

    @Override
    public void onRendererShutdown() {
        Log.d(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.d(TAG, "onSurfaceChanged");
    }

    @Override
    public void onSurfaceCreated(EGLConfig config) {
        Log.i(TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        rectLeftEye = new TexturedRect();
        rectRightEye = new TexturedRect();
        checkGLError("Error after creating textures");
    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {
        Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        headTransform.getHeadView(headView, 0);
    }

    @Override
    public void onDrawEye(Eye eye) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Apply the eye transformation to the camera.
        Matrix.multiplyMM(view, 0, eye.getEyeView(), 0, camera, 0);

        // TODO: Do something with the head transform (e.g. pan the photo around)
        // For now, just reset the view matrix, so that the photo is in the center at all times.
        Matrix.setIdentityM(view, 0);

        float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);
        if (eye.getType() == 1) {
            Matrix.multiplyMM(modelView, 0, view, 0, rectLeftEye.getModelMatrix(), 0);
            Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
            rectLeftEye.draw(modelViewProjection);
        } else {
            Matrix.multiplyMM(modelView, 0, view, 0, rectRightEye.getModelMatrix(), 0);
            Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
            rectRightEye.draw(modelViewProjection);
        }
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
    }

    /**
     * Called when the Cardboard trigger is pulled.
     * This is the only thing we'll really "use" from the CardboardView...
     */
    @Override
    public void onCardboardTrigger() {
        Log.d(TAG, "onCardboardTrigger");

        currentFileIndex++;
        loadNextMpo();

        // Always give user feedback.
        vibrator.vibrate(50);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case READ_PERMISSION_REQUEST:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    findImages();
                } else {
                    Toast.makeText(this, R.string.error_grant_permission, Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                break;
        }
    }

    private static void checkGLError(String label) {
        int error;
        if ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, label + ": glError " + error);
            throw new RuntimeException(label + ": glError " + error);
        }
    }

    private void setProgress(boolean enabled) {
        progressLeft.setVisibility(enabled ? View.VISIBLE : View.GONE);
        progressRight.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    private void setStatus(boolean visible, String status) {
        statusLeft.setVisibility(visible ? View.VISIBLE : View.GONE);
        statusRight.setVisibility(visible ? View.VISIBLE : View.GONE);
        statusLeft.setText(status);
        statusRight.setText(status);
    }

    private void checkReadPermissionThenScanImages() {
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE }, READ_PERMISSION_REQUEST);
        } else {
            findImages();
        }
    }

    private void findImages() {
        // kick off our task to find all MPOs, which will in turn kick off showing the first one.
        new MpoFindTask().execute((Void) null);
    }

    /**
     * Task that finds all MPO files in external storage.
     * Returns a (flat) list of File objects.
     */
    private class MpoFindTask extends AsyncTask<Void, Integer, List<File>> {

        private List<File> getMpoFiles(File parentDir, int level) {
            ArrayList<File> inFiles = new ArrayList<>();
            if (parentDir == null || level > 2) {
                return inFiles;
            }
            File[] files = parentDir.listFiles();
            if (files == null) {
                return inFiles;
            }
            for (File file : files) {
                if (file.isDirectory()) {
                    inFiles.addAll(getMpoFiles(file, level + 1));
                } else {
                    if (file.getName().toLowerCase(Locale.ENGLISH).endsWith(".mpo")) {
                        inFiles.add(file);
                        Log.d(TAG, "Found MPO: " + file.getAbsolutePath());
                    }
                }
            }
            return inFiles;
        }

        protected List<File> doInBackground(Void... dummy) {
            List<File> mpoFiles = new ArrayList<>();
            List<String> pathList = new ArrayList<>();
            StorageManager sm = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
            try {
                String[] volumes = (String[]) sm.getClass().getMethod("getVolumePaths").invoke(sm);
                if (volumes != null && volumes.length > 0) {
                    pathList.addAll(Arrays.asList(volumes));
                }
            }catch(Exception e) {
                e.printStackTrace();
            }
            if (pathList.size() == 0 && Environment.getExternalStorageDirectory() != null) {
                pathList.add(Environment.getExternalStorageDirectory().getAbsolutePath());
            }
            for (String path : pathList) {
                mpoFiles.addAll(getMpoFiles(new File(path), 0));
            }
            return mpoFiles;
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(List<File> results) {
            mpoFileList.clear();
            setProgress(false);
            if (results.size() == 0) {
                setStatus(true, getString(R.string.status_error_not_found));
                return;
            }
            mpoFileList.addAll(results);
            currentFileIndex = 0;
            setStatus(false, "");
            loadNextMpo();
        }
    }

    private class MainLoadTask extends MpoUtils.MpoLoadTask {
        private final int MAX_BMP_SIZE = 1024;
        private Bitmap bmpLeft;
        private Bitmap bmpRight;

        @Override
        protected List<Long> doInBackground(File... file) {
            List<Long> results = super.doInBackground(file);
            try {
                synchronized (bmpLock) {
                    if (results.size() == 2) {
                        // this is the most common type of MPO, which is left-eye / right-eye
                        Log.d(TAG, "Found 2 JPGs, so loading 0/1...");
                        bmpLeft = MpoUtils.loadMpoBitmapFromFile(mpoFile, results.get(0), MAX_BMP_SIZE, MAX_BMP_SIZE);
                        bmpRight = MpoUtils.loadMpoBitmapFromFile(mpoFile, results.get(1), MAX_BMP_SIZE, MAX_BMP_SIZE);
                    } else if (results.size() == 4) {
                        // I've seen this type in the wild, as well, which seems to be
                        // left-eye-hi-res / left-eye-lo-res / right-eye-hi-res / right-eye-lo-res
                        Log.d(TAG, "Found 4 JPGs, so loading 0/2...");
                        bmpLeft = MpoUtils.loadMpoBitmapFromFile(mpoFile, results.get(0), MAX_BMP_SIZE, MAX_BMP_SIZE);
                        bmpRight = MpoUtils.loadMpoBitmapFromFile(mpoFile, results.get(2), MAX_BMP_SIZE, MAX_BMP_SIZE);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error while reading file.", e);
            }
            return results;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
        }

        @Override
        protected void onPostExecute(List<Long> results) {
            synchronized (bmpLock) {
                if (bmpLeft == null || bmpRight == null) {
                    setStatus(true, getString(R.string.status_error_load));
                } else {

                    cardboardView.queueEvent(new Runnable() {
                        @Override
                        public void run() {

                            rectLeftEye.loadTexture(bmpLeft);
                            cleanupBitmap(bmpLeft);

                            rectRightEye.loadTexture(bmpRight);
                            cleanupBitmap(bmpRight);
                        }
                    });
                    setStatus(false, "");
                }
            }
            setProgress(false);
        }
    }

    /**
     * Load the next MPO file in our sequence. Wrap to the beginning if we're at the end.
     */
    private void loadNextMpo() {
        if (mpoFileList.size() == 0) {
            return;
        }
        if (currentFileIndex >= mpoFileList.size()) {
            currentFileIndex = 0;
        }
        setProgress(true);
        setStatus(true, String.format(getString(R.string.status_loading_file), mpoFileList.get(currentFileIndex).getName()));
        new MainLoadTask().execute(mpoFileList.get(currentFileIndex));
    }

    private void cleanupBitmap(Bitmap bmp) {
        if (bmp != null) {
            try {
                bmp.recycle();
            } catch (Exception e) {
                Log.e(TAG, "Error while cleaning up bitmap.", e);
            }
        }
    }

}
