package com.deepsleep.memory.ui.main_view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import com.deepsleep.memory.R;
import com.deepsleep.memory.settings.InnerSettingsManager;
import com.deepsleep.memory.settings.UserSettingsManager;

import java.util.ArrayList;
import java.util.List;

public class WordCardContainer extends FrameLayout implements UserSettingsManager.OnSettingsChangedListener {

    private static final float STACK_OFFSET_X = 60f; // 右侧堆叠卡片的X轴偏移量
    private static final float STACK_SCALE_FACTOR = 1f; // 卡片缩放
    private static final float SWIPE_THRESHOLD = 0.3f; // 滑动阈值
    private static final long ANIMATION_DURATION = 400; // 动画持续时间
    private static final long LONG_PRESS_TIMEOUT = 1000; // 长按超时时间

    private List<View> cardList = new ArrayList<>();
    private int currentCardIndex = 0;
    private View currentCard;
    private ViewGroup cardStackContainer;
    private OnCardSwipedListener onCardSwipedListener;
    private boolean isMoving = false;
    private Runnable longPressRunnable;
    private final Handler longPressHandler = new Handler();
    private UserSettingsManager userSettingsManager;
    private int slideFlag = 1;
    /** 交互模式：true 时卡片内子视图（按钮/输入框）可交互，不再拦截触摸事件 */
    private boolean interactiveMode = false;

    /** 设置交互模式 */
    public void setInteractiveMode(boolean interactive) {
        this.interactiveMode = interactive;
    }

    @Override
    public void onSettingChanged(String key, Object value) {
        // 当设置变化时更新slideFlag
        if (UserSettingsManager.KEY_IS_SLIDE_BACK.equals(key)) {
            slideFlag = (Boolean) value ? 1 : -1;
        }
    }

    @Override
    public void onSettingsReset() {
        slideFlag = userSettingsManager.isSlideBackEnabled() ? 1 : -1;
    }

    public interface OnCardSwipedListener {
        void onCurrentCardChanged(View newCard);

    }

    public void setOnCardSwipedListener(OnCardSwipedListener listener) {
        this.onCardSwipedListener = listener;
    }

    public WordCardContainer(@NonNull Context context) {
        super(context);
        init(context);
    }

