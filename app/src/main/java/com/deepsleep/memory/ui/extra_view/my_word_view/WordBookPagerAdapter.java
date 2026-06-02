package com.deepsleep.memory.ui.extra_view.my_word_view;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class WordBookPagerAdapter extends FragmentStateAdapter {

    public WordBookPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return new FavoriteWordsFragment(); // 收藏单词
        } else {
            return new WeakWordsFragment(); // 薄弱单词
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
