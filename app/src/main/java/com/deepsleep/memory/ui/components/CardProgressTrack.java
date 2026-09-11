package com.deepsleep.memory.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.deepsleep.memory.R;

/**
 * 卡片进度点阵：iOS UIPageControl 风格的抽象化进度指示。
 *
 * <p>每日卡片可能有几十张，不为每张卡生成一个点，而是用固定数量的点
 * （{@link #MAX_POINTS}，放不下时）按<b>比例映射</b>覆盖整批卡片：</p>
 * <ul>
 *   <li>每个点默认为圆形；当前卡所在区间的点拉宽为胶囊——
 *       即从圆形到圆角矩形的形变（宽度动画过渡）；</li>
 *   <li>点色 = 类型色相（由调用方传入），透明度编码区间完成度；
 *       当前点始终饱和，其余按完成度淡化；</li>
 *   <li>快速点击：跳到该点区间首卡；</li>
 *   <li>长按 0.2s 进入拖拽：手指滑到某个点范围内该点拉宽成为当前点，
 *       并<b>实时回调切卡</b>（由容器以既有动效切换），松手结束拖拽。</li>
 * </ul>
 *
 * <p>组件保持纯绘制：每张卡的最终颜色（完成态透明度编码）
 * 由调用方计算传入（{@link #setSegments(int[])}），本组件不感知业务模型。</p>
 */
public class CardProgressTrack extends View {

    /** 拖拽/点击后请求跳到对应卡片（0-based） */
    public interface OnSeekListener {
        void onSeek(int cardIndex);
    }

    /** 点直径 */
    private static final float POINT_DP = 6f;
    /** 点间距 */
    private static final float GAP_DP = 4f;
    /** 激活/拖拽点拉宽后的宽度（胶囊） */
    private static final float ACTIVE_WIDTH_DP = 16f;
    /** 点数上限：超出时按比例映射 */
    private static final int MAX_POINTS = 9;
    private static final float VERTICAL_PADDING_DP = 4f;
    /** 长按触发拖拽的时长 */
    private static final int DRAG_ACTIVATE_MS = 200;
    private static final long MORPH_ANIM_MS = 160;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    /** 每张卡的最终颜色（含完成态透明度），由调用方传入 */
    private int[] cardColors = new int[0];
    private int currentIndex = 0;
    private int pointCount = 0;
    private int activePoint = 0;
    /** 每个点当前渲染宽度（激活点在圆与胶囊之间形变） */
    private float[] pointWidths;
    private float pointD;
    private float gapD;
    private float activeW;
    private float verticalPadding;
    /** 拖拽状态：长按 0.2s 激活，期间实时跟随手指并切卡 */
    private boolean dragging = false;
    private boolean pressActive = false;
    private float lastTouchX = 0f;
    private ValueAnimator morphAnimator;
    private OnSeekListener seekListener;
    private final Runnable dragActivator = this::startDrag;

    public CardProgressTrack(Context context) {
        this(context, null);
    }

    public CardProgressTrack(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        pointD = POINT_DP * d;
        gapD = GAP_DP * d;
        activeW = ACTIVE_WIDTH_DP * d;
        verticalPadding = VERTICAL_PADDING_DP * d;
        paint.setColor(ContextCompat.getColor(getContext(), R.color.light_gray));
    }

    /** 传入每张卡的最终颜色（完成态透明度编码），触发重算与重绘 */
    public void setSegments(int[] colors) {
        cardColors = colors == null ? new int[0] : colors;
        if (currentIndex > cardColors.length - 1) {
            currentIndex = Math.max(cardColors.length - 1, 0);
        }
        rebuildPoints();
        invalidate();
    }

    /** 当前卡切换（外部回流）：激活点形变到新区间 */
    public void setCurrentIndex(int index) {
        currentIndex = clampCard(index);
        int newActive = pointOfCard(currentIndex);
        if (newActive != activePoint) {
            animateMorph(activePoint, newActive);
            activePoint = newActive;
        }
        invalidate();
    }

    public void setOnSeekListener(OnSeekListener listener) {
        seekListener = listener;
    }

    // ==================== 比例映射算法 ====================
    // 采用 i*M/N 比例映射（而非整除均分）：保证单调、均匀、且最后一个点
    // 必然覆盖最后一张卡——整除式在 N 不是 M 的倍数时尾部点将永远无法激活。

    /** 卡片数 → 点数：放不下时固定为 MAX_POINTS */
    private int pointCountFor(int cardCount) {
        return Math.max(1, Math.min(cardCount, MAX_POINTS));
    }

    /** 卡片序号 → 所在点序号（比例映射） */
    private int pointOfCard(int cardIndex) {
        int n = cardColors.length;
        int m = pointCount;
        if (n == 0 || m == 0) {
            return 0;
        }
        return (int) ((long) cardIndex * m / n);
    }

    /** 点序号 → 该区间第一张卡片序号（ceil 互逆：pointOfCard(firstCardOfPoint(p)) == p） */
    private int firstCardOfPoint(int pointIndex) {
        int n = cardColors.length;
        int m = pointCount;
        if (m == 0) {
            return 0;
        }
        return (int) (((long) pointIndex * n + m - 1) / m);
    }

