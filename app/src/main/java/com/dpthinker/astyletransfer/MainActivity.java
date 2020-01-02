package com.dpthinker.astyletransfer;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;


public class MainActivity extends AppCompatActivity {
    private final static String TAG= "MainActivity";

    private ImageView mContentImageView;
    private ImageView mStyleImageView;

    private Bitmap mContentImage;
    private Bitmap mStyleImage;
    private Bitmap mTransferredImage;

    private RadioGroup mRadioGroup;
    private Button mTransferButton;

    private final static int REQUEST_CONTENT_IMG = 1;
    private final static int REQUEST_STYLE_IMG = 2;

    private final static int STYLE_IMG_SIZE = 256;
    private final static int CONTENT_IMG_SIZE = 384;
    private final static int NUM_BYTES_PER_CHANNEL = 4;
    private final static int DIM_BATCH_SIZE = 1;
    private final static int DIM_PIXEL_SIZE = 3;

    private final static int USING_CPU = 1;
    private final static int USING_GPU = 2;
    private final static int USING_NNAPI = 3;

    private int mDelagationMode = USING_CPU;

    private final static String PREDICT_MODEL = "style_predict.tflite";
    private final static String TRANSFROM_MODE = "style_transform.tflite";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        View decorView = getWindow().getDecorView();
        // Hide the status bar.
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);

        initViews();
    }

    private void initViews() {
        mContentImageView = findViewById(R.id.content_img);
        mContentImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, REQUEST_CONTENT_IMG);
            }
        });

        mStyleImageView = findViewById(R.id.style_img);
        mStyleImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, REQUEST_STYLE_IMG);
            }
        });


        mRadioGroup = findViewById(R.id.rg_model);
        mRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if (i == R.id.rb_cpu) {
                    mDelagationMode = USING_CPU;
                } else if (i == R.id.rb_gpu) {
                    mDelagationMode = USING_GPU;
                } else if (i == R.id.rb_nnapi) {
                    mDelagationMode = USING_NNAPI;
                }
            }
        });

        mTransferButton = findViewById(R.id.bt_transfer);
        mTransferButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Interpreter.Options options = new Interpreter.Options();
                switch (mDelagationMode) {
                    case USING_CPU:
                        break;
                    case USING_GPU:
                        options.addDelegate(new GpuDelegate());
                        break;
                    case USING_NNAPI:
                        options.addDelegate(new NnApiDelegate());
                        break;
                }

                try {
                    // Should init two interpreter instances: style predict and style transform
                    Interpreter predictInterpreter = new Interpreter(
                            loadModelFile(MainActivity.this, PREDICT_MODEL), options);

                    Interpreter tranfromInterpreter = new Interpreter(
                            loadModelFile(MainActivity.this, TRANSFROM_MODE), options);

                    ByteBuffer bottleneck = runPredict(predictInterpreter, mStyleImage);

                    Bitmap mTransferredImage = runTransform(tranfromInterpreter, mContentImage, bottleneck);
                    saveImage(mTransferredImage);

                    Intent intent = new Intent(MainActivity.this, TransferredActivity.class);
                    startActivity(intent);
                } catch (IOException ex) {
                    Log.e(TAG, "Init interpreter failed with IOException!" + ex);
                }
            }
        });
    }

    private void saveImage(Bitmap finalBitmap) {
        String dataDir = getFilesDir().toString();
        File myDir = new File(dataDir);
        myDir.mkdirs();
        String fname = "TransferredImage.jpg";
        File file = new File(myDir, fname);
        if (file.exists()) file.delete();
        Log.i(TAG, dataDir + "/"+ fname);
        try {
            FileOutputStream out = new FileOutputStream(file);
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private ByteBuffer runPredict(Interpreter tflite, Bitmap styleImage) {
        int imageTensorIndex = 0;
        DataType imageDataType = tflite.getInputTensor(imageTensorIndex).dataType();
        TensorImage inputImageBuffer = new TensorImage(imageDataType);
        inputImageBuffer.load(styleImage);
        inputImageBuffer.getBuffer().rewind();

        int probabilityTensorIndex = 0;
        int[] probabilityShape =
                tflite.getOutputTensor(probabilityTensorIndex).shape();
        DataType probabilityDataType = tflite.getOutputTensor(probabilityTensorIndex).dataType();
        TensorBuffer outputProbabilityBuffer
                = TensorBuffer.createFixedSize(probabilityShape, probabilityDataType);

        tflite.run(inputImageBuffer.getBuffer(), outputProbabilityBuffer.getBuffer().rewind());

        float[] out = outputProbabilityBuffer.getFloatArray();
//        for (float f : out ) {
//            Log.e(TAG, "out: " + f);
//        }

        Log.e(TAG, "out size: " + out.length);
        outputProbabilityBuffer.getBuffer().rewind();



        return outputProbabilityBuffer.getBuffer();
    }

    private Bitmap runTransform(Interpreter tflite, Bitmap contentImage, ByteBuffer bottleneck) {
        int imageTensorIndex = 0;
        DataType imageDataType = tflite.getInputTensor(imageTensorIndex).dataType();
        TensorImage inputImageBuffer = new TensorImage(imageDataType);
        inputImageBuffer.load(contentImage);
        inputImageBuffer.getBuffer().rewind();

        Object[] inputs = new Object[2];
        inputs[0] = inputImageBuffer.getBuffer();
        inputs[1] = bottleneck;

        int probabilityTensorIndex = 0;
        int[] probabilityShape = new int[]{
                DIM_BATCH_SIZE, CONTENT_IMG_SIZE, CONTENT_IMG_SIZE, DIM_PIXEL_SIZE};
        DataType probabilityDataType = tflite.getOutputTensor(probabilityTensorIndex).dataType();
        TensorBuffer outputProbabilityBuffer
                = TensorBuffer.createFixedSize(probabilityShape, probabilityDataType);

        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(0, outputProbabilityBuffer.getBuffer());
        tflite.runForMultipleInputsOutputs(inputs, outputs);
        outputProbabilityBuffer.getBuffer().rewind();
        float[] outFloatArray = outputProbabilityBuffer.getFloatArray();
        int[] shape = outputProbabilityBuffer.getShape();
        for (int s: shape) {
            Log.e(TAG, "outputProbabilityBuffer.getShape() item : " + s);
        }
        Log.e(TAG,"outFloatArray size: " + outFloatArray.length);
//        for (float i : outFloatArray) {
//            Log.e(TAG, "outFloatArray item: " + i);
//        }
        return convertOutputToBmp(outFloatArray);
    }

    private Bitmap convertOutputToBmp(float[] out) {
        Bitmap result = Bitmap.createBitmap(
                CONTENT_IMG_SIZE , CONTENT_IMG_SIZE,Bitmap.Config.ARGB_8888);
        int[] pixels = new int[CONTENT_IMG_SIZE * CONTENT_IMG_SIZE];
        for (int i = 0; i < CONTENT_IMG_SIZE * CONTENT_IMG_SIZE * 3; i++) {
            int a = 0xFF;
            float r = out[i] * 255.0f;
            float g = out[i++] * 255.0f;
            float b = out[i++] * 255.0f;
//            Log.e(TAG, "convertOutputToBmp r: " + r);
//            Log.e(TAG, "convertOutputToBmp g: " + g);
//            Log.e(TAG, "convertOutputToBmp b: " + b);
            pixels[(i + 1) / 3 - 1] = a << 24 | (int)r << 16 | (int)g << 8 | (int)b;
            //Log.e(TAG, "convertOutputToBmp : " + pixels[(i + 1) / 3 - 1]);
        }
        result.setPixels(pixels, 0, CONTENT_IMG_SIZE, 0, 0, CONTENT_IMG_SIZE, CONTENT_IMG_SIZE);
        return result;
    }

    /** Memory-map the model file in Assets. */
    private MappedByteBuffer loadModelFile(Activity activity, String modePath) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(modePath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Uri uri = data.getData();
            try {
                Bitmap bitmap = BitmapFactory.decodeStream(
                        this.getContentResolver().openInputStream(uri));

                bitmap = preprocesBitmap(bitmap,
                        requestCode == REQUEST_CONTENT_IMG? CONTENT_IMG_SIZE : STYLE_IMG_SIZE);

                if (requestCode == REQUEST_CONTENT_IMG) {
                    mContentImageView.setImageBitmap(bitmap);
                    mContentImage = bitmap;
                } else {
                    mStyleImageView.setImageBitmap(bitmap);
                    mStyleImage = bitmap;
                }
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Not found input image: " + uri.toString());
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private Bitmap preprocesBitmap(Bitmap bitmap, int size) {
        return Bitmap.createScaledBitmap(bitmap, size, size, false);
    }
}
