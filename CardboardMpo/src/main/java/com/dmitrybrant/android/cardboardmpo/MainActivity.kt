package com.dmitrybrant.android.cardboardmpo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.dmitrybrant.android.cardboardmpo.MpoUtils.getFileName
import com.dmitrybrant.android.cardboardmpo.MpoUtils.loadMpoBitmapFromFile
import com.dmitrybrant.android.cardboardmpo.MpoUtils.searchBytes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.InputStream
import kotlin.math.max

/*
 * Copyright 2017+ Dmitry Brant. All rights reserved.
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
class MainActivity : AppCompatActivity() {
    private lateinit var imageLeft: ImageView
    private lateinit var imageRight: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var vrButton: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageLeft = findViewById(R.id.image_left)
        imageRight = findViewById(R.id.image_right)
        progressBar = findViewById(R.id.model_progress_bar)
        progressBar.isVisible = false
        vrButton = findViewById(R.id.vr_fab)

        vrButton.setOnClickListener { startVrActivity() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.container_view)) { view, insets ->
            val newStatusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val newNavBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val newCaptionBarInsets = insets.getInsets(WindowInsetsCompat.Type.captionBar())
            val newSystemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val rightInset = max(max(max(newStatusBarInsets.right, newCaptionBarInsets.right), newSystemBarInsets.right), newNavBarInsets.right)
            val bottomInset = max(max(max(newStatusBarInsets.bottom, newCaptionBarInsets.bottom), newSystemBarInsets.bottom), newNavBarInsets.bottom)
            val params = vrButton.layoutParams as FrameLayout.LayoutParams
            params.rightMargin = rightInset
            params.bottomMargin = bottomInset
            insets
        }

        if (intent.data != null && savedInstanceState == null) {
            beginLoadFile(intent.data!!)
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            item.itemId -> {
                checkReadPermissionThenOpen()
                return true
            }
            item.itemId -> {
                val temp = MpoApplication.getInstance().bmpLeft
                MpoApplication.getInstance().bmpLeft = MpoApplication.getInstance().bmpRight
                MpoApplication.getInstance().bmpRight = temp
                updateCurrentBitmaps()
                return true
            }
            item.itemId -> {
                showAboutDialog()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            READ_PERMISSION_REQUEST -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                beginOpenFile()
            } else {
                Toast.makeText(this, R.string.error_grant_permission, Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) {
            return
        }
        if (requestCode == OPEN_DOCUMENT_REQUEST && resultCode == RESULT_OK && data.data != null) {
            val uri: Uri = data.data!!
            grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            beginLoadFile(uri)
        }
    }

    private fun checkReadPermissionThenOpen() {
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), READ_PERMISSION_REQUEST)
        } else {
            beginOpenFile()
        }
    }

    private fun beginOpenFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.setType("*/*")
        startActivityForResult(intent, OPEN_DOCUMENT_REQUEST)
    }

    private fun beginLoadFile(uri: Uri) {
        progressBar.isVisible = true
        MpoApplication.getInstance().cleanupBitmaps()
        updateCurrentBitmaps()
        loadMpo(uri)
    }

    private fun updateCurrentBitmaps() {
        imageLeft.setImageBitmap(MpoApplication.getInstance().bmpLeft)
        imageRight.setImageBitmap(MpoApplication.getInstance().bmpRight)
    }

    private fun startVrActivity() {
        if (MpoApplication.getInstance().bmpLeft == null || MpoApplication.getInstance().bmpRight == null) {
            Toast.makeText(this, R.string.status_error_not_loaded, Toast.LENGTH_SHORT).show()
        } else {
            startActivity(Intent(this, MpoGvrActivity::class.java))
        }
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_name)
            .setMessage(R.string.about_text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun loadMpo(uri: Uri) {
        var fileName: String?

        lifecycleScope.launch(CoroutineExceptionHandler { _, throwable ->
            throwable.printStackTrace()
            fileName = null
            MpoApplication.getInstance().cleanupBitmaps()
            progressBar.isVisible = false
        }) {
            progressBar.isVisible = true

            val maxBmpSize = 1024
            var stream: InputStream? = null
            try {
                val cr = MpoApplication.getInstance().contentResolver
                fileName = getFileName(cr, uri)
                stream = cr.openInputStream(uri)
                if (stream != null) {
                    Log.d(TAG, "Processing file: $fileName")
                    /*
                     * An MPO file is simply multiple JPG files concatenated together.
                     * We can detect their locations by simply looking for the beginning markers of a
                     * JPG image. So that's what we'll do.
                     */
                    val chunkLength = 4096
                    val sig1 = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte())
                    val sig2 = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe1.toByte())

                    val fs: InputStream = BufferedInputStream(stream)
                    val tempBytes = ByteArray(chunkLength)
                    var currentOffset: Long = 0
                    val mpoOffsets = mutableListOf<Long>()

                    while (true) {
                        if (fs.read(tempBytes, 0, chunkLength) <= 0) {
                            break
                        }
                        var sigOffset = searchBytes(tempBytes, sig1, 0, chunkLength)
                        if (sigOffset == -1) {
                            sigOffset = searchBytes(tempBytes, sig2, 0, chunkLength)
                        }
                        if (sigOffset >= 0) {
                            // it's a new image
                            Log.d(TAG, "Found new JPG at offset " + (currentOffset + sigOffset))
                            mpoOffsets.add(currentOffset + sigOffset)
                        }
                        currentOffset += chunkLength.toLong()
                    }

                    if (mpoOffsets.size == 2) {
                        // this is the most common type of MPO, which is left-eye / right-eye
                        Log.d(TAG, "Found 2 JPGs, so loading 0/1...")
                        MpoApplication.getInstance().bmpLeft = loadMpoBitmapFromFile(uri, mpoOffsets[0], maxBmpSize, maxBmpSize)
                        MpoApplication.getInstance().bmpRight = loadMpoBitmapFromFile(uri, mpoOffsets[1], maxBmpSize, maxBmpSize)
                    } else if (mpoOffsets.size == 4) {
                        // I've seen this type in the wild, as well, which seems to be
                        // left-eye-hi-res / left-eye-lo-res / right-eye-hi-res / right-eye-lo-res
                        Log.d(TAG, "Found 4 JPGs, so loading 0/2...")
                        MpoApplication.getInstance().bmpLeft = loadMpoBitmapFromFile(uri, mpoOffsets[0], maxBmpSize, maxBmpSize)
                        MpoApplication.getInstance().bmpRight = loadMpoBitmapFromFile(uri, mpoOffsets[2], maxBmpSize, maxBmpSize)
                    } else {
                        throw RuntimeException("Incorrect number of MPO elements: " + mpoOffsets.size)
                    }
                }
            } finally {
                Util.closeSilently(stream)
            }

            setTitle(fileName)
            progressBar.isVisible = false
            if (fileName != null) {
                updateCurrentBitmaps()
            } else {
                Toast.makeText(applicationContext, R.string.status_error_load, Toast.LENGTH_SHORT).show()
                progressBar.isVisible = false
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"

        private const val READ_PERMISSION_REQUEST = 100
        private const val OPEN_DOCUMENT_REQUEST = 101
    }
}
