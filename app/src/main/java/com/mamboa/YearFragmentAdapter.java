package com.mamboa;

import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.List;

public class YearFragmentAdapter extends FragmentStateAdapter {

    public static int pos = 0;

    private final SparseArray<YearFragment> myFragments = new SparseArray<>();
    private final List<Integer> myYears;

    public YearFragmentAdapter(FragmentActivity fa, List<Integer> myYears) {
        super(fa);
        this.myYears = myYears;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        int year = myYears.get(position);
        YearFragment fragment = YearFragment.newInstance(year);

        myFragments.put(position, fragment);
        return fragment;
    }

    @Override
    public int getItemCount() {
        return myYears.size();
    }

    public int getYearPosition(int year){
        return myYears.indexOf(year);
    }
}