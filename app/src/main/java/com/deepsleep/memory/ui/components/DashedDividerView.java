package com.deepsleep.memory.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.deepsleep.memory.R;

/**
 * 虚线分割线（Canvas 绘制，兼容所有 API 级别，
 * 避免 android:shape="line" 作为 View 背景时线条被裁剪/不渲染的问题）
 */
public class DashedDividerView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float dashWidth;
    private final float dashGap;

    public DashedDividerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dashWidth = dp(5);
        dashGap = dp(4);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(ContextCompat.getColor(context, R.color.theme_primary));
        setBackground(null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float midY = getHeight() / 2f;
        path.reset();
        path.moveTo(0, midY);
        path.lineTo(getWidth(), midY);
        paint.setPathEffect(new DashPathEffect(new float[] { dashWidth, dashGap }, 0));
        canvas.drawPath(path, paint);
    }

    private float dp(float value) {
        return getResources().getDisplayMetrics().density * value;
    }
}
