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

/**
 * 卡片进度点阵：线性爬升的拨盘点阵。
 *
 * <p>当前点的槽位随卡片进度<b>从最左端线性爬升到最右端</b>：
 * 首卡在最左端，随切换逐渐向中间迁移，接近最后一张卡时继续向右迁移、
 * 直到最右端；其余点在它两侧等距排布、随视口滚动进出，
 * 视口两端以透明度衰减表示边界。整体宽度恒定、点距恒定，
 * 拉宽以槽位中心对称展开，绝不推动相邻点。</p>
 *
 * <p>交互：快速点击跳到该槽对应卡片；长按 0.2s 进入拖拽，
 * 手指扫过槽位实时切卡（容器以既有动效切换）。</p>
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
    /** 当前点撑开后的宽度（胶囊，以槽位中心对称展开） */
    private static final float ACTIVE_WIDTH_DP = 16f;
    /** 视口槽位数 */
    private static final int MAX_SLOTS = 9;
    private static final float VERTICAL_PADDING_DP = 2f;
    /** 长按触发拖拽的时长 */
    private static final int DRAG_ACTIVATE_MS = 200;
    /** 拨盘平移 + 形变动画时长 */
    private static final long SLIDE_ANIM_MS = 200;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final float pointD;
    private final float gapD;
    private final float activeW;
    private final float verticalPadding;

    /** 每张卡的最终颜色（含完成态透明度），由调用方传入 */
    private int[] cardColors = new int[0];
    private int currentIndex = 0;
    /** 拨盘平移偏移（px，动画中从 ∓槽宽 过渡到 0） */
    private float slideOffset = 0f;
    /** 当前槽宽度形变进度（0=圆，1=胶囊），与平移动画同步 */
    private float morphT = 1f;
    private boolean dragging = false;
    private boolean pressActive = false;
    private float lastTouchX = 0f;
    private ValueAnimator slideAnimator;
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
        // elevation 是全局 Z 轴：练习卡带 2dp elevation（堆叠预览会压住本组件），
        // 本组件必须高于卡片才能正常可见
        setElevation(4f * d);
    }

    /** 传入每张卡的最终颜色（完成态透明度编码），触发重绘 */
    public void setSegments(int[] colors) {
        // 同批次数据不重置动画状态：切卡回调（onCurrentCardChanged）会高频触发
        // 刷新，若在此重置 slideOffset/morphT，拨盘的平移动画会被打断
        if (colors != null && colors.length == cardColors.length
                && java.util.Arrays.equals(colors, cardColors)) {
            invalidate();
            return;
        }
        cardColors = colors == null ? new int[0] : colors;
        if (currentIndex > cardColors.length - 1) {
            currentIndex = Math.max(cardColors.length - 1, 0);
        }
        slideOffset = 0f;
        morphT = 1f;
        invalidate();
    }

    /**
     * 当前卡切换：整条点阵向切向平移一格（拨盘滚动），
     * 当前点在平移中从圆形撑开为胶囊。
     */
    public void setCurrentIndex(int index) {
        int clamped = clampCard(index);
        if (clamped == currentIndex) {
            invalidate();
            return;
        }
        int dir = (int) Math.signum(clamped - currentIndex);
        currentIndex = clamped;
        startSlide(dir);
    }

    public void setOnSeekListener(OnSeekListener listener) {
        seekListener = listener;
    }

    private void startSlide(int dir) {
        if (slideAnimator != null) {
            slideAnimator.cancel();
        }
        float unit = pointD + gapD;
        slideAnimator = ValueAnimator.ofFloat(0f, 1f);
        slideAnimator.setDuration(SLIDE_ANIM_MS);
        slideAnimator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            slideOffset = -dir * unit * (1 - t);
            morphT = t;
            invalidate();
        });
        slideAnimator.start();
    }

    // ==================== 线性爬升的槽位映射 ====================

    /** 当前卡所在槽位：随卡片进度从槽 0（最左端）线性爬升到槽 MAX_SLOTS-1（最右端） */
    private int activeSlot() {
        int n = cardColors.length;
        if (n <= 1) {
            return 0;
        }
        return (int) ((long) currentIndex * (MAX_SLOTS - 1) / (n - 1));
    }

    /** 视口起点卡片：使当前卡恰好落在其爬升槽位上 */
    private int startCard() {
        return currentIndex - activeSlot();
    }

    /** 槽位 → 卡片（越界返回 -1，调用方跳过该槽） */
    private int cardAtSlot(int slot) {
        int card = startCard() + slot;
        return card >= 0 && card < cardColors.length ? card : -1;
    }

    // ==================== 绘制 ====================

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float d = getResources().getDisplayMetrics().density;
        int desiredHeight = (int) (pointD + verticalPadding * 2);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int resolvedHeight = heightMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.AT_MOST
                ? Math.min(desiredHeight, heightSize == 0 ? desiredHeight : heightSize)
                : heightSize;

        // 宽度需容纳首末槽激活胶囊的对称溢出，否则边缘的胶囊会被裁切
        int desiredWidth = (int) ((ACTIVE_WIDTH_DP - POINT_DP) * d
                + (MAX_SLOTS - 1) * (POINT_DP + GAP_DP) * d + ACTIVE_WIDTH_DP * d);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int resolvedWidth = widthMode == MeasureSpec.UNSPECIFIED ? desiredWidth
                : widthMode == MeasureSpec.AT_MOST ? Math.min(desiredWidth, widthSize) : widthSize;

        setMeasuredDimension(resolvedWidth, resolvedHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cardColors.length == 0) {
            return;
        }
        int act = activeSlot();
        int start = startCard();
        float unit = pointD + gapD;
        float cy = getHeight() / 2f;
        float radius = pointD / 2f;

        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            int card = start + slot;
            if (card < 0 || card >= cardColors.length) {
                continue;
            }
            boolean active = card == currentIndex;
            // 拉宽以槽位中心对称展开：槽距恒定、整体宽度恒定、不推动相邻点
            float w = active ? pointD + (activeW - pointD) * morphT : pointD;

            // 透明度：完成度（已学饱和 / 未学 30% / 当前点加重）× 距离衰减 × 端点衰减。
            // 注意 alpha 必须是 0-255 尺度，误用 0-1 小数会被 (int) 截断为 0（全透明）
            int dist = Math.abs(slot - act);
            float distAlpha;
            switch (dist) {
                case 0:  distAlpha = 1f;    break;
                case 1:  distAlpha = 0.82f; break;
                case 2:  distAlpha = 0.68f; break;
                default: distAlpha = 0.55f; break;
            }
            if (slot == 0) {
                distAlpha *= 0.75f;         // 左缘边界衰减
            } else if (slot == MAX_SLOTS - 1) {
                distAlpha *= 0.8f;          // 右缘边界衰减
            }
            int base = cardColors[card];
            float stateAlpha = Color.alpha(base) >= 128 ? 255f
                    : (active ? 220f : 90f);
            float finalAlpha = stateAlpha * distAlpha;      // 0-255 尺度
            paint.setColor(Color.argb((int) finalAlpha, Color.red(base), Color.green(base), Color.blue(base)));

            float cx = (activeW - pointD) / 2f + slot * unit + unit / 2f + slideOffset;
            rect.set(cx - w / 2f, cy - radius, cx + w / 2f, cy + radius);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
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
                postDelayed(dragActivator, DRAG_ACTIVATE_MS);
                return true;
            case MotionEvent.ACTION_MOVE:
                lastTouchX = x;
                if (dragging) {
                    int cardIndex = cardAtSlot(slotAt(x));
                    if (cardIndex >= 0 && cardIndex != currentIndex) {
                        setCurrentIndex(cardIndex);
                    }
                    if (cardIndex >= 0 && seekListener != null) {
                        seekListener.onSeek(cardIndex);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(dragActivator);
                pressActive = false;
                if (dragging) {
                    dragging = false;
                    performClick();
                    return true;
                }
                int cardIndex = cardAtSlot(slotAt(x));
                if (cardIndex >= 0 && cardIndex != currentIndex) {
                    setCurrentIndex(cardIndex);
                }
                if (cardIndex >= 0 && seekListener != null) {
                    seekListener.onSeek(cardIndex);
                }
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

    /** 长按到达 0.2s：进入拖拽模式 */
    private void startDrag() {
        if (!pressActive || dragging) {
            return;
        }
        dragging = true;
        getParent().requestDisallowInterceptTouchEvent(true);
        lastTouchX = Math.min(Math.max(lastTouchX, 0), getWidth());
        int cardIndex = cardAtSlot(slotAt(lastTouchX));
        if (cardIndex >= 0 && cardIndex != currentIndex) {
            setCurrentIndex(cardIndex);
        }
        if (cardIndex >= 0 && seekListener != null) {
            seekListener.onSeek(cardIndex);
        }
    }

    private int slotAt(float x) {
        float unit = pointD + gapD;
        float pad = (activeW - pointD) / 2f;
        return (int) Math.min(MAX_SLOTS - 1, Math.max(0, (x - pad) / unit));
    }

    private int clampCard(int index) {
        int max = Math.max(cardColors.length - 1, 0);
        return Math.min(Math.max(index, 0), max);
    }
}
