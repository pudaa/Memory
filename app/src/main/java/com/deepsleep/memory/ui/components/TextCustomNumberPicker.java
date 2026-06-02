package com.deepsleep.memory.ui.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.NumberPicker;
import com.deepsleep.memory.R;

import java.lang.reflect.Field;

public class TextCustomNumberPicker extends NumberPicker {

    public TextCustomNumberPicker(Context context) {
        super(context);
    }

    public TextCustomNumberPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TextCustomNumberPicker(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    @Override
    public void addView(View child) {
        super.addView(child);
        updateView(child);
    }

    @Override
    public void addView(View child, int width, int height) {
        super.addView(child, width, height);
        updateView(child);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        updateView(child);
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        super.addView(child, params);
        updateView(child);
    }

    public void updateView(View view) {
        if (view instanceof EditText) {
            ((EditText) view).setTextSize(20);
        }
    }

    @SuppressLint("SoonBlockedPrivateApi")
    private void setDividerColor(NumberPicker picker) {
        Field field = null;
        try {
            field = NumberPicker.class.getDeclaredField("mSelectionDivider");
            if (field != null) {
                field.setAccessible(true);
                field.set(picker, new ColorDrawable(Color.RED));
            }
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

    }
    public void setNormalTextSize(float size) {
        try {
            @SuppressLint("SoonBlockedPrivateApi") java.lang.reflect.Field selectorWheelPaintField = NumberPicker.class.getDeclaredField("mSelectorWheelPaint");
            selectorWheelPaintField.setAccessible(true);
            android.graphics.Paint paint = (android.graphics.Paint) selectorWheelPaintField.get(this);
            paint.setTextSize(size * getResources().getDisplayMetrics().density);
            selectorWheelPaintField.setAccessible(false);
            // 重绘组件
            invalidate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
