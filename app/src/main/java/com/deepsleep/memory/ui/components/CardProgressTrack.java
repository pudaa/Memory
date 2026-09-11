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
 * 卡片进度轨道：连续分段色轨 + 加宽胶囊游标。
 *
 * <p>设计借鉴 iOS 分页控制与 Material Expressive slider 的抽象化思路：
 * 每日卡片可能有几十张，不为每张卡片生成一个独立圆点，而是把整条轨道
 * 按卡片数等分为连续段——色相区分复习（蓝）/新学（橙），透明度区分
 * 未完成/已完成；当前卡片以加宽的胶囊游标呈现，内显序号，切换时平滑滑动；
 * 点击或拖动轨道可快速跳到任意卡片（吸附最近段）。</p>
 *
 * <p>组件保持纯绘制：每段的最终颜色（含完成态的透明度编码）由调用方计算后传入，
 * 本组件不感知业务模型。</p>
 */
public class CardProgressTrack extends View {

    /** 用户松手后请求跳转到第 index 张卡片（0-based） */
    public interface OnSeekListener {
        void onSeek(int index);
    }

    private static final float TRACK_HEIGHT_DP = 6f;
    private static final float CURSOR_HEIGHT_DP = 20f;
    private static final float CURSOR_MIN_WIDTH_DP = 26f;
    private static final float CURSOR_CORNER_DP = 10f;
    private static final float VERTICAL_PADDING_DP = 8f;
    private static final long CURSOR_ANIM_MS = 180;
    private static final int TEXT_SIZE_SP = 10;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint segmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int[] segmentColors = new int[0];
    private int currentIndex = 0;
    private float cursorCenterX;
    private float trackHeight;
    private float cursorHeight;
    private float cursorMinWidth;
    private float cursorCorner;
    private float verticalPadding;
    private boolean cursorPositionReady = false;
    private ValueAnimator cursorAnimator;
    private OnSeekListener seekListener;

    public CardProgressTrack(Context context) {
        this(context, null);
    }

    public CardProgressTrack(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        trackHeight = TRACK_HEIGHT_DP * d;
        cursorHeight = CURSOR_HEIGHT_DP * d;
        cursorMinWidth = CURSOR_MIN_WIDTH_DP * d;
        cursorCorner = CURSOR_CORNER_DP * d;
        verticalPadding = VERTICAL_PADDING_DP * d;

        trackPaint.setColor(ContextCompat.getColor(getContext(), R.color.light_gray));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(TEXT_SIZE_SP * d);
        textPaint.setFakeBoldText(true);
    }

    /** 传入每段最终颜色（调用方完成类型/完成态的透明度编码），触发重绘 */
    public void setSegments(int[] colors) {
        segmentColors = colors == null ? new int[0] : colors;
        if (currentIndex > segmentColors.length - 1) {
            currentIndex = Math.max(segmentColors.length - 1, 0);
        }
        if (!cursorPositionReady && getWidth() > 0) {
            cursorCenterX = centerOf(currentIndex);
            cursorPositionReady = true;
        }
        invalidate();
    }

    /** 游标平滑滑动到指定卡片段 */
    public void setCurrentIndex(int index) {
        int clamped = clampIndex(index);
        if (clamped == currentIndex && cursorPositionReady) {
            return;
        }
        currentIndex = clamped;
        animateCursorTo(centerOf(currentIndex));
    }

