package com.dpthinker.astyletransfer;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Toast;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
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

    private RadioGroup mRadioGroup;
    private Button mTransferButton;

    private final static int REQUEST_CONTENT_IMG = 1;
    private final static int REQUEST_STYLE_IMG = 2;

    private final static int STYLE_IMG_SIZE = 256;
    private final static int CONTENT_IMG_SIZE = 384;
    private final static int DIM_BATCH_SIZE = 1;
    private final static int DIM_PIXEL_SIZE = 3;

    private final static int USING_CPU = 1;
    private final static int USING_GPU = 2;
    private final static int USING_NNAPI = 3;

    private int mDelegationMode = USING_CPU;

    private final static String PREDICT_MODEL = "style_predict.tflite";
    private final static String TRANSFORM_MODE = "style_transform.tflite";

    private final static int IMAGE_MEAN = 0;
    private final static int IMAGE_STD = 255;

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
        // init content and style imageview
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

        // init radio group
        mRadioGroup = findViewById(R.id.rg_model);
        mRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if (i == R.id.rb_cpu) {
                    mDelegationMode = USING_CPU;
                } else if (i == R.id.rb_gpu) {
                    mDelegationMode = USING_GPU;
                } else if (i == R.id.rb_nnapi) {
                    mDelegationMode = USING_NNAPI;
                }
            }
        });

        // init transfer button
        mTransferButton = findViewById(R.id.bt_transfer);
        mTransferButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mContentImage == null) {
                    Toast.makeText(MainActivity.this, R.string.no_contentimg, Toast. LENGTH_SHORT).show();
                    return;
                }
                if (mStyleImage == null) {
                    Toast.makeText(MainActivity.this, R.string.no_styleimg, Toast. LENGTH_SHORT).show();
                    return;
                }
                Interpreter.Options options = new Interpreter.Options();
                switch (mDelegationMode) {
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
                    // init two interpreter instances: style predict and style transform
                    Interpreter predictInterpreter = new Interpreter(
                            loadModelFile(MainActivity.this, PREDICT_MODEL), options);

                    Interpreter transformInterpreter = new Interpreter(
                            loadModelFile(MainActivity.this, TRANSFORM_MODE), options);

                    ByteBuffer bottleneck = runPredict(predictInterpreter, mStyleImage);

                    Bitmap mTransferredImage = runTransform(transformInterpreter, mContentImage, bottleneck);
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
        String imageName = "TransferredImage.jpg";
        File file = new File(myDir, imageName);
        if (file.exists()) file.delete();
        Log.i(TAG, "save transferred image to " + dataDir + "/"+ imageName);
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
        TensorImage inputTensorImage = new TensorImage(imageDataType);
        inputTensorImage.load(styleImage);

        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new NormalizeOp(IMAGE_MEAN, IMAGE_STD))
                        .build();
        inputTensorImage = imageProcessor.process(inputTensorImage);

        int outputTensorIndex = 0;
        int[] outputShape = tflite.getOutputTensor(outputTensorIndex).shape();
        DataType outputDataType = tflite.getOutputTensor(outputTensorIndex).dataType();
        TensorBuffer outputTensorBuffer
                = TensorBuffer.createFixedSize(outputShape, outputDataType);

        tflite.run(inputTensorImage.getBuffer(), outputTensorBuffer.getBuffer().rewind());

        outputTensorBuffer.getBuffer().rewind();

        return outputTensorBuffer.getBuffer();
    }

    private Bitmap runTransform(Interpreter tflite, Bitmap contentImage, ByteBuffer bottleneck) {
        int imageTensorIndex = 0;
        DataType imageDataType = tflite.getInputTensor(imageTensorIndex).dataType();
        TensorImage inputTensorImage = new TensorImage(imageDataType);
        inputTensorImage.load(contentImage);

        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new NormalizeOp(IMAGE_MEAN, IMAGE_STD))
                        .build();
        inputTensorImage = imageProcessor.process(inputTensorImage);

        inputTensorImage.getBuffer().rewind();

        Object[] inputs = new Object[2];
        inputs[0] = inputTensorImage.getBuffer();
        inputs[1] = bottleneck;

        int outputTensorIndex = 0;
        int[] outputShape = new int[]{
                DIM_BATCH_SIZE, CONTENT_IMG_SIZE, CONTENT_IMG_SIZE, DIM_PIXEL_SIZE};
        DataType outputDataType = tflite.getOutputTensor(outputTensorIndex).dataType();
        TensorBuffer outputTensorBuffer
                = TensorBuffer.createFixedSize(outputShape, outputDataType);

        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(0, outputTensorBuffer.getBuffer().rewind());
        tflite.runForMultipleInputsOutputs(inputs, outputs);
        outputTensorBuffer.getBuffer().rewind();
        float[] outputFloatArray = outputTensorBuffer.getFloatArray();
//        for (float f : outputFloatArray) {
//            Log.e(TAG, "runTransform outputFloatArray item: " + f);
//        }
        return convertOutputToBmp(outputFloatArray);

    }

    private Bitmap convertOutputToBmp(float[] output) {
        Bitmap result = Bitmap.createBitmap(
                CONTENT_IMG_SIZE, CONTENT_IMG_SIZE, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[CONTENT_IMG_SIZE * CONTENT_IMG_SIZE];
        for (int i = 0; i < CONTENT_IMG_SIZE * CONTENT_IMG_SIZE * 3; i++) {
            int a = 0xFF;
            float r = output[i] * 255.0f;
            float g = output[i++] * 255.0f;
            float b = output[i++] * 255.0f;
            pixels[(i + 1) / 3 - 1] = a << 24 | (int)r << 16 | (int) g << 8 | (int) b;
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

                bitmap = preProcessBitmap(bitmap,
                        requestCode == REQUEST_CONTENT_IMG ? CONTENT_IMG_SIZE : STYLE_IMG_SIZE);

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

    private Bitmap preProcessBitmap(Bitmap bitmap, int size) {
        return Bitmap.createScaledBitmap(bitmap, size, size, false);
    }
}
