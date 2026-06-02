package com.deepsleep.memory.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import com.deepsleep.memory.R;

public class CustomCircleButton extends androidx.appcompat.widget.AppCompatButton {
    private Paint backgroundPaint;
    private Paint wavePaint;
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private Rect textBounds = new Rect();
    private Paint textPaint;
    private int buttonColor;
    private int textColor;
    private ValueAnimator waveAnimator;

    public CustomCircleButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 初始化画笔
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // 设置默认颜色
        buttonColor = getResources().getColor(R.color.theme_color );
        textColor = Color.WHITE;
        backgroundPaint.setColor(buttonColor);
        textPaint.setColor(textColor);

        // 移除默认背景
        setBackground(null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        int radius = Math.min(width, height) / 2;
        int centerX = width / 2;
        int centerY = height / 2;

        // 绘制圆形背景
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint);


        // 绘制文字或图标
        String text = getText().toString();
        if (!text.isEmpty()) {
            textPaint.setTextSize(getTextSize());
            textPaint.getTextBounds(text, 0, text.length(), textBounds);
            float textY = centerY - (textBounds.top + textBounds.bottom) / 2;
            canvas.drawText(text, centerX, textY, textPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                backgroundPaint.setColor(darkenColor(buttonColor)); // 按下时加深颜色
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                backgroundPaint.setColor(buttonColor); // 恢复原色
                invalidate();
                break;
        }
        return super.onTouchEvent(event);
    }

    private int darkenColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.8f; // 减少亮度
        return Color.HSVToColor(hsv);
    }

    // 设置按钮颜色
    public void setButtonColor(int color) {
        buttonColor = color;
        backgroundPaint.setColor(buttonColor);
        invalidate();
    }

    // 设置文字颜色
    public void setTextColor(int color) {
        textColor = color;
        textPaint.setColor(textColor);
        invalidate();
    }
}
