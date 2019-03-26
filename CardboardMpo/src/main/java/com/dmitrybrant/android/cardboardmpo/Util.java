package com.dmitrybrant.android.cardboardmpo;

import android.content.Context;
import android.opengl.GLES20;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/*
 * Copyright 2017 Dmitry Brant. All rights reserved.
 *
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
public final class Util {

    public static int compileProgram(@NonNull Context context, @RawRes int vertexShader,
                                     @RawRes int fragmentShader, @NonNull String[] attributes) {
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, loadShader(GLES20.GL_VERTEX_SHADER,
                readTextFileFromRawRes(context, vertexShader)));
        GLES20.glAttachShader(program, loadShader(GLES20.GL_FRAGMENT_SHADER,
                readTextFileFromRawRes(context, fragmentShader)));
        for (int i = 0; i < attributes.length; i++) {
            GLES20.glBindAttribLocation(program, i, attributes[i]);
        }
        GLES20.glLinkProgram(program);
        return program;
    }

    public static void checkGLError(@NonNull String label) {
        int error;
        if ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            throw new RuntimeException(label + ": glError " + error);
        }
    }

    public static void closeSilently(@Nullable Closeable c) {
        try {
            if (c != null) {
                c.close();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static int loadShader(int type, @NonNull String shaderCode){
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        // If the compilation fails, delete the shader.
        if (compileStatus[0] == 0) {
            Log.e("loadShader", "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("Shader compilation failed.");
        }
        return shader;
    }

    @NonNull
    private static String readTextFileFromRawRes(@NonNull Context context, @RawRes int resourceId)
    {
        InputStream inputStream = context.getResources().openRawResource(resourceId);
        try {
            byte[] bytes = new byte[inputStream.available()];
            inputStream.read(bytes);
            return new String(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeSilently(inputStream);
        }
        throw new RuntimeException("Failed to read raw resource id " + resourceId);
    }
}
