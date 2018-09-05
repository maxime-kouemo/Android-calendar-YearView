package mamboa.yearview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.graphics.ColorUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;

import java.util.GregorianCalendar;

/**
 * Created by mamboa on 9/4/2018.
 */
public class YearView extends View{
    private int mYear = 2018;
    private int verticalSpacing = 5;
    private int horizontalSpacing = 5;
    private int columns = 2;
    private int rows = 6;
    private int mWidth = 10;
    private int mHeight = 10;
    private int marginBelowMonthName = 5;
    private boolean mSundayFirst = false;
    private int titleGravity = 1;
    private int firstDayOfWeek = 1; //since we are using Joda-Time, it goes from Monday: 1 to Sunday: 7
    private Context mContext;
    private final static int numDays = 7;
    private int selectionColor = Color.BLUE;

    private Paint dayNumberPaint;
    private Paint todayTodayPaint;
    private Paint selectionPaint;
    private Paint monthNamePaint;

    private Rect[] monthBlocks = null;
    private Rect[] originalMonthBlocks = null; //used for the visual click animation
    GestureDetector mGestureDetector;

    private final static int CENTER = 1;
    private final static int LEFT = 2;
    private final static int RIGHT = 3;
    private final static int START = 2;
    private final static int END = 3;
    private int mOnDownDelay = 0;

    private final static float SELECTION_STROKE = 2.f;
    private final static int SELECTION_ALPHA = 255/2;

    private int selectedMonthID = -1;

    private Handler handler;

    private boolean isClearSelectionLaunched = false;
    private final Runnable clearSelectionRunnable = new Runnable() {
        @Override
        public void run() {
            selectedMonthID = -1;
            isClearSelectionLaunched = false;
            invalidate();
        }
    };

    public YearView(Context context) {
        super(context);
    }

