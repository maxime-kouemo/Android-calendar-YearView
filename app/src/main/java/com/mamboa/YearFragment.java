package com.mamboa;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.mamboa.yearview.core.datetime.CalendarDate;
import com.mamboa.yearview.core.datetime.timeproviders.ICalendarDateTimeProvider;
import com.mamboa.yearview.core.datetime.timeproviders.KotlinxTimeProvider;
import com.mamboa.yearview.core.utils.Utils;
import com.mamboa.yearview.legacy.YearView;

public class YearFragment extends Fragment implements YearView.MonthGestureListener {
    private final static String YEAR_TAG = "year";
    private final ICalendarDateTimeProvider dateTimeProvider = new KotlinxTimeProvider();
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

    // The callbacks below take CalendarDate directly. The epoch-millis overloads are
    // gone: every event used to fire both variants, so a listener implementing both was
    // notified twice, and the millis form forced a round trip through a timestamp whose
    // time zone had to match the one the view used.
    //
    // Note this class overrides only the five callbacks it cares about. That works from
    // Java because :legacy is compiled with -Xjvm-default=all, which emits the interface
    // bodies as real Java default methods.

    @Override
    public void onMonthClick(@NonNull CalendarDate date) {
        String formatted = dateTimeProvider.format(date, "yyyy-MM", java.util.Locale.ROOT);
        Toast.makeText(getContext(), formatted, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMonthLongClick(@NonNull CalendarDate date) {
        String formatted = dateTimeProvider.format(date, "yyyy-MM", java.util.Locale.ROOT);
        Toast.makeText(getContext(), formatted, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDayClick(@NonNull CalendarDate date) {
        String formatted = dateTimeProvider.format(date, "yyyy-MM-dd", java.util.Locale.ROOT);
        Toast.makeText(getContext(), "Clicked day: " + formatted, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDayLongClick(@NonNull CalendarDate date) {
        String formatted = dateTimeProvider.format(date, "yyyy-MM-dd", java.util.Locale.ROOT);
        Toast.makeText(getContext(), "Long clicked day: " + formatted, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRangeSelected(@NonNull CalendarDate start, @NonNull CalendarDate end) {
        String startStr = dateTimeProvider.format(start, "yyyy-MM-dd", java.util.Locale.ROOT);
        String endStr = dateTimeProvider.format(end, "yyyy-MM-dd", java.util.Locale.ROOT);
        Toast.makeText(getContext(),
                "Range selected: " + startStr + " to " + endStr,
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        if (yearView != null)
            yearView.setMonthGestureListener(null);

        super.onDestroyView();
    }
}
