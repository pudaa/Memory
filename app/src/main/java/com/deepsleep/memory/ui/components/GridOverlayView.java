package com.deepsleep.memory.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 相机三分构图网格线覆盖层。
 * <p>
 * 借鉴系统相机/成熟相机 App 的取景辅助：绘制两条竖线 + 两条横线，将画面三等分，
 * 帮助用户对齐被摄主体（如作文纸页面、书写区域）。线条为白色半透明细线，不拦截触摸。
 * <p>
 * 三分线必须基于<b>相机画面的实际渲染区域</b>计算，而非整个屏幕：
 * 16:9 铺满时渲染区域即全屏；4:3 使用 FIT_CENTER 居中显示时渲染区域是画面矩形，
 * 若按屏幕计算会导致网格线与画面错位（只能看到一两条线且画歪）。
 */
public class GridOverlayView extends View {

    private final Paint linePaint;
    /** 相机画面实际渲染区域（本 View 坐标系内）；null 表示全屏 */
    private RectF renderRect;

    public GridOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(0x66FFFFFF); // 白色 40% 透明度
        linePaint.setStrokeWidth(dp(1f));
        setWillNotDraw(false);
    }

    /** 设置相机画面渲染区域（画面 FIT 居中时非全屏）；null 恢复全屏 */
    public void setRenderRect(@Nullable RectF rect) {
        renderRect = rect;
        invalidate();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = 0;
        float top = 0;
        float right = getWidth();
        float bottom = getHeight();
        if (renderRect != null) {
            left = renderRect.left;
            top = renderRect.top;
            right = renderRect.right;
            bottom = renderRect.bottom;
        }
        if (right - left <= 0 || bottom - top <= 0)
            return;
        // 两条竖线：将画面宽度三等分
        canvas.drawLine(left + (right - left) / 3f, top, left + (right - left) / 3f, bottom, linePaint);
        canvas.drawLine(left + (right - left) * 2f / 3f, top, left + (right - left) * 2f / 3f, bottom, linePaint);
        // 两条横线：将画面高度三等分
        canvas.drawLine(left, top + (bottom - top) / 3f, right, top + (bottom - top) / 3f, linePaint);
        canvas.drawLine(left, top + (bottom - top) * 2f / 3f, right, top + (bottom - top) * 2f / 3f, linePaint);
    }
}
