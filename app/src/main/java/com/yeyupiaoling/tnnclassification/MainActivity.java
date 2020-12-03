package com.yeyupiaoling.tnnclassification;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.FileUtils;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.yeyupiaoling.tnnclassification.tnn.ImageClassifyUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private ImageView imageView;
    private TextView textView;
    private ArrayList<String> classNames;
    private ImageClassifyUtil imageClassifyUtil;
    private static final boolean USE_GPU = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (!hasPermission()) {
            requestPermission();
        }
        // 加载模型和标签
        classNames = Utils.ReadListFromFile(getAssets(), "label_list.txt");
//        String protoContent = getCacheDir().getAbsolutePath() + File.separator + "squeezenet_v1.1.tnnproto";
//        Utils.copyFileFromAsset(MainActivity.this, "squeezenet_v1.1.tnnproto", protoContent);
//        String modelContent = getCacheDir().getAbsolutePath() + File.separator + "squeezenet_v1.1.tnnmodel";
//        Utils.copyFileFromAsset(MainActivity.this, "squeezenet_v1.1.tnnmodel", modelContent);
        String protoContent = getCacheDir().getAbsolutePath() + File.separator + "test.opt.tnnproto";
        Utils.copyFileFromAsset(MainActivity.this, "test.opt.tnnproto", protoContent);
        String modelContent = getCacheDir().getAbsolutePath() + File.separator + "test.opt.tnnmodel";
        Utils.copyFileFromAsset(MainActivity.this, "test.opt.tnnmodel", modelContent);

        imageClassifyUtil = new ImageClassifyUtil();
        int status = imageClassifyUtil.init(modelContent, protoContent, USE_GPU ? 1 : 0);
        if (status == 0){
            Toast.makeText(MainActivity.this, "模型加载成功！", Toast.LENGTH_SHORT).show();
        }else {
            Toast.makeText(MainActivity.this, "模型加载失败！", Toast.LENGTH_SHORT).show();
            finish();
        }
        // 获取控件
        Button selectImgBtn = findViewById(R.id.select_img_btn);
        Button openCamera = findViewById(R.id.open_camera);
        imageView = findViewById(R.id.image_view);
        textView = findViewById(R.id.result_text);
        selectImgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 打开相册
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, 1);
            }
        });
        openCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 打开实时拍摄识别页面
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                startActivity(intent);
            }
        });
    }

    //leiheng
    public float innerProduct(float[] f1,float[] f2){
        float result=0.0f;
        for (int i = 0; i < f1.length; i++) {
            result+=f1[i]*f2[i];
        }
        return result;
    }

    //leiheng
    public void mirror(float[] f1,float[] f2){
        float sum=0.0f;
        for (int i = 0; i < f1.length; i++) {
            f1[i]+=f2[i];
            sum+=f1[i]*f1[i];
        }
        sum= (float) Math.sqrt(sum);
        for (int i = 0; i < f1.length; i++) {
            f1[i]/=sum;
        }
    }
    public Bitmap horverImage(Bitmap bitmap) {
        int bmpWidth = bitmap.getWidth();
        int bmpHeight = bitmap.getHeight();
        Matrix matrix = new Matrix();
        matrix.postScale(-1, 1);   //水平翻转H
        return Bitmap.createBitmap(bitmap, 0, 0, bmpWidth, bmpHeight, matrix, true);
    }

    public static Bitmap readBitmapFromFile(AssetManager assetManager, String filePath) {
        InputStream istr = null;
        try {
            istr = assetManager.open(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Bitmap bitmap = BitmapFactory.decodeStream(istr);
        return bitmap;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String image_path;
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 1) {
                if (data == null) {
                    Log.w("onActivityResult", "user photo data is null");
                    return;
                }
                Uri image_uri = data.getData();
                image_path = getPathFromURI(MainActivity.this, image_uri);
                try {
                    // 预测图像
                    FileInputStream fis = new FileInputStream(image_path);
                    imageView.setImageBitmap(BitmapFactory.decodeStream(fis));

                    final String[] IMAGES = {"1.jpg","2.jpg","3.jpg","4.jpg","5.jpg","6.jpg","7.jpg","8.jpg"};
                    int num_image=IMAGES.length;


                    int NET_INPUT=112;
                    int num_image_total=2*num_image;
                    final Bitmap[] originBitmaps=new Bitmap[num_image];
                    final Bitmap[] scaleBitmaps=new Bitmap[num_image_total];
                    for (int i = 0; i < num_image; i++) {
                        originBitmaps[i] = readBitmapFromFile(getResources().getAssets(), IMAGES[i]);
                        scaleBitmaps[i] = Bitmap.createScaledBitmap(originBitmaps[i], NET_INPUT, NET_INPUT, false);
                        scaleBitmaps[num_image+i] = horverImage(scaleBitmaps[i]);
                    }
                    float [][] result = new float[num_image_total][];

                    for (int i = 0; i < num_image_total; i++) {
                        result[i]=imageClassifyUtil.predict(scaleBitmaps[i],NET_INPUT, NET_INPUT);
                    }

                    String show_text ="no mirror:\n";
                    DecimalFormat df = new DecimalFormat("#.####");
                    for (int i = 0; i < num_image; i++) {
                        for (int j = 0; j < num_image; j++) {
                            show_text +=df.format(innerProduct(result[i],result[j]))+"\t";
                        }
                        show_text +="\n";
                    }

                    for (int i = 0; i < num_image; i++) {
                        mirror(result[i],result[num_image+i]);//return from result[i]
                    }
                    show_text +="mirror:\n";
                    for (int i = 0; i < num_image; i++) {
                        for (int j = 0; j < num_image; j++) {
                            show_text +=df.format(innerProduct(result[i],result[j]))+"\t";
                        }
                        show_text +="\n";
                    }
//                    long start = System.currentTimeMillis();
//                    float[] result = imageClassifyUtil.predictImage(image_path);
//                    long end = System.currentTimeMillis();
//                    String show_text= result.length+Arrays.toString(result);
//                    String show_text = "预测结果标签：" + (int) result[0] +
//                            "\n名称：" +  classNames.get((int) result[0]) +
//                            "\n概率：" + result[1] +
//                            "\n时间：" + (end - start) + "ms";
                    textView.setText(show_text);
                    Log.d("text", show_text);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // 根据相册的Uri获取图片的路径
    public static String getPathFromURI(Context context, Uri uri) {
        String result;
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor == null) {
            result = uri.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

    @Override
    protected void onDestroy() {
        imageClassifyUtil.deinit();
        super.onDestroy();
    }

    // check had permission
    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    // request permission
    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }
}