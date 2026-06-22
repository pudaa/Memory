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
        switch (position) {
            case 0: return new FavoriteWordsFragment();
            case 1: return new WeakWordsFragment();
            case 2: return new LexiconBrowseFragment();
            default: return new FavoriteWordsFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
