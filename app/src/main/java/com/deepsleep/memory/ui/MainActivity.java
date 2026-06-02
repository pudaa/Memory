package com.deepsleep.memory.ui;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.deepsleep.memory.R;
import com.deepsleep.memory.settings.ThemeHelper;
import com.deepsleep.memory.ui.main_view.DailyReadingFragment;
import com.deepsleep.memory.ui.main_view.TreasureBoxFragment;
import com.deepsleep.memory.ui.main_view.UserHomeFragment;
import com.deepsleep.memory.ui.main_view.WordLearningFragment;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    ImageView mImg1;
    ImageView mImg2;
    ImageView mImg3;
    ImageView mImg4;
    WordLearningFragment wordLearningFragment;
    TreasureBoxFragment treasureBoxFragment;
    DailyReadingFragment dailReadingFragment;
    UserHomeFragment userHomeFragment;
    LinearLayout linearLayout1;
    LinearLayout linearLayout2;
    LinearLayout linearLayout3;
    LinearLayout linearLayout4;

    private int currentTab = 0;
    private int currentThemeMode = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        // EdgeToEdge.enable(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        initViews();
        selectTab(currentTab);
    }

    private void initViews() {
        //初始化四个Tab的布局文件
        linearLayout1 = (LinearLayout) findViewById(R.id.linear1);
        linearLayout2 = (LinearLayout) findViewById(R.id.linear2);
        linearLayout3 = (LinearLayout) findViewById(R.id.linear3);
        linearLayout4 = (LinearLayout) findViewById(R.id.linear4);
        //设置底部菜单的四个线性布局被监听
        linearLayout1.setOnClickListener(this);
        linearLayout2.setOnClickListener(this);
        linearLayout3.setOnClickListener(this);
        linearLayout4.setOnClickListener(this);
        //初始化四个ImageView
        mImg1 = (ImageView) findViewById(R.id.img1);
        mImg2 = (ImageView) findViewById(R.id.img2);
        mImg3 = (ImageView) findViewById(R.id.img3);
        mImg4 = (ImageView) findViewById(R.id.img4);
    }
    //进行选中底部菜单的处理
    private void selectTab(int i) {
        //获取FragmentManager对象
        FragmentManager manager = getSupportFragmentManager();
        //获取FragmentTransaction对象
        FragmentTransaction transaction = manager.beginTransaction();
        // 添加切换动画
        if (i > currentTab) {
            // 向右切换 - 新Fragment从右侧进入，旧Fragment向左退出
            transaction.setCustomAnimations(
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
            );
        } else if (i < currentTab) {
            // 向左切换 - 新Fragment从左侧进入，旧Fragment向右退出
            transaction.setCustomAnimations(
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
            );
        }
        currentTab = i;
        //先隐藏所有的Fragment
        hideFragments(transaction);
        switch (i) {
            //当选中点击的是第一页的底部菜单时
            case 0:
                //如果第一页对应的Fragment没有实例化，则进行实例化，并显示出来
                if (wordLearningFragment == null) {
                    wordLearningFragment = new WordLearningFragment();
                    transaction.add(R.id.id_content, wordLearningFragment);
                } else {
                    //如果第一页对应的Fragment已经实例化，则直接显示出来
                    transaction.show(wordLearningFragment);
                }
                break;
            case 1:
                // mImg2.setImageResource(R.drawable.user_selected);
                if (treasureBoxFragment == null) {
                    treasureBoxFragment = new TreasureBoxFragment();
                    transaction.add(R.id.id_content, treasureBoxFragment);
                } else {
                    transaction.show(treasureBoxFragment);
                }
                break;
            case 2:
                // mImg3.setImageResource(R.drawable.finding_selected);
                if (dailReadingFragment == null) {
                    dailReadingFragment = new DailyReadingFragment();
                    transaction.add(R.id.id_content, dailReadingFragment);
                } else {
                    transaction.show(dailReadingFragment);
                }
                break;
            case 3:
                // mImg4.setImageResource(R.drawable.me_selected);
                if (userHomeFragment == null) {
                    userHomeFragment = new UserHomeFragment();
                    transaction.add(R.id.id_content, userHomeFragment);
                } else {
                    transaction.show(userHomeFragment);
                }
                break;
        }
        //不要忘记提交事务
        transaction.commit();
    }

    //将四个的Fragment隐藏
    private void hideFragments(FragmentTransaction transaction) {
        if (wordLearningFragment != null) {
            transaction.hide(wordLearningFragment);
        }
        if (treasureBoxFragment != null) {
            transaction.hide(treasureBoxFragment);
        }
        if (dailReadingFragment != null) {
            transaction.hide(dailReadingFragment);
        }
        if (userHomeFragment != null) {
            transaction.hide(userHomeFragment);
        }
    }


    //处理底部菜单的点击事件
    @Override
    public void onClick(View v) {
        resetImgs(); //先将四个ImageView还原
        if (v.getId() == R.id.linear1) {
            selectTab(0);
        } else if (v.getId() == R.id.linear2) {
            selectTab(1);
        } else if (v.getId() == R.id.linear3) {
            selectTab(2);
        } else if (v.getId() == R.id.linear4) {
            selectTab(3);
        }

    }
    //将四个ImageView置为灰色
    private void resetImgs() {
        mImg1.setImageResource(R.drawable.word_learning);
        mImg2.setImageResource(R.drawable.treasure_box);
        mImg3.setImageResource(R.drawable.daily_reading);
        mImg4.setImageResource(R.drawable.user_home);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 检测主题是否在设置页被更改，若更改则重建 Activity
        int savedMode = ThemeHelper.getThemeMode(this);
        if (currentThemeMode != -1 && currentThemeMode != savedMode) {
            recreate();
        }
        currentThemeMode = savedMode;
    }
}