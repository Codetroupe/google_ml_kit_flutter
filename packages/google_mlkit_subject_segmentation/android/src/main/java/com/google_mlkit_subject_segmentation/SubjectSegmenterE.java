package com.google_mlkit_subject_segmentation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.Segmenter;
import com.google.mlkit.vision.segmentation.subject.Subject;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;
import com.google_mlkit_commons.InputImageConverter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

public class SubjectSegmenterE implements MethodChannel.MethodCallHandler {
    private static final String START = "vision#startSubjectSegmenter";
    private static final String CLOSE = "vision#closeSubjectSegmenter";

    private final Context context;
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

    private SubjectSegmenter initialize(MethodCall call) {
        Boolean isStream = call.argument("isStream");
        Boolean enableRawSizeMask = call.argument("enableRawSizeMask");
        return SubjectSegmentation.getClient(
                new SubjectSegmenterOptions.Builder()
                        .enableForegroundBitmap()
                        .enableForegroundConfidenceMask()
                        .enableMultipleSubjects(
                                new SubjectSegmenterOptions.SubjectResultOptions.Builder()
                                        .enableConfidenceMask()
                                        .enableSubjectBitmap()
                                        .build())
                        .build());
    }

    private void handleDetection(MethodCall call, final MethodChannel.Result result) {
        Map<String, Object> imageData = (Map<String, Object>) call.argument("imageData");
        String filePath = (String) imageData.get("path");
        String fileName;
        if (filePath != null) {
            fileName = getFileNameWithoutExtension(filePath);
        } else {
            fileName = "";
        }
        InputImage inputImage = InputImageConverter.getInputImageFromData(imageData, context, result);
        if (inputImage == null) return;
        String id = call.argument("id");
        SubjectSegmenter segmenter = instances.get(id);
        if (segmenter == null) {
            segmenter = initialize(call);
            instances.put(id, segmenter);
        }
        segmenter.process(inputImage)
                .addOnSuccessListener(
                        segmentationMask -> {
                            Map<String, Object> map = new HashMap<>();
                            FloatBuffer mask = segmentationMask.getForegroundConfidenceMask();
                            int bitmapW = segmentationMask.getForegroundBitmap().getWidth();
                            int bitmapH = segmentationMask.getForegroundBitmap().getHeight();
//                            Log.e("ForegroundBitmap>>>", "原图宽x高:" + bitmapW + "x" + bitmapH);
                            //新建一个bitmap,宽度和高度保持原图一致
                            Bitmap bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888);
                            // 创建画布
                            Canvas canvas = new Canvas(bitmap);
                            bitmap.setHasAlpha(true);
                            //循环遍历主题分割结果
                            if (!segmentationMask.getSubjects().isEmpty()) {
                                int subjectCount = segmentationMask.getSubjects().size();
                                List<String> subjectItemMap = new ArrayList<>();

                                for (int i = 0; i < subjectCount; i++) {
                                    //获取分割bitmap
                                    Bitmap itemBitmap = segmentationMask.getSubjects().get(i).getBitmap();
                                    //获取宽高
                                    int itemBitmapW = segmentationMask.getSubjects().get(i).getWidth();
                                    int itemBitmapH = segmentationMask.getSubjects().get(i).getHeight();
                                    int itemBitmapX = segmentationMask.getSubjects().get(i).getStartX();
                                    int itemBitmapY = segmentationMask.getSubjects().get(i).getStartY();
                                    String itemBitmapPath = saveBitmapToFile(context, itemBitmap, fileName + "_itemBitmap_" + i + ".png");
//                                    Log.e("itemBitmap>>>" + i, itemBitmapPath);
//                                    map.put("bitmap_path", itemBitmapPath);
                                    JSONObject jsonObject = new JSONObject();
                                    try {
                                        // 添加属性到 JSON 对象中
                                        jsonObject.put("with", itemBitmapW);
                                        jsonObject.put("height", itemBitmapH);
                                        jsonObject.put("x", itemBitmapX);
                                        jsonObject.put("y", itemBitmapY);
                                        jsonObject.put("path", itemBitmapPath);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                    // 将 JSON 对象转换为 JSON 字符串
                                    String jsonString = jsonObject.toString();
                                    subjectItemMap.add(jsonString);
                                    // 在画布上绘制背景图
                                    canvas.drawBitmap(itemBitmap, itemBitmapX, itemBitmapY, null);
                                }
//                                Log.e("itemBitmap>>>", "itemBitmap处理完成.");
                                String reSaveFilePath = saveBitmapToFile(context, bitmap, fileName + "_rsl_basic_pic.png");
//                                Log.e("reSaveFilePath>>>", reSaveFilePath);
                                map.put("basisPath", reSaveFilePath);
                                map.put("basis_item_list", subjectItemMap);
                            }

                            map.put("width", bitmapW);
                            map.put("height", bitmapH);
                            final float[] confidences = new float[bitmapW * bitmapH];
//                            mask.asFloatBuffer().get(confidences, 0, confidences.length);
                            for (int y = 0; y < bitmapH; y++) {
                                for (int x = 0; x < bitmapW; x++) {
                                    // Gets the confidence of the (x,y) pixel in the mask being in the foreground.
                                    // float foregroundConfidence = mask.getFloat();
                                    assert mask != null;
                                    confidences[y * bitmapW + x] = mask.get();
                                }
                            }
                            map.put("confidences", confidences);
                            result.success(map);
                        })
                .addOnFailureListener(
                        e -> result.error("Selfie segmentation failed!", e.getMessage(), e));
    }
    // 将 Bitmap 保存到指定路径的文件中
    // 将 Bitmap 保存到指定路径的文件中，并返回文件的绝对路径
    private static String saveBitmapToFile(Context context, Bitmap bitmap, String fileName) {
        // 获取应用的缓存目录
//        File directory = context.getExternalCacheDir();
        File directory = context.getCacheDir();
        if (directory == null) {
            return null;
        }
        // 创建目标文件
        File file = new File(directory, fileName);
        // 尝试创建文件
        try {
            // 创建文件输出流
            FileOutputStream fos = new FileOutputStream(file);
            // 将 Bitmap 压缩为 PNG 格式，可以根据需要修改格式和压缩质量
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            // 关闭输出流
            fos.close();
//            Log.e("save_image>", file.getAbsolutePath());
            // 返回文件的绝对路径
            return file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    private static String getFileNameWithoutExtension(String filePath) {
        // 获取文件名部分
        String fileNameWithExtension = getFileNameFromPath(filePath);
        // 查找最后一个点的位置
        int lastDotIndex = fileNameWithExtension.lastIndexOf('.');
        // 如果存在点且不在文件名的开头或结尾
        if (lastDotIndex > 0 && lastDotIndex < fileNameWithExtension.length() - 1) {
            // 提取文件名（不包括后缀）
            return fileNameWithExtension.substring(0, lastDotIndex);
        } else {
            // 如果没有找到点，或者点在文件名的开头或结尾，直接返回原始文件名
            return fileNameWithExtension;
        }
    }
    private static String getFileNameFromPath(String filePath) {
        // 查找最后一个斜杠的位置
        int lastSlashIndex = filePath.lastIndexOf('/');
        // 提取文件名部分
        // 返回文件名
        return filePath.substring(lastSlashIndex + 1);
    }

    private void closeDetector(MethodCall call) {
        String id = call.argument("id");
        SubjectSegmenter segmenter = instances.get(id);
        if (segmenter == null) return;
        segmenter.close();
        instances.remove(id);
    }
}
