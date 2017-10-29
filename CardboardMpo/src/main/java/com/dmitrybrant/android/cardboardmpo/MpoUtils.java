/*
 * Copyright 2017 Dmitry Brant.

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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
            /*
             * An MPO file is simply multiple JPG files concatenated together.
             * We can detect their locations by simply looking for the beginning markers of a
             * JPG image. So that's what we'll do.
             */
            final int chunkLength = 4096;
            final byte[] sig1 = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0};
            final byte[] sig2 = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe1};
            mpoFile = file[0];
            List<Long> mpoOffsets = new ArrayList<>();

            InputStream fs = null;
            try {
                fs = new BufferedInputStream(new FileInputStream(mpoFile));
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
            } finally {
                if (fs != null) {
                    try {
                        fs.close();
                    } catch (IOException e) {
                        // don't worry
                    }
                }
            }
            return mpoOffsets;
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(List<Long> results) {
        }
    }

    @Nullable
    public static Bitmap loadMpoBitmapFromFile(@NonNull File file, long offset, int maxWidth,
                                               int maxHeight) throws IOException {
        // First, decode the width and height of the image, so that we know how much to scale
        // it down when loading it into our ImageView (so we don't need to do a huge allocation).
        BitmapFactory.Options opts = new BitmapFactory.Options();
        InputStream fs = null;
        try {
            fs = new BufferedInputStream(new FileInputStream(file));
            fs.skip(offset);
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(fs, null, opts);
        } finally {
            if (fs != null) {
                try {
                    fs.close();
                } catch (IOException e) {
                    // don't worry
                }
            }
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
        Bitmap bmp = null;
        fs = null;
        try {
            fs = new BufferedInputStream(new FileInputStream(file));
            fs.skip(offset);
            BitmapFactory.Options opts2 = new BitmapFactory.Options();
            opts2.inSampleSize = scale;
            bmp = BitmapFactory.decodeStream(fs, null, opts2);
        } finally {
            if (fs != null) {
                try {
                    fs.close();
                } catch (IOException e) {
                    // don't worry
                }
            }
        }
        return bmp;
    }

    /**
     * Task that finds all MPO files in external storage.
     * Returns a (flat) list of File objects.
     */
    public static class MpoFindTask extends AsyncTask<Void, Integer, List<File>> {
        private List<File> mpoFiles = new ArrayList<>();
        private StorageManager sm;

        MpoFindTask(@NonNull Context context) {
            sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        }

        private void getMpoFiles(File parentDir, int level) {
            if (parentDir == null || level > 10) {
                return;
            }
            File[] files = parentDir.listFiles();
            if (files == null) {
                return;
            }
            for (File file : files) {
                if (file.isDirectory()) {
                    getMpoFiles(file, level + 1);
                } else {
                    if (file.getName().toLowerCase(Locale.ENGLISH).endsWith(".mpo")) {
                        mpoFiles.add(file);
                        Log.d(TAG, "Found MPO: " + file.getAbsolutePath());
                    }
                }
            }
        }

        protected List<File> doInBackground(Void... dummy) {
            List<String> pathList = new ArrayList<>();
            try {
                String[] volumes = (String[]) sm.getClass().getMethod("getVolumePaths").invoke(sm);
                if (volumes != null && volumes.length > 0) {
                    pathList.addAll(Arrays.asList(volumes));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (pathList.size() == 0 && Environment.getExternalStorageDirectory() != null) {
                pathList.add(Environment.getExternalStorageDirectory().getAbsolutePath());
            }
            for (String path : pathList) {
                getMpoFiles(new File(path), 0);
            }
            return mpoFiles;
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(List<File> results) {
        }
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
