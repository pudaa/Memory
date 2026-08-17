package com.deepsleep.memory.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * 圆形 Tab 行（裁剪页属性切换，参考 iOS 相机裁剪页设计）：
 * <ul>
 * <li>一行三个 {@link CircularValueView}（旋转 / 缩放 / 比例），整体可左右滑动；</li>
 * <li>滑动切换：手指滑动带动整行移动（曲线减速），松手后带惯性滑动，
 * 最终以曲线动画矫正——让最靠近屏幕正中的圆回到正中，正中圆 = 当前调整属性；</li>
 * <li>点击行为：点击非当前圆 → 平滑滑动使其回到正中并切换属性；
 * 点击当前圆（已在正中）→ 触发重置回调（{@link OnTabListener#onTabReselect}）；</li>
 * <li>滑动中不触发点击，点击判定仅在位移小于触摸容差时生效。</li>
 * </ul>
 */
public class CircularTabsView extends ViewGroup {

    public interface OnTabListener {
        /** 属性切换（index 0=旋转 1=缩放 2=比例） */
        void onTabSelected(int index);

        /** 点击了当前正中的圆：重置该属性 */
        void onTabReselect(int index);
    }

    public static final int TAB_COUNT = 3;

    private final CircularValueView[] tabs = new CircularValueView[TAB_COUNT];
    private OnTabListener listener;

    /** 当前浮点位置：正中圆 = Math.round(currentIndex) */
    private float currentIndex = 0f;
    private float spacing = dp(84f);
    private float radius = dp(30f);

    private VelocityTracker velocityTracker;
    private boolean scrolling = false;
    private float downX = 0f;
    private float lastX = 0f;
    private boolean settleRunning = false;

    public CircularTabsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        for (int i = 0; i < TAB_COUNT; i++) {
            tabs[i] = new CircularValueView(context, null);
            addView(tabs[i]);
        }
    }

    public void setListener(OnTabListener l) {
        this.listener = l;
    }

    public CircularValueView getTab(int index) {
        return tabs[index];
    }

    /** 切换到指定属性（点击外部/初始化时调用，平滑滚动） */
    public void selectTab(int index) {
        animateToIndex(index);
    }

    public int getSelectedIndex() {
        return Math.round(currentIndex);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    /** 索引夹取到有效范围 [0, TAB_COUNT-1]，保证边界滑动停止并回弹到最近有效项 */
    private float clampIndex(float idx) {
        return Math.max(0f, Math.min(TAB_COUNT - 1f, idx));
    }

    /**
     * 橡皮筋阻尼：边界内正常滑动，超出边界时以 0.3 阻尼系数继续（允许用户向外拖拽），
     * 松手后由 settleToNearest → clampIndex 回弹到最近有效项。
     */
    private float dampedIndex(float idx) {
        if (idx < 0f) {
            return idx * 0.3f;
        }
        if (idx > TAB_COUNT - 1f) {
            return TAB_COUNT - 1f + (idx - (TAB_COUNT - 1f)) * 0.3f;
        }
        return idx;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = MeasureSpec.makeMeasureSpec((int) (radius * 2 + dp(8f)), MeasureSpec.EXACTLY);
        for (CircularValueView tab : tabs) {
            tab.measure(MeasureSpec.makeMeasureSpec((int) (radius * 2), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec((int) (radius * 2), MeasureSpec.EXACTLY));
        }
        super.onMeasure(widthMeasureSpec, h);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        layoutTabs();
    }

    private void layoutTabs() {
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        for (int i = 0; i < TAB_COUNT; i++) {
            float x = centerX + (i - currentIndex) * spacing - radius;
            float y = centerY - radius;
            tabs[i].layout((int) x, (int) y, (int) (x + radius * 2), (int) (y + radius * 2));
            tabs[i].setSelected(Math.round(currentIndex) == i);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);
        switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
            if (settleRunning) {
                stopSettle();
            }
            scrolling = false;
            downX = event.getX();
            lastX = event.getX();
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        case MotionEvent.ACTION_MOVE:
            float dx = event.getX() - lastX;
            lastX = event.getX();
            if (Math.abs(event.getX() - downX) > dp(6f)) {
                scrolling = true;
            }
            if (scrolling) {
                currentIndex = dampedIndex(currentIndex - dx / spacing);
                layoutTabs();
            }
            return true;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
            velocityTracker.computeCurrentVelocity(1000);
            float velocityX = velocityTracker.getXVelocity();
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
            if (!scrolling && event.getActionMasked() == MotionEvent.ACTION_UP) {
                handleTap(event.getX(), event.getY());
            } else if (Math.abs(velocityX) > dp(300f)) {
                // 惯性滑动（衰减后矫正）
                fling(velocityX);
            } else {
                settleToNearest();
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    /** 点击判定：命中某个圆 → 切换（非当前）或重置（当前） */
    private void handleTap(float x, float y) {
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        for (int i = 0; i < TAB_COUNT; i++) {
            float cx = centerX + (i - currentIndex) * spacing;
            float dx = x - cx;
            float dy = y - centerY;
            if (dx * dx + dy * dy <= radius * radius * 1.3f) {
                int nearest = Math.round(currentIndex);
                if (nearest == i) {
                    if (listener != null) {
                        listener.onTabReselect(i);
                    }
                } else {
                    animateToIndex(i);
                }
                return;
            }
        }
        settleToNearest();
    }

    /** 惯性滑动：按速度继续移动并衰减，之后矫正到最近整数 */
    private void fling(float velocityX) {
        // index/s 速度 = px/s ÷ spacing
        final float speed = velocityX / spacing;
        stopSettle();
        postOnAnimation(new Runnable() {
            float v = speed;

            @Override
            public void run() {
                currentIndex -= v * 0.016f;
                v *= 0.94f; // 阻尼衰减（曲线减速）
                if (Math.abs(v) > dp(20f) / spacing) {
                    currentIndex = dampedIndex(currentIndex);
                    layoutTabs();
                    postOnAnimation(this);
                } else {
                    settleToNearest();
                }
            }
        });
    }

    /** 曲线动画矫正：让最靠近正中的圆回到正中 */
    private void settleToNearest() {
        animateToIndex(Math.round(currentIndex));
    }

    private void animateToIndex(final float targetIndex) {
        final float from = currentIndex;
        final float to = clampIndex(targetIndex);
        if (Math.abs(to - from) < 0.001f) {
            currentIndex = to;
            layoutTabs();
            notifySelected();
            return;
        }
        stopSettle();
        settleRunning = true;
        final long duration = (long) Math.min(320, 160 + Math.abs(to - from) * 120);
        final long start = System.currentTimeMillis();
        final DecelerateInterpolator interpolator = new DecelerateInterpolator(1.6f);
        postOnAnimation(new Runnable() {
            @Override
            public void run() {
                float t = Math.min(1f, (System.currentTimeMillis() - start) / (float) duration);
                currentIndex = from + (to - from) * interpolator.getInterpolation(t);
                layoutTabs();
                if (t < 1f) {
                    postOnAnimation(this);
                } else {
                    settleRunning = false;
                    currentIndex = to;
                    layoutTabs();
                    notifySelected();
                }
            }
        });
    }

    private void stopSettle() {
        settleRunning = false;
    }

    /** 矫正完成后通知属性切换（仅当整数索引变化时） */
    private void notifySelected() {
        int index = Math.round(currentIndex);
        if (listener != null) {
            listener.onTabSelected(index);
        }
    }
}
