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

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;

public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer {
  private static final String TAG = "MainActivity";

  private List<File> mpoFileList = new ArrayList<>();
  private int currentFileIndex = 0;

  private Vibrator vibrator;
  private AlphaAnimation fadeInAnim;

  private ImageView imageLeft;
  private ImageView imageRight;
  private ProgressBar progressLeft;
  private ProgressBar progressRight;
  private TextView statusLeft;
  private TextView statusRight;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.common_ui);
    CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
    cardboardView.setRenderer(this);
    setCardboardView(cardboardView);

    vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

    imageLeft = (ImageView) findViewById(R.id.image_left);
    imageRight = (ImageView) findViewById(R.id.image_right);
    progressLeft = (ProgressBar) findViewById(R.id.progress_left);
    progressRight = (ProgressBar) findViewById(R.id.progress_right);
    statusLeft = (TextView) findViewById(R.id.status_text_left);
    statusRight = (TextView) findViewById(R.id.status_text_right);

    fadeInAnim = new AlphaAnimation(0.0f, 1.0f);
    fadeInAnim.setDuration(500);

    setProgress(true);
    setStatus(true, getString(R.string.status_finding_files));
    new MpoFindTask().execute((Void)null);
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

  @Override
  public void onRendererShutdown() {
  }

  @Override
  public void onSurfaceChanged(int width, int height) {
  }

  @Override
  public void onSurfaceCreated(EGLConfig config) {
  }

  @Override
  public void onNewFrame(HeadTransform headTransform) {
    // TODO: shift the image based on the head transform.
  }

  @Override
  public void onDrawEye(Eye eye) {
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
      if (Environment.getExternalStorageDirectory() != null) {
        mpoFiles.addAll(getMpoFiles(Environment.getExternalStorageDirectory(), 0));
      }
      // TODO: find better way of getting external SD card directory?
      mpoFiles.addAll(getMpoFiles(new File("/mnt/extSdCard"), 0));
      return mpoFiles;
    }

    protected void onProgressUpdate(Integer... progress) {
    }

    protected void onPostExecute(List<File> results) {
      mpoFileList.clear();
      mpoFileList.addAll(results);
      currentFileIndex = 0;
      setProgress(false);
      setStatus(false, "");
      loadNextMpo();
    }
  }

  private class MainLoadTask extends MpoUtils.MpoLoadTask {
    @Override
    protected void onProgressUpdate(Integer... progress) {
    }

    @Override
    protected void onPostExecute(List<Long> results) {
      try {
        if (results.size() == 2) {
          // this is the most common type of MPO, which is left-eye / right-eye
          Log.d(TAG, "Found 2 JPGs, so loading 0/1...");
          MpoUtils.loadMpoBitmapFromFileIntoView(mpoFile, results.get(0), imageLeft);
          MpoUtils.loadMpoBitmapFromFileIntoView(mpoFile, results.get(1), imageRight);
        } else if (results.size() == 4) {
          // I've seen this type in the wild, as well, which seems to be
          // left-eye-hi-res / left-eye-lo-res / right-eye-hi-res / right-eye-lo-res
          Log.d(TAG, "Found 4 JPGs, so loading 0/2...");
          MpoUtils.loadMpoBitmapFromFileIntoView(mpoFile, results.get(0), imageLeft);
          MpoUtils.loadMpoBitmapFromFileIntoView(mpoFile, results.get(2), imageRight);
        }
      } catch (IOException e) {
        Log.e(TAG, "Error while reading file.", e);
      }
      setProgress(false);
      setStatus(false, "");
      imageLeft.startAnimation(fadeInAnim);
      imageRight.startAnimation(fadeInAnim);
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
    imageLeft.setImageDrawable(null);
    imageRight.setImageDrawable(null);
    new MainLoadTask().execute(mpoFileList.get(currentFileIndex));
  }

}
