package com.deepsleep.memory.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.core.content.ContextCompat;

import com.deepsleep.memory.R;

/**
 * 加载指示器：三点呼吸。
 *
 * <p>三个主题色圆点依次做"放大 + 提亮"的呼吸脉冲（相位错开），
 * 形成柔和的波浪感。相比文本跳动更克制、更精致，
 * 且与卡片进度点阵的圆点语言一致。</p>
 *
 * <p>尺寸极小（默认 3 × 7dp 点 + 5dp 间距），可直接内嵌在文案行首。</p>
 */
public class LoadingDotsView extends View {

    private static final int DOT_COUNT = 3;
    private static final float DOT_DP = 7f;
    private static final float GAP_DP = 5f;
    /** 单点呼吸周期 */
    private static final long CYCLE_MS = 900;
    /** 相邻点的相位差（周期占比），形成依次呼吸的波浪 */
    private static final float PHASE_STEP = 0.22f;
    /** 呼吸深度：最小/最大缩放与透明度 */
    private static final float MIN_SCALE = 0.72f;
    private static final float MAX_SCALE = 1.08f;
    private static final float MIN_ALPHA = 0.32f;
    private static final float MAX_ALPHA = 1f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dotD;
    private final float gapD;
    private float phase = 0f;
    private ValueAnimator animator;

    public LoadingDotsView(Context context) {
        this(context, null);
    }

    public LoadingDotsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        dotD = DOT_DP * d;
        gapD = GAP_DP * d;
        paint.setColor(ContextCompat.getColor(context, R.color.theme_primary));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    /** 开始呼吸动画（随 View 挂载自动开始） */
    public void start() {
        if (animator != null && animator.isRunning()) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(CYCLE_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = (int) (DOT_COUNT * dotD + (DOT_COUNT - 1) * gapD);
        int desiredHeight = (int) (dotD * MAX_SCALE);
        setMeasuredDimension(desiredWidth, desiredHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cy = getHeight() / 2f;
        float radius = dotD / 2f;
        for (int i = 0; i < DOT_COUNT; i++) {
            // 相位依次错开，形成波浪式呼吸
            float t = (phase + i * PHASE_STEP) % 1f;
            // 平滑呼吸曲线：0 -> 1 -> 0（正弦）
            float breathe = (float) ((Math.sin(t * 2 * Math.PI - Math.PI / 2) + 1) / 2);
            float scale = MIN_SCALE + (MAX_SCALE - MIN_SCALE) * breathe;
            float alpha = MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * breathe;
            paint.setAlpha((int) (alpha * 255));
            float cx = radius + i * (dotD + gapD);
            canvas.drawCircle(cx, cy, radius * scale, paint);
        }
    }
}