    public YearView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.YearView);
        try{
            mYear = a.getInteger(R.styleable.YearView_current_year, mYear);
            rows = a.getInteger(R.styleable.YearView_rows, rows);
            columns = a.getInteger(R.styleable.YearView_columns, columns);
            verticalSpacing = a.getInteger(R.styleable.YearView_vertical_spacing, verticalSpacing);
            horizontalSpacing = a.getInteger(R.styleable.YearView_horizontal_spacing, horizontalSpacing);
            titleGravity = a.getInteger(R.styleable.YearView_title_gravity, titleGravity);
            marginBelowMonthName = a.getInteger(R.styleable.YearView_margin_below_month_name,marginBelowMonthName);
            selectionColor = a.getColor(R.styleable.YearView_selection_color, selectionColor);
            firstDayOfWeek = a.getInteger(R.styleable.YearView_firstDayOfWeek,firstDayOfWeek);
        }
        catch (Exception e){

        }

        mOnDownDelay = ViewConfiguration.getTapTimeout();
        dayNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dayNumberPaint.setColor(Color.BLACK);
        dayNumberPaint.setTextSize(context.getResources().getDimensionPixelSize(R.dimen.year_view_day_text_size));
        dayNumberPaint.setTextAlign(Paint.Align.LEFT);

        todayTodayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        todayTodayPaint.setColor(Color.RED);
        todayTodayPaint.setTextSize(context.getResources().getDimensionPixelSize(R.dimen.year_view_day_text_size));
        todayTodayPaint.setTextAlign(Paint.Align.LEFT);

        monthNamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        monthNamePaint.setColor(Color.BLACK);
        monthNamePaint.setTextSize(context.getResources().getDimensionPixelSize(R.dimen.year_view_month_text_size));
        monthNamePaint.setTextAlign(Paint.Align.LEFT);
        monthNamePaint.setTypeface(Typeface.DEFAULT_BOLD);

        selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectionPaint.setColor(ColorUtils.setAlphaComponent(selectionColor, SELECTION_ALPHA));
        selectionPaint.setStrokeJoin(Paint.Join.ROUND);
        selectionPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        selectionPaint.setStrokeWidth(SELECTION_STROKE);

        mGestureDetector = new GestureDetector(context, new CalendarGestureListener());
        mSundayFirst = true;

        handler = new Handler();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        mWidth = width;
        mHeight = height;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // View is now attached
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        doCleanup();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawMonths(canvas);
        drawSelection(canvas);
    }

    private void drawMonths(Canvas canvas){
        splitViewInBlocks();
        DateTime dateTime = new DateTime().withDate(mYear, 2, 1).withHourOfDay(12);
        DateTime nowTime = new DateTime();

        for(int i = 0; i <= 11; i++){
            DateTime monthTime = dateTime.withMonthOfYear(i+1);
            int dayOfWeek = dateTime.withMonthOfYear(i+1).dayOfWeek().get();
            if(firstDayOfWeek != DateTimeConstants.SUNDAY)
                dayOfWeek -= firstDayOfWeek;

            drawAMonth(canvas,i,dayOfWeek, monthTime.dayOfMonth().getMaximumValue(), nowTime.getYear() == monthTime.getYear() &&
                    nowTime.getDayOfYear() == monthTime.getDayOfYear() ? monthTime.dayOfMonth().get() : -1, monthTime.monthOfYear().getAsText(Utils.getCurrentLocale(mContext)));
        }
    }

    private void drawSelection(Canvas canvas){
        if(selectedMonthID > -1){
            canvas.drawRect(
                    originalMonthBlocks[selectedMonthID].left,
                    originalMonthBlocks[selectedMonthID].top,
                    originalMonthBlocks[selectedMonthID].right,
                    originalMonthBlocks[selectedMonthID].bottom,
                    selectionPaint
            );

            if(!isClearSelectionLaunched) {
                handler.postDelayed(clearSelectionRunnable, mOnDownDelay *2);
                isClearSelectionLaunched = true;
            }
        }
    }

    /**
     * Draws the label name of a month
     * @param canvas the canvas' reference
     * @param index the index of the month ([0,11])
     * @param monthName the String name of the month
     */
    private void drawMonthName(Canvas canvas,int index, String monthName){
        Paint paint = new Paint(monthNamePaint);
        Rect textBounds = new Rect();
        paint.getTextBounds(monthName, 0, 1, textBounds);

        int xStart = monthBlocks[index].left; //START or LEFT
        int yValue = monthBlocks[index].top + textBounds.height();
        int width = (int)paint.measureText(monthName);
        switch (titleGravity){
            case CENTER:
                xStart = (monthBlocks[index].left + monthBlocks[index].right)/2 - width /2;
            break;
            case END: //or RIGHT
                xStart = monthBlocks[index].right - width - horizontalSpacing/2;
                break;
        }
        canvas.drawText(monthName+"", xStart, yValue, paint);

        //shift the rest below
        int left =  monthBlocks[index].left;
        int top = monthBlocks[index].top + textBounds.height()*2 + marginBelowMonthName;
        int right = monthBlocks[index].right;
        int bottom = monthBlocks[index].bottom;
        monthBlocks[index] = new Rect(left, top, right, bottom);
    }

    /**
     * Draws a month
     * @param canvas
     * @param index the index of the month as in the list of monthBlock
     * @param firstDay the first day of the month
     * @param daysInMonth the maximum number of days in that month
     * @param todayID is -1 if the current month doesn't contain today, otherwise returns the id of today
     *                in the month
     * @param monthName the string name of a month
     */
    private void drawAMonth(Canvas canvas, int index, int firstDay, int daysInMonth,int todayID, String monthName){
        drawMonthName(canvas, index, monthName);

        int xUnit = monthBlocks[index].width()/numDays;
        int yUnit = monthBlocks[index].height()/numDays;

        int todaysId = 0;
        if(todayID > -1)
            todaysId = todayID;

        int curId = 1 - firstDay;
        for (int y = 0; y <= 7; y++) {
            for (int x = 0; x < 7; x++) {
                int xValue = monthBlocks[index].left + (xUnit * x);
                int yValue = monthBlocks[index].top + (yUnit * y);

                //draw day titles
                if(y == 0){
                    DateTime dateTime = new DateTime()
                            .withYear(mYear)
                            .withMonthOfYear(index + 1)
                            .withDayOfWeek(getDayIndex(x));
//                            .withDayOfWeek(x+1);

                    DateTime.Property pDoW = dateTime.dayOfWeek();
                    String dayName = pDoW.getAsShortText(Utils.getCurrentLocale(mContext)).substring(0,1);
                    canvas.drawText(dayName+"", xValue, yValue, dayNumberPaint);
                }
                else {
                    if (curId >= 1 && curId <= daysInMonth) {

                        if (curId == todaysId) { //if today
                            canvas.drawText(curId + "", xValue, yValue, todayTodayPaint);
//                        canvas.drawCircle(x * dayWidth - dayWidth / dividerConstant, y * dayWidth - dayWidth / dividerConstant, dayWidth * 0.41f, todayCirclePaint)
                        }
                        else
                            canvas.drawText(curId + "", xValue, yValue, dayNumberPaint);
                    }
                    curId++;
                }

            }
        }
    }

    /**
     * Returns the day index according to DateTimeConstants Joda-Time, taking into account what the user
     * wants as first day of the week
     * @param position of the day ([0;7[)
     * @return
     */
    private int getDayIndex(int position){
        return firstDayOfWeek == DateTimeConstants.MONDAY || position + firstDayOfWeek <= 7 ?
                firstDayOfWeek + position :
                (firstDayOfWeek + position)%7;
    }

    /**
     * Split the view in 12 blocks. Each block will be used to draw a month inside
     */
    private void splitViewInBlocks(){
        monthBlocks = new Rect[12];
        originalMonthBlocks = new Rect[12];
        int k = 0;
        for(int i = 0; i < rows; i++){
            for(int j = 0; j < columns; j++){
                int currentHorizontalSpacing = j == 0 ? horizontalSpacing : horizontalSpacing/2;
                int currentVerticalSpacing = i == 0 ? verticalSpacing : verticalSpacing/2;

                int left = currentHorizontalSpacing + (j * mWidth/columns);
                int top = currentVerticalSpacing + (i * mHeight/rows);
                int right = (j + 1) * mWidth/columns - currentHorizontalSpacing;
                int bottom = (i + 1) * mHeight/rows - currentVerticalSpacing;
                monthBlocks[k] = new Rect(left,top,right,bottom);
                originalMonthBlocks[k] = new Rect(left,top,right,bottom);
                k++;
            }
        }
    }

    private void doCleanup(){
        if(handler != null)
            handler.removeCallbacksAndMessages(null);
    }

    MonthGestureListener monthGestureListener;

    public void setMonthGestureListener(MonthGestureListener monthGestureListener) {
        this.monthGestureListener = monthGestureListener;
    }

    public interface MonthGestureListener{
        //first day of the month in millis
        void onClickMonth(long timeInMillis);
    }

    /**
     * Get the firstDay at first hour of the clicked month
     * @param x
     * @param y
     * @return
     */
    private long  getClickedMonth(int x, int y){
        for(int i = 0; i < monthBlocks.length; i ++){
            if(monthBlocks[i].contains(x,y)){
                DateTime dayOfWeek = new DateTime().withYear(mYear)
                        .withMonthOfYear(i + 1)
                        .withDayOfMonth(1)
                        .withHourOfDay(1);
                selectedMonthID = i;
                return dayOfWeek.getMillis();
            }
        }
        return 0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mGestureDetector.onTouchEvent(ev);
    }

    class CalendarGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent ev) {
            if(monthGestureListener != null) {
                long timeInMillis = getClickedMonth((int)ev.getX(), (int)ev.getY());
                monthGestureListener.onClickMonth(timeInMillis);
                invalidate();
            }
            return true;
        }

        @Override
        public void onLongPress(MotionEvent ev) { }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            return true;
        }

        @Override
        public boolean onDown(MotionEvent ev) {
            return true;
        }
    }
}