    public void setOnSeekListener(OnSeekListener listener) {
        seekListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = (int) (cursorHeight + verticalPadding * 2);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int resolvedHeight = heightMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.AT_MOST
                ? Math.min(desiredHeight, heightSize == 0 ? desiredHeight : heightSize)
                : heightSize;
        super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(resolvedHeight, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (!cursorPositionReady && w > 0) {
            cursorCenterX = centerOf(currentIndex);
            cursorPositionReady = true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float cy = getHeight() / 2f;
        float halfTrack = trackHeight / 2f;

        // 1) 底轨
        rect.set(0, cy - halfTrack, w, cy + halfTrack);
        canvas.drawRoundRect(rect, halfTrack, halfTrack, trackPaint);

        // 2) 分段填充（无缝衔接；首末段跟随轨道圆角收边，避免方角突出）
        int n = segmentColors.length;
        if (n > 0) {
            float segW = w / n;
            for (int i = 0; i < n; i++) {
                segmentPaint.setColor(segmentColors[i]);
                float left = i * segW;
                float right = Math.min(left + segW, w);
                if (i == 0) {
                    rect.set(0, cy - halfTrack, right, cy + halfTrack);
                    canvas.drawRoundRect(rect, halfTrack, halfTrack, segmentPaint);
                    if (right < w) {
                        rect.set(right - halfTrack, cy - halfTrack, right, cy + halfTrack);
                        canvas.drawRect(rect, segmentPaint);
                    }
                } else if (i == n - 1 && left < w) {
                    rect.set(left, cy - halfTrack, w, cy + halfTrack);
                    canvas.drawRoundRect(rect, halfTrack, halfTrack, segmentPaint);
                    if (left > 0) {
                        rect.set(left, cy - halfTrack, left + halfTrack, cy + halfTrack);
                        canvas.drawRect(rect, segmentPaint);
                    }
                } else {
                    rect.set(left, cy - halfTrack, right, cy + halfTrack);
                    canvas.drawRect(rect, segmentPaint);
                }
            }
        }

        // 3) 当前卡游标：加宽胶囊 + 序号。游标始终取饱和色（段的透明度编码的是完成态，
        //    游标表达的是"所在位置"，不受该卡是否已完成影响）
        String label = n == 0 ? "" : String.valueOf(currentIndex + 1);
        float textW = textPaint.measureText(label);
        float cursorW = Math.max(cursorMinWidth, textW + 14 * density());
        float left = Math.min(Math.max(cursorCenterX - cursorW / 2f, 0), Math.max(w - cursorW, 0));
        rect.set(left, cy - cursorHeight / 2f, left + cursorW, cy + cursorHeight / 2f);
        cursorPaint.setColor(cursorColor());
        canvas.drawRoundRect(rect, cursorCorner, cursorCorner, cursorPaint);
        canvas.drawText(label, rect.centerX(), cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);
    }

    private int cursorColor() {
        if (currentIndex >= 0 && currentIndex < segmentColors.length) {
            int c = segmentColors[currentIndex];
            if (Color.alpha(c) < 255) {
                c = Color.argb(255, Color.red(c), Color.green(c), Color.blue(c));
            }
            return c;
        }
        return ContextCompat.getColor(getContext(), R.color.theme_stress);
    }

    private void animateCursorTo(float target) {
        if (cursorAnimator != null) {
            cursorAnimator.cancel();
        }
        cursorAnimator = ValueAnimator.ofFloat(cursorCenterX, target);
        cursorAnimator.setDuration(CURSOR_ANIM_MS);
        cursorAnimator.addUpdateListener(animation -> {
            cursorCenterX = (float) animation.getAnimatedValue();
            invalidate();
        });
        cursorAnimator.start();
    }

    private float centerOf(int index) {
        int n = Math.max(segmentColors.length, 1);
        float w = Math.max(getWidth(), 1);
        return (index + 0.5f) * w / n;
    }

    private int indexAt(float x) {
        int n = Math.max(segmentColors.length, 1);
        float w = Math.max(getWidth(), 1);
        return (int) Math.min(n - 1, Math.max(0, x / w * n));
    }

    private int clampIndex(int index) {
        int max = Math.max(segmentColors.length - 1, 0);
        return Math.min(Math.max(index, 0), max);
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (segmentColors.length == 0) {
            return false;
        }
        float x = Math.min(Math.max(event.getX(), 0), getWidth());
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                // 拖动预览：游标实时跟手（父容器可能拦截滑动，需申请不拦截）
                getParent().requestDisallowInterceptTouchEvent(true);
                cursorCenterX = x;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                int index = indexAt(x);
                animateCursorTo(centerOf(index));
                int previous = currentIndex;
                currentIndex = index;
                if (seekListener != null && index != previous) {
                    seekListener.onSeek(index);
                }
                performClick();
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
