/*
 * Copyright 2016 Dmitry Brant.

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

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class TexturedRect {
    private final FloatBuffer mCubeTextureCoordinates;
    private int mTextureDataHandle = -1;

    private final String vertexShaderCode =
            "attribute vec2 a_TexCoordinate;" +
                    "varying vec2 v_TexCoordinate;" +
                    "uniform mat4 uMVPMatrix;" +
                    "attribute vec4 vPosition;" +
                    "void main() {" +
                    "  gl_Position = vPosition * uMVPMatrix;" +
                    "  v_TexCoordinate = a_TexCoordinate;" +
                    "}";

    private final String fragmentShaderCode =
            "precision mediump float;" +
                    "uniform sampler2D u_Texture;" +
                    "varying vec2 v_TexCoordinate;" +
                    "void main() {" +
                    "  gl_FragColor = texture2D(u_Texture, v_TexCoordinate);" +
                    "}";

    private float[] modelMatrix;
    public float[] getModelMatrix() {
        return modelMatrix;
    }

    private final int shaderProgram;
    private final FloatBuffer vertexBuffer;
    private final ShortBuffer drawListBuffer;

    static final int COORDS_PER_VERTEX = 2;
    static float rectCoords[] = { -1f, 1f, -1f, -1f, 1f, -1f, 1f, 1f };

    private short drawOrder[] = { 0, 1, 2, 0, 2, 3 }; //Order to draw vertices

    public TexturedRect()
    {
        ByteBuffer bb = ByteBuffer.allocateDirect(rectCoords.length * 4); // 4 bytes per float
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(rectCoords);
        vertexBuffer.position(0);

        final float[] cubeTextureCoordinateData = { 0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f };

        mCubeTextureCoordinates = ByteBuffer.allocateDirect(cubeTextureCoordinateData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mCubeTextureCoordinates.put(cubeTextureCoordinateData).position(0);

        ByteBuffer dlb = ByteBuffer.allocateDirect(rectCoords.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(drawOrder);
        drawListBuffer.position(0);

        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        shaderProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(shaderProgram, vertexShader);
        GLES20.glAttachShader(shaderProgram, fragmentShader);

        //Texture Code
        GLES20.glBindAttribLocation(shaderProgram, 0, "a_TexCoordinate");
        GLES20.glLinkProgram(shaderProgram);

        modelMatrix = new float[16];
        resetMatrix();
    }

    public void draw(float[] mvpMatrix)
    {
        if (mTextureDataHandle == -1) {
            return;
        }
        GLES20.glUseProgram(shaderProgram);

        int mPositionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition");
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        final int vertexStride = COORDS_PER_VERTEX * 4; //Bytes per vertex
        GLES20.glVertexAttribPointer(mPositionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);

        int mTextureUniformHandle = GLES20.glGetAttribLocation(shaderProgram, "u_Texture");
        int mTextureCoordinateHandle = GLES20.glGetAttribLocation(shaderProgram, "a_TexCoordinate");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureDataHandle);
        GLES20.glUniform1i(mTextureUniformHandle, 0);

        mCubeTextureCoordinates.position(0);
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, 2, GLES20.GL_FLOAT, false, 0, mCubeTextureCoordinates);
        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);

        int mMVPMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

        GLES20.glDisableVertexAttribArray(mPositionHandle);
    }

    private static int loadShader(int type, String shaderCode)
    {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    private void resetMatrix() {
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, 0, 0, -2f);
    }

    public void loadTexture(Bitmap bitmap) {
        final int[] textureHandle = new int[1];
        if (mTextureDataHandle != -1) {
            textureHandle[0] = mTextureDataHandle;
            GLES20.glDeleteTextures(1, textureHandle, 0);
            textureHandle[0] = 0;
        }

        GLES20.glGenTextures(1, textureHandle, 0);

        if (textureHandle[0] != 0)
        {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        } else {
            throw new RuntimeException("Error loading texture.");
        }
        mTextureDataHandle = textureHandle[0];

        // scale our matrix based on the aspect ratio of the bitmap
        float aspect = (float) bitmap.getWidth() / (float) bitmap.getHeight();
        resetMatrix();
        if (aspect > 1f) {
            Matrix.scaleM(modelMatrix, 0, aspect, 1f, 1f);
        } else {
            Matrix.scaleM(modelMatrix, 0, 1f, 1f / aspect, 1f);
        }
    }
}
