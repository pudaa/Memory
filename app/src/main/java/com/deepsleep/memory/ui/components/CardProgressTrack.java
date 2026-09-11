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
 * （{@link #MAX_POINTS}，放不下时）按区间均分映射整批卡片：</p>
 * <ul>
 *   <li>每个点默认为圆形；当前卡所在区间的点拉宽为胶囊——
 *       即从圆形到圆角矩形的形变（宽度动画过渡）；</li>
 *   <li>点色 = 区间主导类型色（复习蓝 / 新学橙），透明度编码区间完成度
 *       （全完成饱和 / 进行中半透 / 未开始更淡）；</li>
 *   <li>点击点跳到该区间第一张卡片。</li>
 * </ul>
 *
 * <p>组件保持纯绘制：每张卡的最终颜色（类型色相 + 完成态透明度）
 * 由调用方计算传入（{@link #setSegments(int[])}），本组件不感知业务模型。</p>
 */
public class CardProgressTrack extends View {

    /** 用户点击第 pointIndex 个点后，请求跳到对应区间的第一张卡片（0-based 卡片序号） */
    public interface OnSeekListener {
        void onSeek(int cardIndex);
    }

    /** 点直径 */
    private static final float POINT_DP = 6f;
    /** 点间距 */
    private static final float GAP_DP = 4f;
    /** 激活点拉宽后的宽度（胶囊） */
    private static final float ACTIVE_WIDTH_DP = 16f;
    /** 点数上限：超出时按区间均分映射 */
    private static final int MAX_POINTS = 9;
    private static final float VERTICAL_PADDING_DP = 4f;
    private static final long MORPH_ANIM_MS = 200;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    /** 每张卡的最终颜色（含完成态透明度），由调用方传入 */
    private int[] cardColors = new int[0];
    private int currentIndex = 0;
    private int pointCount = 0;
    private int cardsPerPoint = 1;
    private int activePoint = 0;
    /** 每个点当前渲染宽度（激活点在圆与胶囊之间形变） */
    private float[] pointWidths;
    private float pointD;
    private float gapD;
    private float activeW;
    private float verticalPadding;
    private ValueAnimator morphAnimator;
    private OnSeekListener seekListener;

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

    /** 传入每张卡的最终颜色（类型色相 + 完成态透明度），触发重算与重绘 */
    public void setSegments(int[] colors) {
        cardColors = colors == null ? new int[0] : colors;
        if (currentIndex > cardColors.length - 1) {
            currentIndex = Math.max(cardColors.length - 1, 0);
        }
        rebuildPoints();
        invalidate();
    }

    /** 当前卡切换：激活点形变到新区间 */
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

    // ==================== 区间映射算法 ====================

    /** 卡片数 → 点数：放不下时均分映射，点数固定上限 */
    private int pointCountFor(int cardCount) {
        return Math.max(1, Math.min(cardCount, MAX_POINTS));
    }

    /** 每个点代表的卡片数（向上取整，保证区间覆盖全部卡片） */
    private int cardsPerPoint() {
        int n = cardColors.length;
        if (n == 0) {
            return 1;
        }
        int m = pointCountFor(n);
        return (int) Math.ceil((double) n / m);
    }

    /** 卡片序号 → 所在点序号 */
    private int pointOfCard(int cardIndex) {
        return Math.min(cardIndex / cardsPerPoint(), pointCount - 1);
    }

    /** 点序号 → 该区间第一张卡片序号 */
    private int firstCardOfPoint(int pointIndex) {
        return Math.min(pointIndex * cardsPerPoint(), Math.max(cardColors.length - 1, 0));
    }

    /** 点 i 的区间颜色：色相取区间首卡，透明度取区间内完成度（alpha）的平均 */
    private int colorOfPoint(int pointIndex) {
        int from = pointIndex * cardsPerPoint();
        int to = Math.min(from + cardsPerPoint(), cardColors.length);
        if (from >= cardColors.length) {
            from = Math.max(cardColors.length - 1, 0);
        }
        if (to <= from) {
            return cardColors[from];
        }
        int first = cardColors[from];
        int alphaSum = 0;
        for (int i = from; i < to; i++) {
            alphaSum += Color.alpha(cardColors[i]);
        }
        int avgAlpha = alphaSum / (to - from);
        return Color.argb(avgAlpha, Color.red(first), Color.green(first), Color.blue(first));
    }

    private void rebuildPoints() {
        pointCount = pointCountFor(cardColors.length);
        cardsPerPoint = cardsPerPoint();
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
        if (morphAnimator != null) {
            morphAnimator.cancel();
        }
        final float wide = activeW;
        final float slim = pointD;
        morphAnimator = ValueAnimator.ofFloat(0f, 1f);
        morphAnimator.setDuration(MORPH_ANIM_MS);
        morphAnimator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            if (pointWidths != null && fromPoint < pointWidths.length && toPoint < pointWidths.length) {
                pointWidths[fromPoint] = slim + (wide - slim) * (1 - t);
                pointWidths[toPoint] = slim + (wide - slim) * t;
            }
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

    /** 激活点始终用饱和色（透明度编码的是区间完成度，激活表达的是"所在位置"） */
    private int saturatedColor(int pointIndex) {
        int c = colorOfPoint(pointIndex);
        return Color.argb(255, Color.red(c), Color.green(c), Color.blue(c));
    }

    // ==================== 交互 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float x = Math.min(Math.max(event.getX(), 0), getWidth());
            int point = pointAt(x);
            int cardIndex = firstCardOfPoint(point);
            int previous = currentIndex;
            currentIndex = cardIndex;
            int newActive = pointOfCard(cardIndex);
            if (newActive != activePoint) {
                animateMorph(activePoint, newActive);
                activePoint = newActive;
            }
            if (seekListener != null && cardIndex != previous) {
                seekListener.onSeek(cardIndex);
            }
            performClick();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private int pointAt(float x) {
        float unit = pointD + gapD;
        return (int) Math.min(pointCount - 1, Math.max(0, x / unit));
    }

    private int clampCard(int index) {
        int max = Math.max(cardColors.length - 1, 0);
        return Math.min(Math.max(index, 0), max);
    }
}
