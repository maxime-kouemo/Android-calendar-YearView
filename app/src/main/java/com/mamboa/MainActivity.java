package com.mamboa;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.joda.time.DateTime;

import java.util.ArrayList;

/**
 * Created by mamboa on 9/4/2018.
 */
public class MainActivity extends AppCompatActivity {
    private YearFragmentAdapter yearFragmentAdapter;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private final static int MIN_YEAR = 1945;
    private final static int MAX_YEAR = 2045;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);
        yearFragmentAdapter = new YearFragmentAdapter(this, buildListOfYears());
        viewPager.setAdapter(yearFragmentAdapter);
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(String.valueOf(buildListOfYears().get(position)))
        ).attach();

        selectFirstDisplayedYear();
    }

    private ArrayList<Integer> buildListOfYears() {
        ArrayList<Integer> listYears = new ArrayList<>();
        for (int i = MIN_YEAR; i <= MAX_YEAR; i++) {
            listYears.add(i);
        }
        return listYears;
    }

    /**
     * The default displayed year is the current one
     */
    private void selectFirstDisplayedYear() {
        if (yearFragmentAdapter != null) {
            int defaultYear = new DateTime().getYear();
            int desiredYearPosition = yearFragmentAdapter.getYearPosition(defaultYear);
            if (desiredYearPosition > -1)
                viewPager.setCurrentItem(desiredYearPosition);
        }
    }
}