
package com.dmitrybrant.android.cardboardmpo;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.widget.ImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;

public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer {
  private static final String TAG = "MainActivity";

  private Vibrator vibrator;

  private ImageView imageLeft;
  private ImageView imageRight;

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

    loadNextMpo();
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

    // Always give user feedback.
    vibrator.vibrate(50);
  }



  private class MpoLoadTask extends AsyncTask<String, Integer, List<String>> {
    protected List<String> doInBackground(String... path) {
      final String mpoFileName = "mpofile";
      final int chunkLength = 256;
      List<String> fileNameList = new ArrayList<>();

      try {
        File f = new File(path[0]);
        FileInputStream fs = new FileInputStream(f);
        byte[] tempBytes = new byte[chunkLength];
        FileOutputStream currentOutStream = null;
        String currentFileName;
        int mpoIndex = 0;

        Log.d(TAG, "Processing file: " + path[0]);
        while (true) {
          if (fs.read(tempBytes, 0, chunkLength) <= 0) {
            break;
          }
          if ((tempBytes[0] == (byte)0xFF) && (tempBytes[1] == (byte)0xD8) && (tempBytes[2] == (byte)0xFF) && ((tempBytes[3] == (byte)0xE0) || (tempBytes[3] == (byte)0xE1))) {
            // it's a new image
            if (currentOutStream != null) {
              currentOutStream.flush();
              currentOutStream.close();
            }
            currentFileName = getCacheDir().getCanonicalPath() + "/" + mpoFileName + (mpoIndex++) + ".jpg";
            Log.d(TAG, "Found new JPG, and calling it " + currentFileName);
            fileNameList.add(currentFileName);
            File fout = new File(currentFileName);
            currentOutStream = new FileOutputStream(fout);
          }
          if (currentOutStream != null) {
            currentOutStream.write(tempBytes, 0, chunkLength);
          }
        }
        if (currentOutStream != null) {
          currentOutStream.flush();
          currentOutStream.close();
        }

      } catch (IOException e) {
        Log.e(TAG, "Error while reading file.", e);
      }
      return fileNameList;
    }

    protected void onProgressUpdate(Integer... progress) {
    }

    protected void onPostExecute(List<String> result) {
      if (result.size() == 2) {
        Log.d(TAG, "Found 2 JPGs, so loading them...");
        loadBitmapFromFileIntoView(result.get(0), imageLeft);
        loadBitmapFromFileIntoView(result.get(1), imageRight);
      }
    }
  }

  private void loadBitmapFromFileIntoView(String path, ImageView view) {
    BitmapFactory.Options opts = new BitmapFactory.Options();
    //Decode image size
    opts.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(path, opts);
    int scale = 1;
    if (opts.outHeight > view.getHeight() || opts.outWidth > view.getWidth()) {
      scale = (int) Math.pow(2, (int) Math.round(Math.log(view.getWidth() / (double) Math.max(opts.outHeight, opts.outWidth)) / Math.log(0.5)));
    }
    if ((opts.outHeight <= 0) || (opts.outWidth <= 0)) {
      return;
    }
    //Decode with inSampleSize
    BitmapFactory.Options opts2 = new BitmapFactory.Options();
    opts2.inSampleSize = scale;
    view.setImageBitmap(BitmapFactory.decodeFile(path, opts2));
  }

  private void loadNextMpo() {
    final String path = "/storage/emulated/0/Download/DSCF0005.MPO";

    new MpoLoadTask().execute(path);
  }



}
