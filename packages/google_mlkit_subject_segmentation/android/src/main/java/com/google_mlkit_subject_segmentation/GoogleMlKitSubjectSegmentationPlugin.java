package com.google_mlkit_subject_segmentation;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodChannel;

public class GoogleMlKitSubjectSegmentationPlugin implements FlutterPlugin {
    private MethodChannel channel;
    private static final String channelName = "google_mlkit_subject_segmenter";

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), channelName);
        channel.setMethodCallHandler(new SubjectSegmenterE(flutterPluginBinding.getApplicationContext()));
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }
}
