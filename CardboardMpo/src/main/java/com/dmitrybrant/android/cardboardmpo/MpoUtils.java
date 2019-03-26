/*
 * Copyright 2017-2018 Dmitry Brant.

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

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContentResolverCompat;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MpoUtils {
    private static final String TAG = "MpoUtils";

    /**
     * Task that facilitates loading of MPO files.
     * For a given MPO file, it returns a list of offsets that point to each individual
     * JPG chunk that the MPO contains.
     */
    public static class MpoLoadTask extends AsyncTask<Uri, Integer, String> {
        private final int MAX_BMP_SIZE = 1024;

        protected String doInBackground(Uri... file) {
            List<Long> mpoOffsets = new ArrayList<>();
            InputStream stream = null;
            String fileName;
            try {
                Uri uri = file[0];
                ContentResolver cr = MpoApplication.getInstance().getContentResolver();
                fileName = getFileName(cr, uri);
                stream = cr.openInputStream(uri);
                if (stream != null) {
                    Log.d(TAG, "Processing file: " + fileName);
                    /*
                     * An MPO file is simply multiple JPG files concatenated together.
                     * We can detect their locations by simply looking for the beginning markers of a
                     * JPG image. So that's what we'll do.
                     */
                    final int chunkLength = 4096;
                    final byte[] sig1 = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0};
                    final byte[] sig2 = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe1};

                    InputStream fs = new BufferedInputStream(stream);
                    byte[] tempBytes = new byte[chunkLength];
                    long currentOffset = 0;


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

                    if (mpoOffsets.size() == 2) {
                        // this is the most common type of MPO, which is left-eye / right-eye
                        Log.d(TAG, "Found 2 JPGs, so loading 0/1...");
                        MpoApplication.getInstance().setBmpLeft(MpoUtils.loadMpoBitmapFromFile(uri, mpoOffsets.get(0), MAX_BMP_SIZE, MAX_BMP_SIZE));
                        MpoApplication.getInstance().setBmpRight(MpoUtils.loadMpoBitmapFromFile(uri, mpoOffsets.get(1), MAX_BMP_SIZE, MAX_BMP_SIZE));
                    } else if (mpoOffsets.size() == 4) {
                        // I've seen this type in the wild, as well, which seems to be
                        // left-eye-hi-res / left-eye-lo-res / right-eye-hi-res / right-eye-lo-res
                        Log.d(TAG, "Found 4 JPGs, so loading 0/2...");
                        MpoApplication.getInstance().setBmpLeft(MpoUtils.loadMpoBitmapFromFile(uri, mpoOffsets.get(0), MAX_BMP_SIZE, MAX_BMP_SIZE));
                        MpoApplication.getInstance().setBmpRight(MpoUtils.loadMpoBitmapFromFile(uri, mpoOffsets.get(2), MAX_BMP_SIZE, MAX_BMP_SIZE));
                    } else {
                        throw new RuntimeException("Incorrect number of MPO elements: " + mpoOffsets.size());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                fileName = null;
                MpoApplication.getInstance().cleanupBitmaps();
            } finally {
                Util.closeSilently(stream);
            }
            return fileName;
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(String fileName) {
        }

        @Nullable
        private String getFileName(@NonNull ContentResolver cr, @NonNull Uri uri) {
            if ("content".equals(uri.getScheme())) {
                String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
                Cursor metaCursor = ContentResolverCompat.query(cr, uri, projection, null, null, null, null);
                if (metaCursor != null) {
                    try {
                        if (metaCursor.moveToFirst()) {
                            return metaCursor.getString(0);
                        }
                    } finally {
                        metaCursor.close();
                    }
                }
            }
            return uri.getLastPathSegment();
        }
    }

    @Nullable
    static Bitmap loadMpoBitmapFromFile(@NonNull Uri uri, long offset, int maxWidth, int maxHeight) throws IOException {
        // First, decode the width and height of the image, so that we know how much to scale
        // it down when loading it into our ImageView (so we don't need to do a huge allocation).
        BitmapFactory.Options opts = new BitmapFactory.Options();
        InputStream fs = null;
        try {
            InputStream stream = MpoApplication.getInstance().getContentResolver().openInputStream(uri);
            fs = new BufferedInputStream(stream);
            fs.skip(offset);
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(fs, null, opts);
        } finally {
            Util.closeSilently(fs);
        }
        int scale = 1;
        if (opts.outHeight > maxHeight || opts.outWidth > maxWidth) {
            scale = (int) Math.pow(2, (int) Math.round(Math.log(maxWidth / (double) Math.max(opts.outHeight, opts.outWidth)) / Math.log(0.5)));
        }
        if ((opts.outHeight <= 0) || (opts.outWidth <= 0)) {
            return null;
        }
        // Decode the image for real, but now with a sampleSize.
        // We have to reopen the file stream, and re-skip to the correct offset, since
        // FileInputStream doesn't support reset().
        Bitmap bmp;
        fs = null;
        try {
            InputStream stream = MpoApplication.getInstance().getContentResolver().openInputStream(uri);
            fs = new BufferedInputStream(stream);
            fs.skip(offset);
            BitmapFactory.Options opts2 = new BitmapFactory.Options();
            opts2.inSampleSize = scale;
            bmp = BitmapFactory.decodeStream(fs, null, opts2);
        } finally {
            Util.closeSilently(fs);
        }
        return bmp;
    }

    /**
     * Search a byte array for a specific sequence of bytes.
     *
     * @param bytesToSearch Byte array to search.
     * @param matchBytes    Sequence of bytes to find in the array.
     * @param startIndex    Offset within the array from which to start searching.
     * @param count         Number of bytes within the array to search.
     * @return Byte offset within the array of the specified sequence, or -1 if it was not found.
     */
    private static int searchBytes(byte[] bytesToSearch, byte[] matchBytes, int startIndex, int count) {
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
