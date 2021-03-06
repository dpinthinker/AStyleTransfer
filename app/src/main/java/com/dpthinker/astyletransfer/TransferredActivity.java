/**
 * Copyright 2019-2020 dpthinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dpthinker.astyletransfer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;

public class TransferredActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transferred);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String dataDir = getFilesDir().toString();
        File myDir = new File(dataDir);
        String fileName = "TransferredImage.jpg";
        File file = new File(myDir, fileName);
        Bitmap transferredBmp = BitmapFactory.decodeFile(file.getAbsolutePath());
        ImageView transferredImgView = findViewById(R.id.transferred_img);
        transferredImgView.setImageBitmap(transferredBmp);

        TextView resTextView = findViewById(R.id.transferred_str);
        resTextView.setText(TimerRecorder.getInstance().getCollectedTime());
    }

    @Override
    protected void onPause() {
        super.onPause();
        TimerRecorder.getInstance().clean();
        finish();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        TimerRecorder.getInstance().clean();
        finish();
    }
}
