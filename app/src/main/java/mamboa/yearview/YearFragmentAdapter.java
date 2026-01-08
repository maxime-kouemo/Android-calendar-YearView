package mamboa.yearview;

import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.util.SparseArray;

import java.util.List;

public class YearFragmentAdapter extends FragmentPagerAdapter {

    public static int pos = 0;

    private SparseArray<YearFragment> myFragments = new SparseArray<YearFragment>();
    private List<Integer> myYears;

    public YearFragmentAdapter(FragmentManager fm, List<Integer> myYears) {
        super(fm);
        this.myYears = myYears;
    }

    @Override
    public YearFragment getItem(int position) {
        int year = myYears.get(position);
        YearFragment fragment = YearFragment.newInstance(year);

        myFragments.put(position, fragment);
        return fragment;
    }

    @Override
    public int getCount() {
        return myYears.size();
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return position > -1 ? ""+this.myYears.get(position) : "";
    }

    public int getYearPosition(int year){
        return myYears.indexOf(year);
    }
}
