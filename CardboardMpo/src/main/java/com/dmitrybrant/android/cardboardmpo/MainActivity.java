package com.dmitrybrant.android.cardboardmpo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/*
 * Copyright 2017-2018 Dmitry Brant. All rights reserved.
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
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private static final int READ_PERMISSION_REQUEST = 100;
    private static final int OPEN_DOCUMENT_REQUEST = 101;

    private MpoApplication app;

    private ImageView imageLeft;
    private ImageView imageRight;
    private ProgressBar progressBar;
    private View vrButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        app = MpoApplication.getInstance();

        imageLeft = findViewById(R.id.image_left);
        imageRight = findViewById(R.id.image_right);
        progressBar = findViewById(R.id.model_progress_bar);
        progressBar.setVisibility(View.GONE);
        vrButton = findViewById(R.id.vr_fab);

        vrButton.setOnClickListener((View v) -> startVrActivity());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.container_view), (v, insets) -> {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) vrButton.getLayoutParams();
            params.topMargin = insets.getSystemWindowInsetTop();
            params.bottomMargin = insets.getSystemWindowInsetBottom();
            params.leftMargin = insets.getSystemWindowInsetLeft();
            params.rightMargin = insets.getSystemWindowInsetRight();
            return insets.consumeSystemWindowInsets();
        });

        if (getIntent().getData() != null && savedInstanceState == null) {
            beginLoadFile(getIntent().getData());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_open_model:
                checkReadPermissionThenOpen();
                return true;
            case R.id.menu_reverse_eyes:
                Bitmap temp = app.getBmpLeft();
                app.setBmpLeft(app.getBmpRight());
                app.setBmpRight(temp);
                updateCurrentBitmaps();
                return true;
            case R.id.menu_about:
                showAboutDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case READ_PERMISSION_REQUEST:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    beginOpenFile();
                } else {
                    Toast.makeText(this, R.string.error_grant_permission, Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == OPEN_DOCUMENT_REQUEST && resultCode == RESULT_OK && resultData.getData() != null) {
            Uri uri = resultData.getData();
            grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            beginLoadFile(uri);
        }
    }

    private void checkReadPermissionThenOpen() {
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    READ_PERMISSION_REQUEST);
        } else {
            beginOpenFile();
        }
    }

    private void beginOpenFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        startActivityForResult(intent, OPEN_DOCUMENT_REQUEST);
    }

    private void beginLoadFile(@NonNull Uri uri) {
        progressBar.setVisibility(View.VISIBLE);
        app.cleanupBitmaps();
        updateCurrentBitmaps();
        new MpoLoadTask().execute(uri);
    }

    private class MpoLoadTask extends MpoUtils.MpoLoadTask {
        @Override
        protected void onProgressUpdate(Integer... progress) {
        }

        @Override
        protected void onPostExecute(String fileName) {
            if (isDestroyed()) {
                return;
            }
            setTitle(fileName);
            progressBar.setVisibility(View.GONE);
            if (fileName != null) {
                updateCurrentBitmaps();
            } else {
                Toast.makeText(getApplicationContext(), R.string.status_error_load, Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
            }
        }
    }

    private void updateCurrentBitmaps() {
        imageLeft.setImageBitmap(app.getBmpLeft());
        imageRight.setImageBitmap(app.getBmpRight());
    }

    private void startVrActivity() {
        if (app.getBmpLeft() == null || app.getBmpRight() == null) {
            Toast.makeText(this, R.string.status_error_not_loaded, Toast.LENGTH_SHORT).show();
        } else {
            startActivity(new Intent(this, MpoGvrActivity.class));
        }
    }

    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.about_text)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
