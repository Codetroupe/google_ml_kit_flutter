package com.google_mlkit_subject_segmentation;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.Segmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;
import com.google_mlkit_commons.InputImageConverter;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

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
//
//        SubjectSegmenterOptions.Builder builder = new SubjectSegmenterOptions.Builder();
//
//        builder.enableMultipleSubjects(new SubjectSegmenterOptions.SubjectResultOptions.Builder().enableConfidenceMask().build())
//
//        subjectSegmenter =
//
//
//        SubjectSegmenterOptions options = builder.build();
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
                            int maskWidth = segmentationMask.getForegroundBitmap().getWidth();
                            int maskHeight = segmentationMask.getForegroundBitmap().getHeight();

                            map.put("width", maskWidth);
                            map.put("height", maskHeight);

                            final float[] confidences = new float[maskWidth * maskHeight];
//                            mask.asFloatBuffer().get(confidences, 0, confidences.length);

                            for (int y = 0; y < maskHeight; y++) {
                                for (int x = 0; x < maskWidth; x++) {
                                    // Gets the confidence of the (x,y) pixel in the mask being in the foreground.
                                    // float foregroundConfidence = mask.getFloat();
                                    assert mask != null;
                                    confidences[y * maskWidth + x] = mask.get();
                                }
                            }

                            map.put("confidences", confidences);

                            result.success(map);
                        })
                .addOnFailureListener(
                        e -> result.error("Selfie segmentation failed!", e.getMessage(), e));
    }

    private void closeDetector(MethodCall call) {
        String id = call.argument("id");
        SubjectSegmenter segmenter = instances.get(id);
        if (segmenter == null) return;
        segmenter.close();
        instances.remove(id);
    }
}
