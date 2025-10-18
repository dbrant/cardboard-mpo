/*
 * Copyright 2017+ Dmitry Brant.

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
package com.dmitrybrant.android.cardboardmpo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.core.content.ContentResolverCompat
import java.io.BufferedInputStream
import java.io.InputStream
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

object MpoUtils {
    private const val TAG = "MpoUtils"

    fun loadMpoBitmapFromFile(uri: Uri, offset: Long, maxWidth: Int, maxHeight: Int): Bitmap? {
        // First, decode the width and height of the image, so that we know how much to scale
        // it down when loading it into our ImageView (so we don't need to do a huge allocation).
        val opts = BitmapFactory.Options()
        var fs: InputStream? = null
        try {
            val stream = MpoApplication.getInstance().contentResolver.openInputStream(uri)
            fs = BufferedInputStream(stream)
            fs.skip(offset)
            opts.inJustDecodeBounds = true
            BitmapFactory.decodeStream(fs, null, opts)
        } finally {
            Util.closeSilently(fs)
        }
        var scale = 1
        if (opts.outHeight > maxHeight || opts.outWidth > maxWidth) {
            scale = 2.0.pow((ln(maxWidth / max(opts.outHeight, opts.outWidth).toDouble()) / ln(0.5)).roundToInt().toDouble()).toInt()
        }
        if ((opts.outHeight <= 0) || (opts.outWidth <= 0)) {
            return null
        }
        // Decode the image for real, but now with a sampleSize.
        // We have to reopen the file stream, and re-skip to the correct offset, since
        // FileInputStream doesn't support reset().
        var bmp: Bitmap?
        fs = null
        try {
            val stream = MpoApplication.getInstance().contentResolver.openInputStream(uri)
            fs = BufferedInputStream(stream)
            fs.skip(offset)
            val opts2 = BitmapFactory.Options()
            opts2.inSampleSize = scale
            bmp = BitmapFactory.decodeStream(fs, null, opts2)
        } finally {
            Util.closeSilently(fs)
        }
        return bmp
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
    fun searchBytes(bytesToSearch: ByteArray, matchBytes: ByteArray, startIndex: Int, count: Int): Int {
        var ret = -1
        val max = count - matchBytes.size + 1
        var found: Boolean
        for (i in startIndex..<max) {
            found = true
            for (j in matchBytes.indices) {
                if (bytesToSearch[i + j] != matchBytes[j]) {
                    found = false
                    break
                }
            }
            if (found) {
                ret = i
                break
            }
        }
        return ret
    }

    fun getFileName(cr: ContentResolver, uri: Uri): String? {
        if ("content" == uri.scheme) {
            val projection = arrayOf<String?>(MediaStore.MediaColumns.DISPLAY_NAME)
            val metaCursor = ContentResolverCompat.query(cr, uri, projection, null, null, null, null as CancellationSignal?)
            if (metaCursor != null) {
                try {
                    if (metaCursor.moveToFirst()) {
                        return metaCursor.getString(0)
                    }
                } finally {
                    metaCursor.close()
                }
            }
        }
        return uri.lastPathSegment
    }
}
