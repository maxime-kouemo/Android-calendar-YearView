package mamboa.yearview;

import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import org.joda.time.DateTime;

import java.util.ArrayList;

/**
 * Created by mamboa on 9/4/2018.
 */
public class MainActivity extends AppCompatActivity{
    private YearFragmentAdapter yearFragmentAdapter;
    private ViewPager viewPager;
    private final static int MIN_YEAR = 1945;
    private final static int MAX_YEAR = 2045;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewPager = (ViewPager) findViewById(R.id.viewPager);
        yearFragmentAdapter = new YearFragmentAdapter(getSupportFragmentManager(), buildListOfYears());
        viewPager.setAdapter(yearFragmentAdapter);

        selectFirstDisplayedYear();
    }

    private ArrayList<Integer> buildListOfYears() {
        ArrayList<Integer> listYears = new ArrayList<>();
        for(int i = MIN_YEAR ; i <= MAX_YEAR;  i++ ){
            listYears.add(i);
        }
        return listYears;
    }

    /**
     * The default displayed year is the current one
     */
    private void selectFirstDisplayedYear(){
        if(yearFragmentAdapter != null){
            int defaultYear = new DateTime().getYear();
            int desiredYearPosition = yearFragmentAdapter.getYearPosition(defaultYear);
            if(desiredYearPosition > -1)
                viewPager.setCurrentItem(desiredYearPosition);
        }
    }
}