    /** 点 i 的区间颜色：透明度取区间内各卡完成度（alpha）的平均，色相保持不变 */
    private int colorOfPoint(int pointIndex) {
        int from = firstCardOfPoint(pointIndex);
        int to = pointIndex + 1 < pointCount ? firstCardOfPoint(pointIndex + 1) : cardColors.length;
        to = Math.min(Math.max(to, from + 1), cardColors.length);
        if (from >= cardColors.length) {
            from = Math.max(cardColors.length - 1, 0);
        }
        int alphaSum = 0;
        int count = 0;
        for (int i = from; i < to; i++) {
            alphaSum += Color.alpha(cardColors[i]);
            count++;
        }
        if (count == 0) {
            return cardColors[from];
        }
        int avgAlpha = alphaSum / count;
        return Color.argb(avgAlpha, Color.red(cardColors[from]),
                Color.green(cardColors[from]), Color.blue(cardColors[from]));
    }

    private void rebuildPoints() {
        pointCount = pointCountFor(cardColors.length);
        activePoint = pointOfCard(currentIndex);
        float[] widths = new float[pointCount];
        for (int i = 0; i < pointCount; i++) {
            widths[i] = i == activePoint ? activeW : pointD;
        }
        pointWidths = widths;
        requestLayout();
        invalidate();
    }

    // ==================== 形变动画 ====================

    /** 激活点换位：旧点胶囊收缩为圆，新点圆撑开为胶囊 */
    private void animateMorph(int fromPoint, int toPoint) {
        if (fromPoint == toPoint || pointWidths == null
                || fromPoint >= pointWidths.length || toPoint >= pointWidths.length) {
            return;
        }
        if (morphAnimator != null) {
            morphAnimator.cancel();
        }
        final float wide = activeW;
        final float slim = pointD;
        morphAnimator = ValueAnimator.ofFloat(0f, 1f);
        morphAnimator.setDuration(MORPH_ANIM_MS);
        morphAnimator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            pointWidths[fromPoint] = slim + (wide - slim) * (1 - t);
            pointWidths[toPoint] = slim + (wide - slim) * t;
            invalidate();
        });
        morphAnimator.start();
    }

    // ==================== 测量与绘制 ====================

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float d = getResources().getDisplayMetrics().density;
        int desiredHeight = (int) (pointD + verticalPadding * 2);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int resolvedHeight = heightMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.AT_MOST
                ? Math.min(desiredHeight, heightSize == 0 ? desiredHeight : heightSize)
                : heightSize;

        int desiredWidth = (int) (pointCountFor(cardColors.length) * (POINT_DP + GAP_DP) * d - GAP_DP * d);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int resolvedWidth = widthMode == MeasureSpec.UNSPECIFIED ? desiredWidth
                : widthMode == MeasureSpec.AT_MOST ? Math.min(desiredWidth, widthSize) : widthSize;

        setMeasuredDimension(resolvedWidth, resolvedHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (pointWidths == null || pointWidths.length == 0) {
            return;
        }
        float cy = getHeight() / 2f;
        float radius = pointD / 2f;
        float x = 0;
        for (int i = 0; i < pointWidths.length; i++) {
            float w = pointWidths[i];
            rect.set(x, cy - radius, x + w, cy + radius);
            paint.setColor(i == activePoint ? saturatedColor(i) : colorOfPoint(i));
            canvas.drawRoundRect(rect, radius, radius, paint);
            x += w + gapD;
        }
    }

    /** 当前点始终用饱和色（透明度编码的是区间完成度，当前表达的是"所在位置"） */
    private int saturatedColor(int pointIndex) {
        int c = colorOfPoint(pointIndex);
        return Color.argb(255, Color.red(c), Color.green(c), Color.blue(c));
    }

    // ==================== 交互：快速点击 + 长按拖拽 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (cardColors.length == 0) {
            return false;
        }
        float x = Math.min(Math.max(event.getX(), 0), getWidth());
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressActive = true;
                dragging = false;
                lastTouchX = x;
                // 长按 0.2s 激活拖拽
                postDelayed(dragActivator, DRAG_ACTIVATE_MS);
                return true;
            case MotionEvent.ACTION_MOVE:
                lastTouchX = x;
                if (dragging) {
                    handleDrag(x);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(dragActivator);
                pressActive = false;
                if (dragging) {
                    // 拖拽结束：状态在 handleDrag 中已实时同步
                    dragging = false;
                    performClick();
                    return true;
                }
                // 快速点击：直接跳到该点区间首卡
                seekToPoint(pointAt(x));
                performClick();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /** 长按到达 0.2s：进入拖拽模式，当前点放大并立即响应手指位置 */
    private void startDrag() {
        if (!pressActive || dragging) {
            return;
        }
        dragging = true;
        getParent().requestDisallowInterceptTouchEvent(true);
        handleDrag(lastTouchX);
    }

    /**
     * 拖拽跟随：手指落在哪个点的范围内，该点拉宽成为当前点，
     * 并实时回调切卡（容器以既有的滑出+渐显动效切换）。
     */
    private void handleDrag(float x) {
        int point = pointAt(x);
        if (point != activePoint) {
            animateMorph(activePoint, point);
            activePoint = point;
        }
        int cardIndex = firstCardOfPoint(point);
        if (cardIndex != currentIndex && seekListener != null) {
            currentIndex = cardIndex;
            seekListener.onSeek(cardIndex);
        }
    }

    /** 快速点击：形变到目标点并跳卡 */
    private void seekToPoint(int point) {
        if (point != activePoint) {
            animateMorph(activePoint, point);
            activePoint = point;
        }
        int cardIndex = firstCardOfPoint(point);
        if (cardIndex != currentIndex && seekListener != null) {
            currentIndex = cardIndex;
            seekListener.onSeek(cardIndex);
        }
    }
}
