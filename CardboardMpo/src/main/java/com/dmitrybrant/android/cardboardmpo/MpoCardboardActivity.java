/*
 * Copyright 2015-2018 Dmitry Brant.

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

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.cardboard.sdk.CardboardView;
import com.google.cardboard.sdk.HeadTransform;
import com.google.cardboard.sdk.Viewport;

import javax.microedition.khronos.egl.EGLConfig;

public class MpoCardboardActivity extends AppCompatActivity implements CardboardView.Renderer {
    private static final String TAG = "MpoCardboardActivity";

    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 5.0f;
    private static final float CAMERA_Z = 1f;

    private CardboardView cardboardView;
    private Vibrator vibrator;

    private TexturedRect rectLeftEye;
    private TexturedRect rectRightEye;

    private float[] camera;
    private float[] view;
    private float[] headView;
    private float[] modelView;
    private float[] modelViewProjection;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cardboard);

        // Use the whole screen, without the system bars, and keep it on.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.hide(WindowInsetsCompat.Type.systemBars());
        insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        cardboardView = findViewById(R.id.cardboard_view);
        cardboardView.setEGLConfigChooser(8, 8, 8, 8, 16, 8);
        cardboardView.setRenderer(this);
        cardboardView.setStereoRenderMode(true);

        cardboardView.setOnBackButtonClick(this::finish);
        cardboardView.setOnSettingsButtonClick(cardboardView::scanViewerQrCode);
        cardboardView.setOnTriggerEvent(this::onCardboardTrigger);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        camera = new float[16];
        view = new float[16];
        modelViewProjection = new float[16];
        modelView = new float[16];
        headView = new float[16];
    }

    @Override
    protected void onResume() {
        super.onResume();
        cardboardView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cardboardView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cardboardView.onDestroy();
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

        rectLeftEye = new TexturedRect(this, 0);
        rectRightEye = new TexturedRect(this, 0);
        checkGLError("Error after creating textures");

        updateBitmaps();
    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {
        Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        headTransform.getHeadView(headView, 0);

        // CardboardView clears the frame (for both eyes) after this, and its distortion pass at the
        // end of the previous frame changes some of the GL state, so the state is set for each frame.
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    }

    @Override
    public void onDrawEye(CardboardView.Eye eye) {
        // TODO: Do something with the head and/or eye transform (e.g. pan the photo around)
        // For now, just reset the view matrix, so that the photo is in the center at all times.
        Matrix.setIdentityM(view, 0);

        float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);
        if (eye.getEyeType() == CardboardView.Eye.LEFT) {
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

    private void onCardboardTrigger() {
        Log.d(TAG, "onCardboardTrigger");
        // Always give user feedback.
        vibrator.vibrate(50);
    }

    private static void checkGLError(String label) {
        int error;
        if ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, label + ": glError " + error);
            throw new RuntimeException(label + ": glError " + error);
        }
    }

    private void updateBitmaps() {
        cardboardView.queueEvent(() -> {
            rectLeftEye.loadTexture(MpoApplication.getInstance().getBmpLeft());
            rectRightEye.loadTexture(MpoApplication.getInstance().getBmpRight());
        });
    }

    private boolean isActivityGone() {
        return isDestroyed() || isFinishing();
    }
}
