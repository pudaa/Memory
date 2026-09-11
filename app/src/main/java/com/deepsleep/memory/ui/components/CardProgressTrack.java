package com.deepsleep.memory.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.deepsleep.memory.R;

/**
 * 卡片进度点阵：横向拨盘（横向滚动视口）。
 *
 * <p>每日卡片可能有几十张，点数固定为 {@link #MAX_SLOTS} 个槽位，
 * <b>当前卡固定落在第 2 个槽位</b>（左侧留一槽展示"上一张"），
 * 其余槽位依次展示后续卡片，越界槽位不绘制：</p>
 * <ul>
 *   <li>透明度按与当前槽的距离衰减：当前点饱和，相邻半透明，
 *       越远越淡；视口两端额外衰减表示边界——有限点承载无限卡片；</li>
 *   <li>切换卡片时整条视口向切向平移一格（拨盘转动），当前点在平移中
 *       从圆形撑开为胶囊（圆 → 圆角矩形形变）；</li>
 *   <li>快速点击：跳到该槽对应卡片；</li>
 *   <li>长按 0.2s 进入拖拽：手指落在哪个槽，该点拉宽为当前点并
 *       <b>实时回调切卡</b>（容器以既有滑出+渐显动效切换），松手结束。</li>
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
    /** 当前点撑开后的宽度（胶囊） */
    private static final float ACTIVE_WIDTH_DP = 16f;
    /** 视口槽位数 */
    private static final int MAX_SLOTS = 9;
    private static final float VERTICAL_PADDING_DP = 12f;
    /** 长按触发拖拽的时长 */
    private static final int DRAG_ACTIVATE_MS = 200;
    /** 拨盘平移 + 形变动画时长 */
    private static final long SLIDE_ANIM_MS = 200;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    /** 每张卡的最终颜色（含完成态透明度），由调用方传入 */
    private int[] cardColors = new int[0];
    private int currentIndex = 0;
    /** 当前卡所在槽位（0 或 1：首卡在槽 0，其余固定槽 1，左侧留槽展示上一张） */
    private int currentSlot = 0;
    /** 拨盘平移偏移（px，动画中从 ±槽宽 过渡到 0） */
    private float slideOffset = 0f;
    /** 当前槽宽度形变进度（0=圆，1=胶囊） */
    private float morphT = 1f;
    /** 拖拽预览槽（-1 表示非拖拽态） */
    private int dragSlot = -1;
    private float pointD;
    private float gapD;
    private float activeW;
    private float verticalPadding;
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
        paint.setColor(ContextCompat.getColor(getContext(), R.color.light_gray));
    }

    /** 传入每张卡的最终颜色（完成态透明度编码），触发重绘 */
    public void setSegments(int[] colors) {
        cardColors = colors == null ? new int[0] : colors;
        if (currentIndex > cardColors.length - 1) {
            currentIndex = Math.max(cardColors.length - 1, 0);
        }
        currentSlot = Math.min(1, currentIndex);
        slideOffset = 0f;
        morphT = 1f;
        invalidate();
    }

    /**
     * 当前卡切换（外部回流或交互）：整条视口向切向平移一格（拨盘转动），
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
        currentSlot = Math.min(1, currentIndex);
        startSlide(dir);
    }

    public void setOnSeekListener(OnSeekListener listener) {
        seekListener = listener;
    }

    // ==================== 槽位 ↔ 卡片映射 ====================

    /** 槽位 s 对应的卡片序号（当前卡固定在 currentSlot，向两侧展开；越界返回 -1/totalCards） */
    private int cardAtSlot(int slot) {
        return currentIndex + (slot - currentSlot);
    }

    /** 槽位 s 对应卡片是否存在 */
    private boolean slotHasCard(int slot) {
        int card = cardAtSlot(slot);
        return card >= 0 && card < cardColors.length;
    }

    // ==================== 透明度梯度（拨盘边界衰减） ====================

    /**
     * 槽位透明度：当前点饱和，按距离衰减（半透 → 更淡），
     * 视口两端再额外衰减表示边界（左缘最先隐没、右缘提示尚有内容）。
     */
    private float alphaForSlot(int slot) {
        int dist = Math.abs(slot - currentSlot);
        float alpha;
        switch (dist) {
            case 0:  alpha = 255f; break;
            case 1:  alpha = 170f; break;
            case 2:  alpha = 115f; break;
            default: alpha = 75f;  break;
        }
        if (slot == 0) {
            alpha *= 0.45f;          // 左缘：即将隐没
        } else if (slot == MAX_SLOTS - 1) {
            alpha *= 0.6f;           // 右缘：边界暗示
        }
        return alpha;
    }

    // ==================== 拨盘平移动画 ====================

    /** dir=+1 前进（内容自右滑入），dir=-1 后退（内容自左滑入） */
    private void startSlide(int dir) {
        if (slideAnimator != null) {
            slideAnimator.cancel();
        }
        float unit = pointD + gapD;
        float from = dir * unit;
        slideAnimator = ValueAnimator.ofFloat(0f, 1f);
        slideAnimator.setDuration(SLIDE_ANIM_MS);
        slideAnimator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            slideOffset = from * (1 - t);
            morphT = t;
            invalidate();
        });
        slideAnimator.start();
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
        float unit = pointD + gapD;
        float cy = getHeight() / 2f;
        float radius = pointD / 2f;

        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            if (!slotHasCard(slot)) {
                continue;
            }
            boolean isActive = slot == currentSlot;
            // 当前槽宽度随 morphT 从圆撑开为胶囊；拖拽预览槽强制胶囊
            float w = isActive ? pointD + (activeW - pointD) * morphT : pointD;
            if (slot == dragSlot && dragging) {
                w = activeW;
            }
            float alpha = alphaForSlot(slot);
            int base = cardColors[cardAtSlot(slot)];
            paint.setColor(Color.argb((int) alpha, Color.red(base), Color.green(base), Color.blue(base)));

            float x = slot * unit + slideOffset + (unit - w) / 2f;
            rect.set(x, cy - radius, x + w, cy + radius);
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
                    dragSlot = slotAt(x);
                    seekToSlot(dragSlot);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(dragActivator);
                pressActive = false;
                if (dragging) {
                    dragging = false;
                    dragSlot = -1;
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

    /** 长按到达 0.2s：进入拖拽模式，立即响应手指位置 */
    private void startDrag() {
        if (!pressActive || dragging) {
            return;
        }
        dragging = true;
        // 拖拽期间禁止父容器拦截手势
        getParent().requestDisallowInterceptTouchEvent(true);
        lastTouchX = Math.min(Math.max(lastTouchX, 0), getWidth());
        dragSlot = slotAt(lastTouchX);
        seekToSlot(dragSlot);
    }

    /** 切换到槽位对应的卡片：拨盘平移 + 实时回调切卡 */
    private void seekToSlot(int slot) {
        int cardIndex = cardAtSlot(slot);
        if (cardIndex < 0 || cardIndex >= cardColors.length) {
            return;
        }
        if (cardIndex != currentIndex) {
            setCurrentIndex(cardIndex);
        }
        if (seekListener != null && cardIndex != lastSeekedCard) {
            lastSeekedCard = cardIndex;
            seekListener.onSeek(cardIndex);
        }
    }

    private int lastSeekedCard = -1;

    private int slotAt(float x) {
        float unit = pointD + gapD;
        return (int) Math.min(MAX_SLOTS - 1, Math.max(0, x / unit));
    }

    private int clampCard(int index) {
        int max = Math.max(cardColors.length - 1, 0);
        return Math.min(Math.max(index, 0), max);
    }
}
