package io.vin.android.scanner.engine.impl;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.Camera;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import io.vin.android.scanner.Result;
import io.vin.android.scanner.engine.DecodeEngine;
import io.vin.android.scanner.util.DisplayUtils;
import io.vin.android.zbar.Config;
import io.vin.android.zbar.Image;
import io.vin.android.zbar.ImageScanner;
import io.vin.android.zbar.Symbol;
import io.vin.android.zbar.SymbolSet;
import io.vin.android.zbar.Symbology;

/**
 * Zbar Decode Engine
 *Author     Vin
 *Mail       vinintg@gmail.com
 *Createtime 2019-07-26 14:25
 *Modifytime 2019-07-26 14:25
 */
public class ZbarDecodeEngine implements DecodeEngine {
    private final WeakReference<Context> mContext;
    private final WeakReference<View> mView;
    private ImageScanner mDecoder;
    private List<Symbology> mSymbologyList;
    private Rect mDecodeUIRect;
    public ZbarDecodeEngine(Context context,View view){
        this.mContext = new WeakReference<>(context);
        this.mView = new WeakReference<>(view);
        this.mDecoder = new ImageScanner();
        this.mSymbologyList = Symbology.ALL;
        configDecoder();
    }

    private void configDecoder(){
        this.mDecoder = new ImageScanner();
        this.mDecoder.setConfig(Symbol.NONE, Config.X_DENSITY, 3);
        this.mDecoder.setConfig(Symbol.NONE, Config.Y_DENSITY, 3);
        //Disable all the Symbols
        this.mDecoder.setConfig(Symbol.NONE, Config.ENABLE, 0);
        //Enable the setting Symbols
        for (Symbology format : mSymbologyList) {
            this.mDecoder.setConfig(format.getId(), Config.ENABLE, 1);
        }
    }

    private Rect getScaledRect(Rect framingRect, int previewWidth, int previewHeight) {
        int scanerViewWidth = this.mView.get().getWidth();
        int scanerViewHeight = this.mView.get().getHeight();

        int width, height;
        if (DisplayUtils.getScreenOrientation(mContext.get()) == Configuration.ORIENTATION_PORTRAIT//竖屏使用
                && previewHeight < previewWidth) {
            width = previewHeight;
            height = previewWidth;
        } else if (DisplayUtils.getScreenOrientation(mContext.get()) == Configuration.ORIENTATION_LANDSCAPE//横屏使用
                && previewHeight > previewWidth) {
            width = previewHeight;
            height = previewWidth;
        } else {
            width = previewWidth;
            height = previewHeight;
        }

        Rect scaledRect = new Rect(framingRect);
        scaledRect.left = scaledRect.left * width / scanerViewWidth;
        scaledRect.right = scaledRect.right * width / scanerViewWidth;
        scaledRect.top = scaledRect.top * height / scanerViewHeight;
        scaledRect.bottom = scaledRect.bottom * height / scanerViewHeight;

        return scaledRect;
    }

    private Rect getRotatedRect(Rect rect, int previewWidth, int previewHeight) {
        int orientation = getDisplayOrientation();
        Rect rotatedRect = new Rect(rect);

        if (orientation == 90) {//若相机图像需要顺时针旋转90度，则将扫码框逆时针旋转90度
            rotatedRect.left = rect.top;
            rotatedRect.top = previewHeight - rect.right;
            rotatedRect.right = rect.bottom;
            rotatedRect.bottom = previewHeight - rect.left;
        } else if (orientation == 180) {//若相机图像需要顺时针旋转180度,则将扫码框逆时针旋转180度
            rotatedRect.left = previewWidth - rect.right;
            rotatedRect.top = previewHeight - rect.bottom;
            rotatedRect.right = previewWidth - rect.left;
            rotatedRect.bottom = previewHeight - rect.top;
        } else if (orientation == 270) {//若相机图像需要顺时针旋转270度，则将扫码框逆时针旋转270度
            rotatedRect.left = previewWidth - rect.bottom;
            rotatedRect.top = rect.left;
            rotatedRect.right = previewWidth - rect.top;
            rotatedRect.bottom = rect.right;
        }

        return rotatedRect;
    }

    public int getDisplayOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(0, info);
        int degrees = 0;
        switch (((WindowManager) mContext.get().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation()) {
            case 0:
                degrees = 0;
                break;
            case 1:
                degrees = 90;
                break;
            case 2:
                degrees = 180;
                break;
            case 3:
                degrees = 270;
                break;
        }
        if (info.facing == 1) {
            return (360 - ((info.orientation + degrees) % 360)) % 360;
        }
        return ((info.orientation - degrees) + 360) % 360;
    }

    @Override
    public void enableCache(Boolean enable) {
        this.mDecoder.enableCache(true);
    }

    @Override
    public void setSymbology(List<Symbology> symbologyList) {
        this.mSymbologyList = symbologyList;
        configDecoder();
    }

    @Override
    public void setDecodeRect(Rect decodeRect) {
        this.mDecodeUIRect = decodeRect;
    }

    @Override
    public void setDecodeRect(View view) {
        Rect decodeArea = new Rect();

        //1.通过getGlobalVisibleRect计算Rect
        Rect cropRect = new Rect();
        view.getGlobalVisibleRect(cropRect);

        Rect scanerRect = new Rect();
        mView.get().getGlobalVisibleRect(scanerRect);

        decodeArea = cropRect;
        decodeArea.top = cropRect.top - scanerRect.top;
        decodeArea.bottom = cropRect.bottom - scanerRect.top;

        setDecodeRect(decodeArea);
    }

    @Override
    public List<Result> decode(byte[] data, Camera camera) {
        Camera.Size size = camera.getParameters().getPreviewSize();
        int width = size.width;
        int height = size.height;
        Image image = new Image(width, height, "Y800");
        image.setData(data);
        //2.根据感应器旋转角度,屏幕旋转角度,和UI层面框大小.决定识别区域
        if (mDecodeUIRect != null) {
            Rect scaledRect = getScaledRect(mDecodeUIRect, width, height);
            Rect rotatedRect = getRotatedRect(scaledRect, width, height);
            image.setCrop(rotatedRect.left, rotatedRect.top, rotatedRect.width(), rotatedRect.height());
        }
        //3.解析结果
        List<Result> resultList = new ArrayList<>();
        int result = this.mDecoder.scanImage(image);
        if (result != 0) {
            SymbolSet resultSet = this.mDecoder.getResults();
            for (Symbol symbolItem : resultSet) {
                if (TextUtils.isEmpty(symbolItem.getData())) {
                    continue;
                }
                Result resultItem = new Result();
                resultItem.setContents(symbolItem.getData());
                resultItem.setSymbology(Symbology.getFormatById(symbolItem.getType()));
                resultList.add(resultItem);
            }
        }
        return resultList;
    }


}
