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

import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MpoUtils {
  private static final String TAG = "MpoUtils";

  /**
   * Task that facilitates loading of MPO files.
   * For a given MPO file, it returns a list of offsets that point to each individual
   * JPG chunk that the MPO contains.
   */
  public static class MpoLoadTask extends AsyncTask<File, Integer, List<Long>> {
    protected File mpoFile;

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
          int sigOffset = searchBytes(tempBytes, sig1, 0, chunkLength);
          if (sigOffset == -1) {
            sigOffset = searchBytes(tempBytes, sig2, 0, chunkLength);
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
    }
  }

  public static void loadMpoBitmapFromFileIntoView(File file, long offset, ImageView view) throws IOException {
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

  public static int searchBytes(byte[] bytesToSearch, byte[] matchBytes, int startIndex, int count) {
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
