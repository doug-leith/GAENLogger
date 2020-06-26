package com.leith.gaenlogger;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

public class MainActivity extends AppCompatActivity {

    private static final int FIRST_FRAG=0;
    private static final int SETTINGS_FRAG=1;
    private SharedPreferences sharedPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final ViewPager viewPager = findViewById(R.id.pager);
        TabLayout tabLayout = findViewById(R.id.tabDots);
        tabLayout.setupWithViewPager(viewPager, true);
        final Adapter adapter = new Adapter(getSupportFragmentManager());
        viewPager.setAdapter(adapter);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplication());
        viewPager.setCurrentItem(sharedPref.getInt("tab",0));
        //viewPager.setCurrentItem(0);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            // This method will be invoked when a new page becomes selected.
            public void onPageSelected(int position) {
                sharedPref.edit().putInt("tab",position).apply();
                adapter.getItem(position).onResume();
            }
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}
            public void onPageScrollStateChanged(int state) {}
        });
    }

    static class Adapter extends FragmentPagerAdapter {
        Adapter(FragmentManager fm) {
            super(fm);
        }

        final Fragment firstFrag = new FirstFragment();
        final Fragment settingsFrag = new SettingsFragment();

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public Fragment getItem(int position) {
            switch(position) {
                case FIRST_FRAG:
                    return firstFrag;
                case SETTINGS_FRAG:
                    return settingsFrag;
                 default:
                    return null;
            }
        }
    }
}
