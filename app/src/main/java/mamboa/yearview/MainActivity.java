package mamboa.yearview;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity implements YearView.MonthGestureListener {
    private YearView yearView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        yearView = (YearView) findViewById(R.id.yearView);
        yearView.setMonthGestureListener(this);
    }

    @Override
    public void onClickMonth(long timeInMillis) {

    }
}
