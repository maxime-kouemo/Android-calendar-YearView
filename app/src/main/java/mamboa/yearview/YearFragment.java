package mamboa.yearview;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class YearFragment extends Fragment implements YearView.MonthGestureListener {
    private final static String YEAR_TAG = "year";
    private YearView yearView;
    private TextView txtYear;
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
        if(getArguments() != null)
            this.year = getArguments().getInt(YEAR_TAG, this.year);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate and locate the main ImageView
        final View v = inflater.inflate(R.layout.year_fragment, container, false);

        txtYear = (TextView) v.findViewById(R.id.txtYear);
        yearView = (YearView) v.findViewById(R.id.yearView);
        yearView.setMonthGestureListener(this);
        txtYear.setText("" + this.year);
        txtYear.setTextColor(Utils.getRandomColor());
        yearView.setYear(this.year);
        yearView.setDayNameTextColor(Utils.getRandomColor());
        yearView.setMonthNameTextColor(txtYear.getTextColors().getDefaultColor());
        yearView.setWeekendTextColor(Utils.getRandomColor());

        if(this.year % 2 == 0)
            yearView.setMonthTitleGravity(YearView.TitleGravity.CENTER);

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
    public void onDestroyView(){
       if(yearView != null)
           yearView.setMonthGestureListener(null);

       super.onDestroyView();
    }
}