    public WordCardContainer(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public WordCardContainer(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // 从XML布局加载容器结构
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View rootView = inflater.inflate(R.layout.card_container_layout, this, true);

        // 获取容器引用
        cardStackContainer = rootView.findViewById(R.id.card_stack_container);

        userSettingsManager = UserSettingsManager.getInstance(getContext());
        userSettingsManager.addSettingsChangeListener(this);
        slideFlag = userSettingsManager.isSlideBackEnabled() ? 1 : -1;

        // 初始化手势检测
        setOnTouchListener((v, event) -> {
            if (currentCard == null)
                return false;

            // 交互模式下不拦截触摸，让子视图（按钮/输入框）处理
            // if (interactiveMode) {
            // return false;
            // }

            switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                currentCard.setTranslationZ(1);
                startX = event.getX();
                startY = event.getY();
                lastX = startX;
                isMoving = false;
                currentCard.animate().setDuration(0).translationX(0);
                getParent().requestDisallowInterceptTouchEvent(true);
                // 设置长按检测
                setupLongPressDetection();
                return true;

            case MotionEvent.ACTION_MOVE:
                float currentX = event.getX();
                float currentY = event.getY();
                float deltaX = currentX - lastX;
                float deltaY = currentY - startY;
                float dist = currentX - startX;
                // 判断是否为移动操作（避免轻微抖动误判）
                if (!isMoving && (Math.abs(dist) > 20 || Math.abs(deltaY) > 20)) {
                    isMoving = true;
                    // 取消长按检测
                    cancelLongPressDetection();
                }
                // 移动当前卡片
                currentCard.setTranslationX(currentCard.getTranslationX() + deltaX);
                lastX = currentX;

                // 计算透明度变化（根据滑动距离）
                float progress = Math.abs(deltaX) / getWidth();
                float alpha = 1 - Math.min(progress, 0.5f);
                currentCard.setAlpha(alpha);

                // 根据滑动卡片位置显示预览下一个卡片
                if (dist > 0 && (slideFlag == 1 ? currentCardIndex > 0 : currentCardIndex < cardList.size() - 1)) {
                    previewCard(currentCardIndex - slideFlag); // 显示前一个卡片
                } else if (dist < 0
                        && (slideFlag == 1 ? currentCardIndex < cardList.size() - 1 : currentCardIndex > 0)) {
                    previewCard(currentCardIndex + slideFlag);
                }

                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelLongPressDetection();
                float endX = event.getX();
                float distanceX = endX - startX;

                // 判断是否是点击事件（滑动距离小）
                boolean isClick = Math.abs(distanceX) < 10 && Math.abs(event.getY() - startY) < 10;

                if (isClick) {
                    // 是点击事件，调用 performClick()
                    currentCard.performClick();
                    LinearLayout definitionContainer = currentCard.findViewById(R.id.definition_container);
                    if (definitionContainer != null && definitionContainer.getVisibility() != View.VISIBLE) {
                        // int visibility = definitionContainer.getVisibility() == View.VISIBLE ?
                        // View.GONE : View.VISIBLE;
                        definitionContainer.setVisibility(View.VISIBLE);
                        getParent().requestDisallowInterceptTouchEvent(false);
                        ValueAnimator scaleDown = ValueAnimator.ofFloat(1f, 0.99f, 1f);
                        scaleDown.setDuration(300);
                        scaleDown.setInterpolator(new AccelerateDecelerateInterpolator());
                        scaleDown.addUpdateListener(animation -> {
                            float scale = (float) animation.getAnimatedValue();
                            currentCard.setScaleX(scale);
                            currentCard.setScaleY(scale);
                        });
                        scaleDown.start();
                    }

                    return true;
                }

                // 处理滑动事件
                if (Math.abs(distanceX) > getWidth() * SWIPE_THRESHOLD
                        && ((slideFlag == 1 ? currentCardIndex < cardList.size() - 1 : currentCardIndex > 0)
                                || distanceX > 0)
                        && ((slideFlag == 1 ? currentCardIndex > 0 : currentCardIndex < cardList.size() - 1)
                                || distanceX < 0)) {
                    // 如果滑动超过阈值，移除卡片并显示下一个
                    animateCardOut(distanceX > 0 ? 1 : -1);
                } else {
                    // 否则将卡片返回原位
                    animateCardBack();
                }
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
            return false;
        });
    }

    public void addCard(View cardView) {
        cardList.add(cardView);

        cardView.setVisibility(View.INVISIBLE);

        if (cardList.size() == 1) {
            showCard(0);
            onCardSwipedListener.onCurrentCardChanged(currentCard);
            cardStackContainer.addView(cardView);
        } else {
            cardStackContainer.addView(cardView);
        }
    }

    public void addCardGroup(List<View> cards) {
        for (View card : cards) {
            addCard(card);
        }
    }

    private void showCard(int index) {
        // Log.i("Showing card at index: " + index, "showCard");
        if (index < 0 || index >= cardList.size()) {
            Log.i("Invalid index: " + index, "showCard");
            return;
        }

        if (currentCard != null) {
            Log.i("Current card is not null", "showCard");
            currentCard.setVisibility(View.INVISIBLE);
        }
        currentCard = cardList.get(index);
        currentCardIndex = index;
        hideOtherCardsExcept(currentCard, null);
        if (currentCard.getVisibility() == View.VISIBLE) {
            currentCard.setAlpha(1f);
            currentCard.setScaleX(1f);
            currentCard.setScaleY(1f);
            currentCard.setTranslationX(0);
            currentCard.setTranslationZ(1);
            setupStackedCards(); // 更新堆叠卡片位置
            notifyCurrentCardChanged();
            return;
        }
        currentCard.setAlpha(0f); // 初始透明度为0
        currentCard.setScaleX(0.97f);
        currentCard.setScaleY(0.97f);
        currentCard.setTranslationX(0);
        currentCard.setVisibility(View.VISIBLE);

        // 添加渐入动画
        currentCard.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(ANIMATION_DURATION)
                .setInterpolator(new AccelerateDecelerateInterpolator()).start();

        for (int i = index + 1; i < cardList.size(); i++) {
            setupStackedCard(cardList.get(i), i);
        }

        if (onCardSwipedListener != null) {
            onCardSwipedListener.onCurrentCardChanged(currentCard);
        }
    }

    private void setupStackedCards() {
        for (int i = currentCardIndex + 1; i < cardList.size(); i++) {
            setupStackedCard(cardList.get(i), i);
        }
    }

    private void notifyCurrentCardChanged() {
        if (onCardSwipedListener != null) {
            onCardSwipedListener.onCurrentCardChanged(currentCard);
        }
    }

    private void hideOtherCardsExcept(View currentCard, View previewCard) {
        for (View card : cardList) {
            if (card == currentCard || card == previewCard)
                continue;
            if (card.getVisibility() != View.INVISIBLE) {
                // 添加消失动画
                card.animate().alpha(0f).setDuration(ANIMATION_DURATION)
                        .setInterpolator(new AccelerateDecelerateInterpolator()).withEndAction(() -> {
                            card.setVisibility(View.INVISIBLE);
                        }).start();
            }
        }
    }

    public void previewCard(int index) {
        if (index < 0 || index >= cardList.size()) {
            return;
        }
        View previewCard = cardList.get(index);
        if (previewCard.getVisibility() == View.VISIBLE) {
            return;
        }
        hideOtherCardsExcept(currentCard, previewCard);
        // 设置预览卡片在当前卡片下方
        previewCard.setTranslationZ(-1); // 层级低于当前卡片
        previewCard.setVisibility(View.VISIBLE);

        previewCard.setAlpha(0f); // 初始透明度为0
        previewCard.setScaleX(0.97f);
        previewCard.setScaleY(0.97f);
        previewCard.setTranslationX(0);
        previewCard.setVisibility(View.VISIBLE);

        // 添加渐入动画
        previewCard.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(ANIMATION_DURATION / 2)
                .setInterpolator(new AccelerateDecelerateInterpolator()).start();

    }

    public List<View> getAllCards() {
        return new ArrayList<>(cardList);
    }

    /** 安全移除所有卡片（仅清空 cardStackContainer 和内部列表，不破坏容器结构） */
    public void removeAllCards() {
        cardList.clear();
        currentCard = null;
        currentCardIndex = 0;
        if (cardStackContainer != null) {
            cardStackContainer.removeAllViews();
        }
    }

    /** 获取当前展示的卡片视图（用于外部重置计时器等操作） */
    public View getCurrentCardView() {
        return currentCard;
    }

    private void setupStackedCard(View card, int position) {
        // 设置缩放
        float scale = STACK_SCALE_FACTOR;
        card.setScaleX(scale);
        card.setScaleY(scale);

        // 设置偏移量
        card.setTranslationX(STACK_OFFSET_X * (position - currentCardIndex));
    }

    private void animateCardBack() {
        currentCard.setTranslationZ(1);
        currentCard.animate().translationX(0).translationY(0).scaleX(1).scaleY(1).alpha(1)
                .setDuration(ANIMATION_DURATION).start();
        hideOtherCardsExcept(currentCard, null);

    }

    void animateCardOut(float direction) { // 方向 1 向右 -1 向左
        float finalX = direction > 0 ? getWidth() : -getWidth();
        if (direction > 0 && (slideFlag == 1 ? currentCardIndex > 0 : currentCardIndex < cardList.size() - 1)) {
            previewCard(currentCardIndex - slideFlag);
        } else if (direction < 0 && (slideFlag == 1 ? currentCardIndex < cardList.size() - 1 : currentCardIndex > 0)) {
            previewCard(currentCardIndex + slideFlag);
        }
        currentCard.animate().x(finalX).translationY(0).scaleX(STACK_SCALE_FACTOR).scaleY(STACK_SCALE_FACTOR).alpha(0f)
                .setDuration(ANIMATION_DURATION).withEndAction(() -> {
                    // 动画结束时更新当前卡片
                    if (direction > 0
                            && (slideFlag == 1 ? currentCardIndex > 0 : currentCardIndex < cardList.size() - 1)) {
                        showCard(currentCardIndex - slideFlag);
                    } else if (direction < 0
                            && (slideFlag == 1 ? currentCardIndex < cardList.size() - 1 : currentCardIndex > 0)) {
                        showCard(currentCardIndex + slideFlag);
                    } else {
                        animateCardBack();
                    }
                }).start();
    }

    public void showCardAtIndex(int index) {
        if (index >= 0 && index < cardList.size()) {
            showCard(index);
        }
    }

    private void setupLongPressDetection() {
        longPressRunnable = this::performLongPressAction;
        longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT);
    }

