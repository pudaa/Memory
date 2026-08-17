package com.deepsleep.memory.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 圆形数值组件（裁剪页 Tab 标识 + 数值显示，参考 iOS 相机裁剪页设计）：
 * <ul>
 * <li>外圈圆环：暗色底色 + 主题蓝亮色弧；亮色弧占比 = 数值比例（progress 0~1），
 * 延伸方向 = 旋转方向（顺时针为正，{@link #setDirection}）；</li>
 * <li>内圈铺满暗色底，中央数字文本显示当前值，数字颜色与外环亮色一致（主题蓝）；</li>
 * <li>选中态：外环底色略微提亮 + 数字加粗，用于 Tab 切换时区分当前项。</li>
 * </ul>
 */
public class CircularValueView extends View {

    private static final int RING_ACCENT = 0xFFFFFFFF; // 亮色（白，与工具 UI 一致，避免杂色）
    private static final int RING_BASE = 0x40FFFFFF; // 暗色圆环底（25% 白，可辨识）
    private static final int RING_BASE_SELECTED = 0x66FFFFFF; // 选中态圆环底
    private static final int INNER_BG = 0x26FFFFFF; // 内圈暗色底

    private float progress = 0f;
    private boolean clockwise = true;
    private String valueText = "";
    private boolean selected = false;

    private final Paint ringBasePaint;
    private final Paint ringAccentPaint;
    private final Paint innerPaint;
    private final Paint textPaint;
    private final RectF ringRect = new RectF();

    public CircularValueView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        ringBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringBasePaint.setStyle(Paint.Style.STROKE);
        ringBasePaint.setStrokeWidth(dp(3f));
        ringAccentPaint = new Paint(ringBasePaint);
        ringAccentPaint.setColor(RING_ACCENT);
        innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint.setColor(INNER_BG);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(RING_ACCENT);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(dp(13f));
        setWillNotDraw(false);
    }

    /** 设置数值比例（0~1，决定外环亮色弧占比） */
    public void setProgress(float p) {
        this.progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    /** 亮色弧延伸方向：true=顺时针，false=逆时针（旋转角度正负用） */
    public void setDirection(boolean clockwise) {
        this.clockwise = clockwise;
        invalidate();
    }

    /** 设置中央数值文本 */
    public void setValueText(String text) {
        this.valueText = text == null ? "" : text;
        invalidate();
    }

    /** 选中态（Tab 当前项）：圆环底提亮 + 文本加粗 */
    public void setSelected(boolean selected) {
        this.selected = selected;
        invalidate();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) / 2f - dp(3f);

        // 内圈暗色底
        canvas.drawCircle(cx, cy, radius - dp(3f), innerPaint);

        // 外环暗色底（选中态提亮）
        ringBasePaint.setColor(selected ? RING_BASE_SELECTED : RING_BASE);
        canvas.drawCircle(cx, cy, radius, ringBasePaint);

        // 外环亮色弧：始终从 12 点方向（起点）出发，正向顺时针填充、反向逆时针填充，
        // 对称延伸（避免反向时圆弧整体旋转的视觉异常）
        if (progress > 0f) {
            float sweep = 360f * progress;
            ringRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
            if (clockwise) {
                canvas.drawArc(ringRect, -90f, sweep, false, ringAccentPaint);
            } else {
                canvas.drawArc(ringRect, -90f, -sweep, false, ringAccentPaint);
            }
        }

        // 中央数值文本（颜色与外环亮色一致）
        textPaint.setFakeBoldText(selected);
        float textY = cy - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.drawText(valueText, cx, textY, textPaint);
    }
}
