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
 * 卡片进度点阵：横向拨盘 + 渐隐渐显。
 *
 * <p>每日卡片可能有几十张，不为每张卡生成一个点，而是用固定数量的点
 * （{@link #MAX_SLOTS} 个槽位）按比例映射覆盖整批卡片。核心视觉约定：</p>
 * <ul>
 *   <li><b>点永远等距均匀排布，整体宽度恒定</b>——当前点拉宽为胶囊时
 *       以槽位中心对称展开（覆盖自身两侧间隙），绝不推动相邻点；</li>
 *   <li>切换卡片时整条点阵向切向平移一格，旧视口整体渐隐滑出、
 *       新视口渐显滑入（交叉淡化），模拟拨盘滚动；</li>
 *   <li>点透明度按与当前槽的距离衰减，视口两端额外衰减表示边界。</li>
 * </ul>
 *
 * <p>交互：快速点击跳到该槽对应卡片；长按 0.2s 进入拖拽，
 * 手指扫过槽位实时切卡（容器以既有动效切换）。触摸热区由外部
 * TouchDelegate 扩大，本组件自身保持极小纵向占用。</p>
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
    /** 拨盘平移 + 交叉淡化动画时长 */
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
    /** 交叉淡化动画的旧视口起始卡（-1 表示当前无动画） */
    private int oldIndex = -1;
    /** 动画方向：+1 前进（内容自右滑入），-1 后退 */
    private int slideDir = 0;
    /** 动画进度 0→1 */
    private float slideT = 1f;
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
    }

    /** 传入每张卡的最终颜色（完成态透明度编码），触发重绘 */
    public void setSegments(int[] colors) {
        cardColors = colors == null ? new int[0] : colors;
        if (currentIndex > cardColors.length - 1) {
            currentIndex = Math.max(cardColors.length - 1, 0);
        }
        oldIndex = -1;
        slideT = 1f;
        invalidate();
    }

    /**
     * 当前卡切换：旧视口向反向滑出并渐隐，新视口自切向滑入并渐显（拨盘滚动）。
     */
    public void setCurrentIndex(int index) {
        int clamped = clampCard(index);
        if (clamped == currentIndex) {
            invalidate();
            return;
        }
        oldIndex = currentIndex;
        slideDir = (int) Math.signum(clamped - oldIndex);
        currentIndex = clamped;
        startSlide();
    }

    public void setOnSeekListener(OnSeekListener listener) {
        seekListener = listener;
    }

    private void startSlide() {
        if (slideAnimator != null) {
            slideAnimator.cancel();
        }
        slideAnimator = ValueAnimator.ofFloat(0f, 1f);
        slideAnimator.setDuration(SLIDE_ANIM_MS);
        slideAnimator.addUpdateListener(animation -> {
            slideT = (float) animation.getAnimatedValue();
            invalidate();
        });
        slideAnimator.start();
    }

    // ==================== 槽位映射 ====================

    /** 当前卡所在的槽位（首卡在槽 0，其余固定槽 1，左侧留一槽展示上一张） */
    private int slotOfCard(int cardIndex) {
        return Math.min(1, cardIndex);
    }

    /** 槽位 → 卡片（当前卡槽向两侧展开，越界由 clampCard 收敛） */
    private int cardOfSlot(int slot) {
        int card = currentIndex + (slot - slotOfCard(currentIndex));
        return clampCard(card);
    }

    private int clampCard(int index) {
        int max = Math.max(cardColors.length - 1, 0);
        return Math.min(Math.max(index, 0), max);
    }

    private int slotAt(float x) {
        float unit = pointD + gapD;
        return (int) Math.min(MAX_SLOTS - 1, Math.max(0, x / unit));
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

        int desiredWidth = (int) (MAX_SLOTS * (POINT_DP + GAP_DP) * d - GAP_DP * d);
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
        if (oldIndex >= 0 && slideT < 1f) {
            // 交叉淡化：旧视口向反向滑出渐隐，新视口自切向滑入渐显
            float unit = pointD + gapD;
            drawLayer(canvas, oldIndex, -slideDir * unit * slideT, (1 - slideT), false);
            drawLayer(canvas, currentIndex, slideDir * unit * (1 - slideT), slideT, true);
        } else {
            oldIndex = -1;
            drawLayer(canvas, currentIndex, 0, 1f, true);
        }
    }

    /**
     * 绘制一层视口：focusCard 落在其当前槽，槽位等距、整体平移 offset；
     * 透明度 = 层透明度 × 槽位距离衰减 × 端点边界衰减 × 卡片完成度。
     */
    private void drawLayer(Canvas canvas, int focusCard, float offset, float layerAlpha, boolean activeMorph) {
        int slot = slotOfCard(focusCard);
        float unit = pointD + gapD;
        float cy = getHeight() / 2f;
        float radius = pointD / 2f;

        for (int s = 0; s < MAX_SLOTS; s++) {
            int card = focusCard + (s - slot);
            if (card < 0 || card >= cardColors.length) {
                continue;
            }
            boolean active = s == slot;
            // 拉宽以槽位中心对称展开：槽距恒定、整体宽度不变、不推动相邻点
            float w = active && activeMorph ? pointD + (activeW - pointD) * slideT : pointD;
            float distAlpha = distanceAlpha(s - slot);
            if (s == 0) {
                distAlpha *= 0.55f;         // 左缘边界衰减
            } else if (s == MAX_SLOTS - 1) {
                distAlpha *= 0.65f;         // 右缘边界衰减
            }
            // 完成度只分两档（已学饱和 / 未学 30%）——不与槽梯度连乘，
            // 否则多层衰减叠乘后未完成点会淡到不可见
            int base = cardColors[card];
            float stateAlpha = Color.alpha(base) >= 128 ? 255f
                    : (active ? 200f : 76f);
            float finalAlpha = stateAlpha / 255f * distAlpha * layerAlpha;
            paint.setColor(Color.argb((int) finalAlpha, Color.red(base), Color.green(base), Color.blue(base)));

            float cx = s * unit + unit / 2f + offset;
            rect.set(cx - w / 2f, cy - radius, cx + w / 2f, cy + radius);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }

    /** 距当前槽的距离衰减：当前 255 → 相邻 170 → 更远 115/75 */
    private float distanceAlpha(int dist) {
        switch (Math.abs(dist)) {
            case 0:  return 255f;
            case 1:  return 170f;
            case 2:  return 115f;
            default: return 75f;
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
                    int cardIndex = cardOfSlot(slotAt(x));
                    if (cardIndex != currentIndex) {
                        setCurrentIndex(cardIndex);
                    }
                    if (seekListener != null) {
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
                seekToSlot(slotAt(x));
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
        int cardIndex = cardOfSlot(slotAt(lastTouchX));
        if (cardIndex != currentIndex) {
            setCurrentIndex(cardIndex);
        }
        if (seekListener != null) {
            seekListener.onSeek(cardIndex);
        }
    }

    /** 快速点击：跳到槽位对应卡片 */
    private void seekToSlot(int slot) {
        int cardIndex = cardOfSlot(slot);
        if (cardIndex != currentIndex) {
            setCurrentIndex(cardIndex);
        }
        if (seekListener != null) {
            seekListener.onSeek(cardIndex);
        }
    }
}