    private void cancelLongPressDetection() {
        if (longPressRunnable != null) {
            longPressHandler.removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
        // 卡片恢复
        currentCard.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(ANIMATION_DURATION)
                .setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    private void performLongPressAction() {
        if (currentCard == null)
            return;

        // 触发卡片缩小动画
        ValueAnimator shrinkAnimator = ValueAnimator.ofFloat(1f, 0.95f);
        shrinkAnimator.setDuration(300);
        shrinkAnimator.addUpdateListener(animation -> {
            float scale = (float) animation.getAnimatedValue();
            currentCard.setScaleX(scale);
            currentCard.setScaleY(scale);
        });
        shrinkAnimator.start();

        // 动画结束后通知监听器执行跳转
        shrinkAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onCardSwipedListener instanceof OnCardLongPressedListener) { // 判断监听器是否实现了OnCardLongPressedListener接口
                    ((OnCardLongPressedListener) onCardSwipedListener).onCardLongPressed(currentCard);
                }
                currentCard.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(ANIMATION_DURATION)
                        .setInterpolator(new AccelerateDecelerateInterpolator()).start();
            }
        });
    }

    public interface OnCardLongPressedListener {
        void onCardLongPressed(View cardView);
    }

    // 记得在适当的时候移除监听器，避免内存泄漏
    public void destroy() {
        if (userSettingsManager != null) {
            userSettingsManager.removeSettingsChangeListener(this);
        }
    }

    private float startX = 0;
    private float startY = 0;
    private float lastX = 0;

}