package com.deepsleep.memory.ui.extra_view.word_search_view;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class WordSearchPagerAdapter extends FragmentStateAdapter {

    private final Fragment[] fragments = new Fragment[4];

    public WordSearchPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
        fragments[0] = new WordSearchLocalFragment();
        fragments[1] = new WordSearchWebFragment();
        fragments[2] = new WordSearchOxfordFragment();    // 牛津词典
        fragments[3] = new WordSearchCambridgeFragment(); // 剑桥词典
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return fragments[position];
    }

    @Override
    public int getItemCount() {
        return fragments.length;
    }

    public Fragment getFragment(int position) {
        return fragments[position];
    }
}
