package com.mamboa;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

public class MainActivityFragment extends Fragment {
    private ViewPager2 viewPager;
    private YearFragmentAdapter adapter;
    private TabLayout tabLayout;
    private int currentPosition = -1;
    private List<Integer> listOfYears = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewPager = view.findViewById(R.id.viewPager);
        tabLayout = view.findViewById(R.id.tabLayout);
        /*tabLayout.addTab(tabLayout.newTab().setText("Tab 1"));
        tabLayout.addTab(tabLayout.newTab().setText("Tab 2"));
        tabLayout.addTab(tabLayout.newTab().setText("Tab 3"));*/
        adapter = new YearFragmentAdapter(requireActivity(), buildListOfYears());
        viewPager.setAdapter(adapter);
        if (currentPosition == -1) {
            selectFirstDisplayedYear();
        } else {
            viewPager.setCurrentItem(currentPosition);
        }
        /*
        if (adapter == null) {
            adapter = new YearFragmentAdapter(requireActivity(), buildListOfYears());
            viewPager.setAdapter(adapter);
            if (currentPosition == -1) {
                selectFirstDisplayedYear();
            } else {
                viewPager.setCurrentItem(currentPosition);
            }
        } else {
            viewPager.setAdapter(adapter);
            viewPager.setCurrentItem(currentPosition);
        }*/

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    String test = String.valueOf(buildListOfYears().get(position));
                    tab.setText(String.valueOf(buildListOfYears().get(position)));
                }
        ).attach();

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentPosition = position;
            }
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("currentPosition", currentPosition);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            currentPosition = savedInstanceState.getInt("currentPosition", -1);
        }
    }

    private ArrayList<Integer> buildListOfYears() {
        if (listOfYears.isEmpty()) {
            for (int i = 1945; i <= 2045; i++) {
                listOfYears.add(i);
            }
        }
        return (ArrayList<Integer>) listOfYears;
    }

    private void selectFirstDisplayedYear() {
        int defaultYear = new DateTime().getYear();
        int desiredYearPosition = adapter.getYearPosition(defaultYear);
        if (desiredYearPosition > -1) {
            currentPosition = desiredYearPosition;
            viewPager.setCurrentItem(desiredYearPosition);
        }
    }
}