package com.mamboa;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.mamboa.yearview.core.utils.Utils;
import com.mamboa.yearview.legacy.YearView;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class YearFragment extends Fragment implements YearView.MonthGestureListener {
    private final static String YEAR_TAG = "year";
    private YearView yearView;
    private int year = 2018;

    public static YearFragment newInstance(int year) {

        final YearFragment mf = new YearFragment();

        final Bundle args = new Bundle();
        args.putInt(YEAR_TAG, year);
        mf.setArguments(args);

        return mf;
    }

    public YearFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null)
            this.year = getArguments().getInt(YEAR_TAG, this.year);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate and locate the main ImageView
        final View v = inflater.inflate(R.layout.year_fragment, container, false);

        yearView = v.findViewById(R.id.yearView);
        yearView.setMonthGestureListener(this);
        yearView.setYear(this.year);
        
        // Use colors from colors.xml based on year for variety
        int colorVariant = (this.year % 6) + 1;
        
        switch (colorVariant) {
            case 1:
                yearView.setDayNameTextColor(R.color.demo_day_name_1);
                yearView.setMonthNameTextColor(R.color.demo_month_name_1);
                yearView.setWeekendTextColor(R.color.demo_weekend_1);
                break;
            case 2:
                yearView.setDayNameTextColor(R.color.demo_day_name_2);
                yearView.setMonthNameTextColor(R.color.demo_month_name_2);
                yearView.setWeekendTextColor(R.color.demo_weekend_2);
                break;
            case 3:
                yearView.setDayNameTextColor(R.color.demo_day_name_3);
                yearView.setMonthNameTextColor(R.color.demo_month_name_3);
                yearView.setWeekendTextColor(R.color.demo_weekend_3);
                break;
            case 4:
                yearView.setDayNameTextColor(R.color.demo_day_name_4);
                yearView.setMonthNameTextColor(R.color.demo_month_name_4);
                yearView.setWeekendTextColor(R.color.demo_weekend_4);
                break;
            case 5:
                yearView.setDayNameTextColor(R.color.demo_day_name_5);
                yearView.setMonthNameTextColor(R.color.demo_month_name_5);
                yearView.setWeekendTextColor(R.color.demo_weekend_5);
                break;
            case 6:
                yearView.setDayNameTextColor(R.color.demo_day_name_6);
                yearView.setMonthNameTextColor(R.color.demo_month_name_6);
                yearView.setWeekendTextColor(R.color.demo_weekend_6);
                break;
        }

        if (this.year % 2 == 0)
            yearView.setMonthTitleGravity(Utils.getRandomTitleGravity());

        return v;
    }

    @Override
    public void onMonthClick(long timeInMillis) {
        DateTime dateTime = new DateTime(timeInMillis);
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM");
        Toast.makeText(getContext(), formatter.print(dateTime), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMonthLongClick(long timeInMillis) {
        DateTime dateTime = new DateTime(timeInMillis);
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM");
        Toast.makeText(getContext(), formatter.print(dateTime), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDayClick(long timeInMillis) {
        DateTime dateTime = new DateTime(timeInMillis);
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd");
        Toast.makeText(getContext(), "Clicked day: " + formatter.print(dateTime), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDayLongClick(long timeInMillis) {
        DateTime dateTime = new DateTime(timeInMillis);
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd");
        Toast.makeText(getContext(), "Long clicked day: " + formatter.print(dateTime), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        if (yearView != null)
            yearView.setMonthGestureListener(null);

        super.onDestroyView();
    }
}
