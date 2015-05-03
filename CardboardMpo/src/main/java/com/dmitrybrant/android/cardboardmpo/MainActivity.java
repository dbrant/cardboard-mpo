
package com.dmitrybrant.android.cardboardmpo;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;

import android.content.Context;
import android.graphics.BitmapFactory;
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
import java.io.FileInputStream;
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
    Log.i(TAG, "onRendererShutdown");
  }

  @Override
  public void onSurfaceChanged(int width, int height) {
    Log.i(TAG, "onSurfaceChanged");
  }

  /**
   * Creates the buffers we use to store information about the 3D world.
   *
   * <p>OpenGL doesn't use Java arrays, but rather needs data in a format it can understand.
   * Hence we use ByteBuffers.
   *
   * @param config The EGL configuration used when creating the surface.
   */
  @Override
  public void onSurfaceCreated(EGLConfig config) {
  }

  /**
   * Prepares OpenGL ES before we draw a frame.
   *
   * @param headTransform The head transformation in the new frame.
   */
  @Override
  public void onNewFrame(HeadTransform headTransform) {
  }

  /**
   * Draws a frame for an eye.
   *
   * @param eye The eye to render. Includes all required transformations.
   */
  @Override
  public void onDrawEye(Eye eye) {
  }

  @Override
  public void onFinishFrame(Viewport viewport) {
  }

  /**
   * Called when the Cardboard trigger is pulled.
   */
  @Override
  public void onCardboardTrigger() {
    Log.i(TAG, "onCardboardTrigger");

    currentFileIndex++;
    loadNextMpo();

    // Always give user feedback.
    vibrator.vibrate(50);
  }


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
            Log.d(TAG, ">>>>>>>>> found: " + file.getAbsolutePath());
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


  private class MpoLoadTask extends AsyncTask<File, Integer, List<Long>> {
    private File mpoFile;

    protected List<Long> doInBackground(File... file) {
      final int chunkLength = 4096;
      final byte[] sig1 = new byte[] { (byte)0xff, (byte)0xd8, (byte)0xff, (byte)0xe0 };
      final byte[] sig2 = new byte[] { (byte)0xff, (byte)0xd8, (byte)0xff, (byte)0xe1 };
      mpoFile = file[0];
      List<Long> mpoOffsets = new ArrayList<>();

      FileInputStream fs = null;
      try {
        fs = new FileInputStream(mpoFile);
        byte[] tempBytes = new byte[chunkLength];
        long currentOffset = 0;

        Log.d(TAG, "Processing file: " + mpoFile.getName());
        while (true) {
          if (fs.read(tempBytes, 0, chunkLength) <= 0) {
            break;
          }
          int sigOffset = SearchBytes(tempBytes, sig1, 0, chunkLength);
          if (sigOffset == -1) {
            sigOffset = SearchBytes(tempBytes, sig2, 0, chunkLength);
          }
          if (sigOffset >= 0) {
            // it's a new image
            Log.d(TAG, "Found new JPG at offset " + (currentOffset + sigOffset));
            mpoOffsets.add(currentOffset + sigOffset);
          }
          currentOffset += chunkLength;
        }

      } catch (IOException e) {
        Log.e(TAG, "Error while reading file.", e);
      }
      if (fs != null) {
        try {
          fs.close();
        } catch (IOException e) {
          // don't worry
        }
      }
      return mpoOffsets;
    }

    protected void onProgressUpdate(Integer... progress) {
    }

    protected void onPostExecute(List<Long> results) {
      try {
        if (results.size() == 2) {
          Log.d(TAG, "Found 2 JPGs, so loading them...");
          loadMpoBitmapFromFileIntoView(mpoFile, results.get(0), imageLeft);
          loadMpoBitmapFromFileIntoView(mpoFile, results.get(1), imageRight);
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

  private void loadMpoBitmapFromFileIntoView(File file, long offset, ImageView view) throws IOException {
    BitmapFactory.Options opts = new BitmapFactory.Options();
    FileInputStream fs = new FileInputStream(file);
    fs.skip(offset);
    //Decode image size
    opts.inJustDecodeBounds = true;
    BitmapFactory.decodeStream(fs, null, opts);
    fs.close();
    int scale = 1;
    if (opts.outHeight > view.getHeight() || opts.outWidth > view.getWidth()) {
      scale = (int) Math.pow(2, (int) Math.round(Math.log(view.getWidth() / (double) Math.max(opts.outHeight, opts.outWidth)) / Math.log(0.5)));
    }
    if ((opts.outHeight <= 0) || (opts.outWidth <= 0)) {
      return;
    }
    fs = new FileInputStream(file);
    fs.skip(offset);
    //Decode with inSampleSize
    BitmapFactory.Options opts2 = new BitmapFactory.Options();
    opts2.inSampleSize = scale;
    view.setImageBitmap(BitmapFactory.decodeStream(fs, null, opts2));
    fs.close();
  }

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
    new MpoLoadTask().execute(mpoFileList.get(currentFileIndex));
  }


  public static int SearchBytes(byte[] bytesToSearch, byte[] matchBytes, int startIndex, int count) {
    int ret = -1, max = count - matchBytes.length + 1;
    boolean found;
    for (int i = startIndex; i < max; i++) {
      found = true;
      for (int j = 0; j < matchBytes.length; j++) {
        if (bytesToSearch[i + j] != matchBytes[j]) {
          found = false;
          break;
        }
      }
      if (found) {
        ret = i;
        break;
      }
    }
    return ret;
  }

}
