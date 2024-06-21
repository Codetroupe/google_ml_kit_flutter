package com.google_mlkit_subject_segmentation;

import static java.lang.Math.max;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.android.gms.common.api.OptionalModuleApi;
import com.google.android.gms.common.moduleinstall.ModuleInstall;
import com.google.android.gms.common.moduleinstall.ModuleInstallClient;
import com.google.android.gms.tasks.TaskExecutors;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.Subject;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;
import com.google_mlkit_commons.InputImageConverter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import kotlinx.coroutines.GlobalScope;

public class SubjectSegmenterE implements MethodChannel.MethodCallHandler {
    private static final String START = "vision#startSubjectSegmenter";
    private static final String CLOSE = "vision#closeSubjectSegmenter";

    private final Context context;
    // 使用 ConcurrentHashMap 来保证线程安全
//    private final Map<String, SubjectSegmenter> instances = new HashMap<>();
//    private final ConcurrentHashMap<String, WeakReference<SubjectSegmenter>> instances = new ConcurrentHashMap<>();
    private final Map<String, SubjectSegmenter> instances = new HashMap<>();

    public SubjectSegmenterE(Context context) {
        this.context = context;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        String method = call.method;
        switch (method) {
            case START:
                handleDetection(call, result);
                break;
            case CLOSE:
                closeDetector(call);
                result.success(null);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private SubjectSegmenter initialize() {

//        Boolean isStream = call.argument("isStream");
//        Boolean enableRawSizeMask = call.argument("enableRawSizeMask");
        SubjectSegmenterOptions.SubjectResultOptions subjectResultOptions =
                new SubjectSegmenterOptions.SubjectResultOptions.Builder()
                        .enableSubjectBitmap()
                        .build();
        SubjectSegmenterOptions options = new SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .enableMultipleSubjects(subjectResultOptions)
                .build();

        SubjectSegmenter segmenter = SubjectSegmentation.getClient(options);
        return segmenter;
    }

    private void handleDetection(MethodCall call, final MethodChannel.Result result) {

        String savePath = call.argument("savePath");//用hash命名

        String imagePath = call.argument("imagePath");
        File imageFile = new File(imagePath);
        // 将 File 对象转换为 Uri
        Uri imageUri = Uri.fromFile(imageFile);
        Log.e("DebugLog", imagePath + "==" + imageUri);
        try {
            Bitmap imageBitmap = BitmapUtils.getBitmapFromContentUri(context.getContentResolver(), imageUri);
            if (imageBitmap == null) {
                return;
            }

            // Get the dimensions of the image view
            Pair<Integer, Integer> targetedSize = getTargetedWidthHeight();

            // Determine how much to scale down the image
            float scaleFactor =
                    max(
                            (float) imageBitmap.getWidth() / (float) targetedSize.first,
                            (float) imageBitmap.getHeight() / (float) targetedSize.second);

            Bitmap resizedBitmap =
                    Bitmap.createScaledBitmap(
                            imageBitmap,
                            (int) (imageBitmap.getWidth() / scaleFactor),
                            (int) (imageBitmap.getHeight() / scaleFactor),
                            true);


            InputImage inputImage = InputImage.fromBitmap(resizedBitmap, 0);
            ScopedExecutor executor = new ScopedExecutor(TaskExecutors.MAIN_THREAD);
            SubjectSegmenter subjectSegmenter =
                    SubjectSegmentation.getClient(
                            new SubjectSegmenterOptions.Builder()
                                    .enableForegroundBitmap()
                                    .enableMultipleSubjects(
                                            new SubjectSegmenterOptions.SubjectResultOptions.Builder()
                                                    .enableConfidenceMask()
                                                    .enableSubjectBitmap()
                                                    .build())
                                    .build());
            subjectSegmenter.process(inputImage).addOnSuccessListener(
                            executor,
                            segmentationMask -> {
                                Log.e("DebugLog", "5");
                                processSegmentationSuccess(segmentationMask, savePath, result);
                            })
                    .addOnFailureListener(
                            executor,
                            e -> {
                                Log.e("DebugLog", "4-1" + e.getMessage());
                                result.error("Selfie segmentation failed!", e.getMessage(), e);
                            });

        } catch (Exception e) {
            Log.e("DebugLog", "Bitmap error:" + e.getMessage());
        }

//        Map<String, Object> imageData = (Map<String, Object>) call.argument("imageData");
//        InputImage inputImage = InputImageConverter.getInputImageFromData(imageData, context, result);
////        Log.e("DebugLog", String.valueOf(inputImage == null));
//        if (inputImage == null) return;
//
//        Log.e("DebugLog", "1");
//        ModuleInstallClient moduleInstallClient = ModuleInstall.getClient(context);
//        OptionalModuleApi optionalModuleApi = initialize();
//        moduleInstallClient
//                .areModulesAvailable(optionalModuleApi)
//                .addOnSuccessListener(
//                        response -> {
//                            if (response.areModulesAvailable()) {
//                                // Modules are present on the device...
//                                Log.e("TFLite", "TFLite is available");
//                                try {
//                                    String id = call.argument("id");
//                                    Log.e("DebugLog", "3::" + id);
//                                    SubjectSegmenter segmenter = instances.get(id);
//                                    if (segmenter == null) {
//                                        Log.e("DebugLog", "3=null");
//                                        segmenter = initialize();
//                                        instances.put(id, segmenter);
//                                    }
//                                    String savePath = call.argument("savePath");//用hash命名
//
//                                    Log.e("DebugLog", "4");//
//                                    segmenter.process(inputImage)
//                                            .addOnSuccessListener(
//                                                    segmentationMask -> {
//                                                        Log.e("DebugLog", "5");
//                                                        processSegmentationSuccess(segmentationMask, savePath, result);
//                                                    })
//                                            .addOnFailureListener(
//                                                    e -> {
//                                                        Log.e("DebugLog", "4-1" + e.getMessage());
//                                                        result.error("Selfie segmentation failed!", e.getMessage(), e);
//                                                    });
//
//                                    Log.e("DebugLog", "6");
//                                } catch (Exception e) {
//                                    Log.e("DebugLog", "7");
//                                    result.error("Exception occurred", e.getMessage(), e);
//                                }
//                            } else {
//                                // Modules are not present on the device...
//                                Log.e("TFLite", "TFLite is not available response");
//                            }
//                        })
//                .addOnFailureListener(
//                        e -> {
//                            // Handle failure…
//                            Log.e("TFLite", "TFLite is not available,error:" + e.getMessage());
//
//                        });

        Log.e("DebugLog", "2");

    }


    private void processSegmentationSuccess(SubjectSegmentationResult segmentationMask, String savePath, MethodChannel.Result result) {

        Log.e("DebugLog", "8");
        Map<String, Object> map = new HashMap<>();
        int bitmapW = segmentationMask.getForegroundBitmap().getWidth();
        int bitmapH = segmentationMask.getForegroundBitmap().getHeight();
        Bitmap bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        bitmap.setHasAlpha(true);
        Log.e("DebugLog", "9");
        //获取主体分割结果即前景图片
        List<Subject> subjects = segmentationMask.getSubjects();
        if (!subjects.isEmpty()) {
            int subjectCount = subjects.size();
            List<String> subjectItemMap = new ArrayList<>();
            //获取每个前景信息并且保存,xy为原图定位
            for (int i = 0; i < subjectCount; i++) {
                Bitmap itemBitmap = subjects.get(i).getBitmap();
                if (itemBitmap == null) {
                    Log.e("Bitmap is null", "Item bitmap is null at index: " + i);
                    continue;
                }
                int itemBitmapW = subjects.get(i).getWidth();
                int itemBitmapH = subjects.get(i).getHeight();
                int itemBitmapX = subjects.get(i).getStartX();
                int itemBitmapY = subjects.get(i).getStartY();
                String itemBitmapPath = saveBitmapToFile(context, itemBitmap, "rmbg_item_bitmap_" + i + ".png", savePath);
                if (itemBitmapPath != null) {
                    JSONObject jsonObject = new JSONObject();
                    try {
                        jsonObject.put("width", itemBitmapW);
                        jsonObject.put("height", itemBitmapH);
                        jsonObject.put("x", itemBitmapX);
                        jsonObject.put("y", itemBitmapY);
                        jsonObject.put("path", itemBitmapPath);
                    } catch (JSONException e) {
                        Log.e("JSON creation error", Objects.requireNonNull(e.getMessage()));
                        result.error("JSON creation error", e.getMessage(), null);
                        return; // 提前返回，防止进一步错误
                    }
                    String jsonString = jsonObject.toString();
                    subjectItemMap.add(jsonString);
                } else {
                    Log.e("File save error", "Failed to save item bitmap at index: " + i);
                    result.error("File save error", "Failed to save item bitmap", null);
                    return; // 文件保存失败，提前返回
                }
                // 在画布上绘制背景图
                canvas.drawBitmap(itemBitmap, itemBitmapX, itemBitmapY, null);
                itemBitmap.recycle(); // 回收内存
            }
            //获取原图切图结果,用于basic
            String reSaveFilePath = saveBitmapToFile(context, bitmap, "rmbg_basic_pic.png", savePath);
            if (reSaveFilePath == null) {
                Log.e("File save error", "Failed to save basic picture");
                result.error("File save error", "Failed to save basic picture", null);
                return; // 提前返回，防止进一步错误
            }
            map.put("basisPath", reSaveFilePath);
            map.put("basis_item_list", subjectItemMap);
        }
        //原图宽高
        map.put("width", bitmapW);
        map.put("height", bitmapH);
        result.success(map);
        bitmap.recycle(); // 回收bitmap内存
        Log.e("DebugLog", "10");
    }

    private static String saveBitmapToFile(Context context, Bitmap bitmap, String fileName, String customPath) {
        Log.e("DebugLog", "11");
        // 获取应用的缓存目录
        File directory;
        if (customPath != null) {
            directory = new File(customPath);
            // 确保自定义路径是个目录
            if (!directory.isDirectory()) {
                // 如果目录不存在，尝试创建
                throw new IllegalArgumentException("File custom path cannot be null or empty:" + customPath + ";default:" + context.getCacheDir().getPath());
            }
        } else {
            directory = context.getCacheDir();
            if (directory == null) {
                return null;
            }
        }
        // 创建目标文件
        File file = new File(directory, fileName);
        // 判断文件是否已经存在，如果存在，可以进行一些额外的处理，比如提示用户或覆盖前确认
        if (file.exists()) {
            // 处理文件已存在的逻辑
            return file.getAbsolutePath(); // 或者是用户确认后的操作
        }
        // 使用 try-with-resources 确保资源被正确关闭
        try (FileOutputStream fos = new FileOutputStream(file)) {
            // 将 Bitmap 压缩为 PNG 格式
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            // 返回文件的绝对路径
            return file.getAbsolutePath();
        } catch (IOException e) {
            // 异常处理可以更加细化，比如根据不同的异常类型进行不同的处理
            throw new IllegalArgumentException("File save error:" + e.getMessage());
        }
    }


    private void closeDetector(MethodCall call) {

        String id = call.argument("id");
        Log.e("DebugLog", "12:::" + id);
        if (id == null) {
            return;
        }
        SubjectSegmenter segmenter = instances.get(id);
        if (segmenter == null) return;
        Log.e("DebugLog", "13");
        segmenter.close();
        instances.remove(id);
        Log.e("DebugLog", "14");
    }


    private Pair<Integer, Integer> getTargetedWidthHeight() {
        int targetWidth;
        int targetHeight;
        boolean isLandScape =
                (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);

        targetWidth = isLandScape ? 1920 : 1080;
        targetHeight = isLandScape ? 1080 : 1920;


        return new Pair<>(targetWidth, targetHeight);
    }
}
