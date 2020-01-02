package com.dpthinker.astyletransfer;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.io.File;

public class TransferredActivity extends AppCompatActivity {

    private static final String TAG = "TransferredActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transferred);
        String dataDir = getFilesDir().toString();
        File myDir = new File(dataDir);
        String fname = "TransferredImage.jpg";
        File file = new File(myDir, fname);
        Log.e(TAG, file.getName() + " is exist: " + file.exists());
        Bitmap transferredBmp = BitmapFactory.decodeFile(file.getAbsolutePath());
        ImageView transferredImgView = findViewById(R.id.transferred_img);
        transferredImgView.setImageBitmap(transferredBmp);
    }
}
