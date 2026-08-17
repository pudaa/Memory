package com.deepsleep.memory.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * 横向滚轮式微调控件（参考 iOS 相机裁剪页的缩放滚轮设计）：
 * <ul>
 * <li>一排竖向刻度线：普通刻度暗色、特殊刻度（{@link #setSpecialValue} 对应位置）亮色且更长；</li>
 * <li>横向正中间固定一条较长亮色指针线，表示当前值；</li>
 * <li>极简交互：横向滑动平移刻度（值随之变化），松手后吸附到最近的步进值（曲线动画），
 * 滑动经过特殊值时轻微震动提示。</li>
 * </ul>
 */
public class WheelSliderView extends View {

    public interface OnValueChangeListener {
        void onValueChanged(float value);

        void onValueChangeEnd(float value);
    }

    private static final int TICK_NORMAL = 0x59FFFFFF; // 普通刻度（35% 白，清晰可辨）
    private static final int TICK_SPECIAL = 0xFFFFFFFF; // 特殊刻度（纯白，更长）
    private static final int POINTER = 0xFFFFFFFF;

    private float minValue = 0f;
    private float maxValue = 100f;
    private float valueStep = 1f;
    private float tickStep = 10f;
    private float value = 0f;
    private float specialValue = 0f;
    private OnValueChangeListener listener;

    private final Paint tickNormalPaint;
    private final Paint tickSpecialPaint;
    private final Paint pointerPaint;

    /** 每 tickStep 对应的像素间距（刻度线密度恒定，不随值范围变化） */
    private float pxPerUnit = dp(14f) / 5f;

    private boolean touching = false;
    private float lastX = 0f;
    private boolean vibratedAtSpecial = false;

    public WheelSliderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        tickNormalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickNormalPaint.setColor(TICK_NORMAL);
        tickNormalPaint.setStrokeWidth(dp(1f));
        tickSpecialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickSpecialPaint.setColor(TICK_SPECIAL);
        tickSpecialPaint.setStrokeWidth(dp(2f));
        pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointerPaint.setColor(POINTER);
        pointerPaint.setStrokeWidth(dp(2.5f));
        setWillNotDraw(false);
    }

    public void setRange(float min, float max) {
        this.minValue = min;
        this.maxValue = max;
        invalidate();
    }

    /** 数值步进（松手吸附的最小粒度）与刻度线步进（显示密度，应为 valueStep 的倍数） */
    public void setSteps(float valueStep, float tickStep) {
        this.valueStep = valueStep;
        this.tickStep = tickStep;
        // 刻度间距恒定（每 tickStep 14dp），值单位像素随之换算
        this.pxPerUnit = dp(14f) / tickStep;
        invalidate();
    }

    public void setValue(float v) {
        this.value = clamp(v);
        invalidate();
    }

    public float getValue() {
        return value;
    }

    /** 特殊值（该位置的刻度亮色；滑动经过时震动，松手可吸附回正） */
    public void setSpecialValue(float special) {
        this.specialValue = special;
        invalidate();
    }

    public void setOnValueChangeListener(OnValueChangeListener l) {
        this.listener = l;
    }

    private float clamp(float v) {
        return Math.max(minValue, Math.min(maxValue, v));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private void vibrate() {
        try {
            Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator())
                return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(12);
            }
        } catch (Exception e) {
            // 部分设备/ROM 的 VibratorService 会抛 RemoteException，震动失败静默忽略
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float centerX = w / 2f;
        float centerY = h / 2f;

        // 绘制刻度线（覆盖可视范围）
        float range = (w / 2f) / pxPerUnit; // 可视的半宽（值单位）
        float start = value - range;
        float end = value + range;
        for (float tick = (float) Math.floor(start / tickStep) * tickStep; tick <= end; tick += tickStep) {
            float x = centerX + (tick - value) * pxPerUnit;
            boolean special = isNear(tick, specialValue);
            float halfLen = special ? dp(9f) : dp(5f);
            canvas.drawLine(x, centerY - halfLen, x, centerY + halfLen, special ? tickSpecialPaint : tickNormalPaint);
        }

        // 中央指针（固定较长亮色线）
        canvas.drawLine(centerX, centerY - dp(13f), centerX, centerY + dp(13f), pointerPaint);
    }

    private boolean isNear(float a, float b) {
        return Math.abs(a - b) < tickStep * 0.5f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            touching = true;
            lastX = event.getX();
            vibratedAtSpecial = false;
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        case MotionEvent.ACTION_MOVE:
            float dx = event.getX() - lastX;
            lastX = event.getX();
            setValue(value - dx / pxPerUnit);
            // 滑动经过特殊值：轻微震动提示
            if (!vibratedAtSpecial && Math.abs(value - specialValue) < valueStep) {
                vibratedAtSpecial = true;
                vibrate();
            }
            if (listener != null) {
                listener.onValueChanged(value);
            }
            return true;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
            touching = false;
            getParent().requestDisallowInterceptTouchEvent(false);
            snapToStep();
            return true;
        }
        return super.onTouchEvent(event);
    }

    /** 松手后吸附到最近步进值（曲线动画过渡，保证自然） */
    private void snapToStep() {
        float snapped = Math.round(value / valueStep) * valueStep;
        snapped = clamp(snapped);
        animateValueTo(snapped);
    }

    private void animateValueTo(float target) {
        final float from = value;
        final float to = target;
        final long duration = 180;
        final long start = System.currentTimeMillis();
        DecelerateInterpolator interpolator = new DecelerateInterpolator(1.5f);
        postOnAnimation(new Runnable() {
            @Override
            public void run() {
                float t = Math.min(1f, (System.currentTimeMillis() - start) / (float) duration);
                setValue(from + (to - from) * interpolator.getInterpolation(t));
                if (listener != null) {
                    listener.onValueChanged(value);
                }
                if (t < 1f) {
                    postOnAnimation(this);
                } else {
                    if (listener != null) {
                        listener.onValueChangeEnd(value);
                    }
                }
            }
        });
    }
}
