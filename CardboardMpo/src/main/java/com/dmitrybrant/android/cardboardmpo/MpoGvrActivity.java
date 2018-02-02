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
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;

import com.google.vr.sdk.base.AndroidCompat;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import javax.microedition.khronos.egl.EGLConfig;

public class MpoGvrActivity extends GvrActivity implements GvrView.StereoRenderer {
    private static final String TAG = "MpoGvrActivity";

    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 5.0f;
    private static final float CAMERA_Z = 1f;

    private GvrView gvrView;
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
        setContentView(R.layout.activity_gvr);

        gvrView = findViewById(R.id.gvr_view);
        gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8);
        gvrView.setRenderer(this);
        gvrView.setTransitionViewEnabled(true);
        if (gvrView.setAsyncReprojectionEnabled(true)) {
            // Async reprojection decouples the app framerate from the display framerate,
            // allowing immersive interaction even at the throttled clockrates set by
            // sustained performance mode.
            AndroidCompat.setSustainedPerformanceMode(this, true);
        }
        setGvrView(gvrView);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        camera = new float[16];
        view = new float[16];
        modelViewProjection = new float[16];
        modelView = new float[16];
        headView = new float[16];
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

        rectLeftEye = new TexturedRect(this, 0);
        rectRightEye = new TexturedRect(this, 0);
        checkGLError("Error after creating textures");

        updateBitmaps();
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

        // TODO: Do something with the head and/or eye transform (e.g. pan the photo around)
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

    @Override
    public void onCardboardTrigger() {
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
        gvrView.queueEvent(() -> {
            rectLeftEye.loadTexture(MpoApplication.getInstance().getBmpLeft());
            rectRightEye.loadTexture(MpoApplication.getInstance().getBmpRight());
        });
    }

    private boolean isActivityGone() {
        return isDestroyed() || isFinishing();
    }
}
