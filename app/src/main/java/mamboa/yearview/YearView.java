package mamboa.yearview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v4.graphics.ColorUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.LocalDate;

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
    private int todayTextColor = Color.WHITE;
    private int todayBackgroundColor = Color.RED;
    private int textColor = Color.BLACK;
    private int weekendColor = Color.BLACK;
    private int dayNameColor = Color.BLACK;
    private int monthNameColor = Color.BLACK;
    private int todayBackgroundShape = SHAPE_CIRCLE;
    private int monthNameFontType = MONTH_NAME_FONT_NORMAL;
    private int dayNameFontType = DAY_NAME_FONT_NORMAL;
    private int todayFontType = TODAY_FONT_NORMAL;
    private int weekendFontType = WEEKEND_NAME_FONT_NORMAL;

    private int[] weekendDays = null;

    private int todayBackgroundRadius = 5;

    public final static int MONTH_NAME_FONT_NORMAL = 1;
    public final static int MONTH_NAME_FONT_BOLD = 2;
    public final static int MONTH_NAME_FONT_ITALIC = 3;
    public final static int MONTH_NAME_FONT_BOLD_ITALIC = 4;

    public final static int DAY_NAME_FONT_NORMAL = 1;
    public final static int DAY_NAME_FONT_BOLD = 2;
    public final static int DAY_NAME_FONT_ITALIC = 3;
    public final static int DAY_NAME_FONT_BOLD_ITALIC = 4;

    public final static int WEEKEND_NAME_FONT_NORMAL = 1;
    public final static int WEEKEND_NAME_FONT_BOLD = 2;
    public final static int WEEKEND_NAME_FONT_ITALIC = 3;
    public final static int WEEKEND_NAME_FONT_BOLD_ITALIC = 4;

    public final static int TODAY_FONT_NORMAL = 1;
    public final static int TODAY_FONT_BOLD = 2;
    public final static int TODAY_FONT_ITALIC = 3;
    public final static int TODAY_FONT_BOLD_ITALIC = 4;

    private Paint dayNumberPaint;
    private Paint todayTodayTextPaint;
    private Paint todayTodayBackgroundPaint;
    private Paint selectionPaint;
    private Paint monthNamePaint;
    private Paint weekendDayPaint;
    private Paint dayNamePaint;

    //if true: a font type ( and a text color)  applied to the name of the days in the week,
    // will also be applied to the name of the days representing the weekend
    private boolean dayNameTranscendsWeekend = false;

    private Rect[] monthBlocks = null;
    private Rect[] originalMonthBlocks = null; //used for the visual click animation
    private GestureDetector mGestureDetector;

    public final static int SHAPE_CIRCLE = 1;
    public final static int SHAPE_SQUARE = 2;

    public final static int TITLE_GRAVITY_CENTER = 1;
    public final static int TITLE_GRAVITY_LEFT = 2;
    public final static int TITLE_GRAVITY_RIGHT = 3;
    public final static int TITLE_GRAVITY_START = 2;
    public final static int TITLE_GRAVITY_END = 3;
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
            textColor  = a.getColor(R.styleable.YearView_text_color, textColor);
            weekendColor  = a.getColor(R.styleable.YearView_text_color_weekend, weekendColor);
            firstDayOfWeek = a.getInteger(R.styleable.YearView_firstDayOfWeek,firstDayOfWeek);
            todayTextColor = a.getColor(R.styleable.YearView_today_text_color, todayTextColor);
            todayBackgroundColor = a.getColor(R.styleable.YearView_today_background_color, todayBackgroundColor);
            todayBackgroundRadius = a.getInteger(R.styleable.YearView_today_background_radius, todayBackgroundRadius);
            dayNameColor = a.getInteger(R.styleable.YearView_day_name_color, dayNameColor);
            monthNameColor = a.getInteger(R.styleable.YearView_month_name_color, monthNameColor);
            todayBackgroundShape = a.getInteger(R.styleable.YearView_today_background_shape, todayBackgroundShape);
            monthNameFontType = a.getInteger(R.styleable.YearView_month_name_font_type, monthNameFontType);
            dayNameFontType = a.getInteger(R.styleable.YearView_week_name_font_type, dayNameFontType);
            todayFontType = a.getInteger(R.styleable.YearView_today_font_type, todayFontType);
            weekendFontType = a.getInteger(R.styleable.YearView_weekend_font_type, weekendFontType);
            dayNameTranscendsWeekend = a.getBoolean(R.styleable.YearView_name_week_transcend_weekend, dayNameTranscendsWeekend);

            //here we get the week end days defined in the xml's "app:weekend_days=..."
            int weekendDaysID = a.getResourceId(R.styleable.YearView_weekend_days, 0);
            if(weekendDaysID > 0)
                weekendDays = a.getResources().getIntArray(weekendDaysID);
        }
        catch (Exception e){

        }

        mOnDownDelay = ViewConfiguration.getTapTimeout();

        setupDayNumberPaint();
        setupWeekendPaint();
        setupDayNamePaint();
        setupMonthNamePaint();
        setupTodayTextPaint();
        setupTodayBackgroundPaint();
        setupSelectionPaint();

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
        if(mContext != null)
            mContext = null;
        doCleanup();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        splitViewInBlocks(); //-1
        drawMonths(canvas);  //-2
        drawSelection(canvas); //-3
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superStae = super.onSaveInstanceState();

       //save here
        return superStae;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
        //restore here
        requestLayout();
    }

    /**
     * Split the view in a grid of colums x rows having 12 blocks. Each block will be used to draw a month inside
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

    private void drawMonths(Canvas canvas){
        DateTime dateTime = new DateTime().withDate(mYear, 2, 1).withHourOfDay(12);

        for(int i = 0; i <= 11; i++){
            DateTime monthTime = dateTime.withMonthOfYear(i+1);
            int dayOfWeek = dateTime.withMonthOfYear(i+1).dayOfWeek().get();
            if(firstDayOfWeek != DateTimeConstants.SUNDAY)
                dayOfWeek -= firstDayOfWeek;

            drawAMonth(canvas,i,dayOfWeek, monthTime.dayOfMonth().getMaximumValue(), monthTime.monthOfYear().getAsText(Utils.getCurrentLocale(mContext)));
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

        int xStart = monthBlocks[index].left; //TITLE_GRAVITY_START or TITLE_GRAVITY_LEFT
        int yValue = monthBlocks[index].top + textBounds.height();
        int width = (int)paint.measureText(monthName);
        switch (titleGravity){
            case TITLE_GRAVITY_CENTER:
                xStart = (monthBlocks[index].left + monthBlocks[index].right)/2 - width /2;
            break;
            case TITLE_GRAVITY_END: //or TITLE_GRAVITY_RIGHT
                xStart = monthBlocks[index].right - width - horizontalSpacing;
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
     * @param month the index of the month as in the list of monthBlock
     * @param firstDay the first day of the month
     * @param daysInMonth the maximum number of days in that month
     * @param monthName the string name of a month
     */
    private void drawAMonth(Canvas canvas, int month, int firstDay, int daysInMonth, String monthName){
        drawMonthName(canvas, month, monthName);

        int xUnit = monthBlocks[month].width()/numDays;
        int yUnit = monthBlocks[month].height()/numDays;

        int dayOfMonth = 1 - firstDay;
        for (int y = 0; y <= 7; y++) {
            for (int x = 0; x < 7; x++) {
                int xValue = monthBlocks[month].left + (xUnit * x);
                int yValue = monthBlocks[month].top + (yUnit * y);

                //draw day titles
                if(y == 0){
                    DateTime dateTime = new DateTime()
                            .withYear(mYear)
                            .withMonthOfYear(month + 1)
                            .withDayOfWeek(getDayIndex(x));

                    DateTime.Property pDoW = dateTime.dayOfWeek();
                    String dayName = pDoW.getAsShortText(Utils.getCurrentLocale(mContext)).substring(0,1);

                    //weekend days
                    if(isDayPresentInWeekendDays(dateTime.getDayOfWeek())  && !dayNameTranscendsWeekend){
                        //todo: add background for title ?
                        canvas.drawText(dayName+"", xValue, yValue, weekendDayPaint);
                    }
                    else {
                        canvas.drawText(dayName+"", xValue, yValue, dayNamePaint);
                    }
                }

                //draw day numbers
                else {

                    if (dayOfMonth >= 1 && dayOfMonth <= daysInMonth) {
                        if (isToday(month, dayOfMonth)) {
                            switch (todayBackgroundShape){
                                case SHAPE_CIRCLE:
                                    drawTodayCircle(canvas, dayOfMonth, xValue, yValue, todayBackgroundRadius);
                                    break;
                                case SHAPE_SQUARE:
                                    drawTodaySquare(canvas, dayOfMonth, xValue, yValue, todayBackgroundRadius);
                                    break;
                                default:
                                    drawTodaySquare(canvas, dayOfMonth, xValue, yValue, todayBackgroundRadius);
                            }


                        }
                        else {
                            if(isWeekend(month,dayOfMonth)) {
                                //todo: draw a color to the background ?
                                canvas.drawText(dayOfMonth + "", xValue, yValue, weekendDayPaint);
                            }
                            else {
                                //todo: draw a color to the background ?
                                canvas.drawText(dayOfMonth + "", xValue, yValue, dayNumberPaint);
                            }
                        }
                    }
                    dayOfMonth++;
                }
            }
        }
    }

    /**
     * Draws a square around a given day
     * @param canvas the canvas
     * @param dayOfMonth the day of the month
     * @param xValue the x of where to draw the day of the month's value
     * @param yValue the y of where to draw the day of the month's value
     * @param margin around the month value
     */
    private void drawTodaySquare(Canvas canvas, int dayOfMonth, int xValue, int yValue, int margin) {
            RectF boxRect = new RectF();
            Rect bounds = new Rect();
            todayTodayTextPaint.getTextBounds(dayOfMonth + "", 0, (dayOfMonth + "").length(), bounds);

            boxRect.left = xValue - margin;
            boxRect.top = yValue - bounds.height()*2/3 - margin;
            boxRect.right = xValue + bounds.width() + margin;
            boxRect.bottom = yValue + margin;

            canvas.drawRect(boxRect, todayTodayBackgroundPaint);
            canvas.drawText(dayOfMonth + "", xValue, yValue, todayTodayTextPaint);
    }

    /**
     * Draws a circle around a given day
     * @param canvas the canvas
     * @param dayOfMonth the day of the month
     * @param xValue the x of where to draw the day of the month's value
     * @param yValue the y of where to draw the day of the month's value
     * @param margin radius the month value
     */
    private void drawTodayCircle(Canvas canvas, int dayOfMonth, int xValue, int yValue, int margin){
        Rect bounds = new Rect();
        todayTodayTextPaint.getTextBounds(dayOfMonth + "", 0, (dayOfMonth + "").length(), bounds);

        int centerX = xValue + bounds.width()/2;
        int centerY = (int) ((yValue - bounds.height()/2) - ((todayTodayTextPaint.descent() + todayTodayTextPaint.ascent())/3));

        //we make sure the circle will always surround the value of the digits
        int radius = (bounds.width() > bounds.height() ? bounds.width() : bounds.height() + margin) /2;

        canvas.drawCircle(centerX, centerY , radius, todayTodayBackgroundPaint);
        canvas.drawText(dayOfMonth + "", xValue, yValue, todayTodayTextPaint);
    }

    /**
     * Tells if the current date defined by the month and the day of the {@link YearView#mYear) refers
     * to the real life current date
     * @param month whose index between [0 - 11] is given
     * @param dayOfMonth whose index is given
     * @return true if we are today false otherwise
     */
    private boolean isToday(int month, int dayOfMonth){
        DateTime dateTime = new DateTime()
                .withYear(mYear)
                .withMonthOfYear(month + 1) //the variable monthBlocks goes from 0 to 11 while Joda Time's months go from 1 to 12
                .withDayOfMonth(dayOfMonth);

        return dateTime.toLocalDate().equals(new LocalDate());
    }

    /**
     * Determine if a given day in a given mon is a weekend day. Notice that here we consider that
     * a weekend day is customizable
     * @param month the current month
     * @param dayOfMonth the day of the mont
     * @return it the day is considered a week end day
     */
    private boolean isWeekend(int month, final int dayOfMonth){
        DateTime dateTime = new DateTime()
                .withYear(mYear)
                .withMonthOfYear(month + 1) //the variable monthBlocks goes from 0 to 11 while Joda Time's months go from 1 to 12
                .withDayOfMonth(dayOfMonth);

        return isDayPresentInWeekendDays(dateTime.getDayOfWeek());
    }

    /**
     * Detects if a day is present in the list of custom weekend days
     * @param dayIndex defines a day according to {@link org.joda.time.DateTimeConstants}, from SUNDAY
     *                 to SATURDAY
     * @return if a day is present in the list of weekend days
     */
    private boolean isDayPresentInWeekendDays(int dayIndex){
        if(weekendDays == null ||  weekendDays.length == 0)
            return false;
        for(int i = 0; i < weekendDays.length; i++){
            if(weekendDays[i] == dayIndex)
                return true;
        }

        return false;
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
     * Set the weekend days according to {@link org.joda.time.DateTimeConstants}.
     * Example: newWeekendDays = {DateTimeConstants.SATURDAY, DateTimeConstants.SUNDAY}
     * The order of the days doesn't count
     * @param newWeekendDays
     */
    public void setWeekendDays(int[] newWeekendDays){
        weekendDays = newWeekendDays;
        invalidate();
    }

    public void setYear(int mYear) {
        this.mYear = mYear;
        invalidate();
    }

    public void setVerticalSpacing(int verticalSpacing) {
        this.verticalSpacing = verticalSpacing;
        invalidate();
    }

    public void setHorizontalSpacing(int horizontalSpacing) {
        this.horizontalSpacing = horizontalSpacing;
        invalidate();
    }

    public void setColumns(int columns) {
        this.columns = columns;
        invalidate();
    }

    public void setRows(int rows) {
        this.rows = rows;
        invalidate();
    }

    public void setSelectionColor(int selectionColor) {
        this.selectionColor = selectionColor;
        setupSelectionPaint();
        invalidate();
    }

    public void setTodayTextColor(int todayTextColor) {
        this.todayTextColor = todayTextColor;
        setupTodayTextPaint();
        invalidate();
    }

    public void setTodayBackgroundColor(int todayBackgroundColor) {
        this.todayBackgroundColor = todayBackgroundColor;
        setupTodayBackgroundPaint();
        invalidate();
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
        setupDayNumberPaint();
        invalidate();
    }

    public void setWeekendColor(int weekendColor) {
        this.weekendColor = weekendColor;
        setupWeekendPaint();
        invalidate();
    }

    public void setTodayBackgroundRadius(int todayBackgroundRadius) {
        this.todayBackgroundRadius = todayBackgroundRadius;
        setupTodayBackgroundPaint();
        invalidate();
    }

    public void setDayNameColor(int dayNameColor) {
        this.dayNameColor = dayNameColor;
        setupDayNamePaint();
        invalidate();
    }

    public void setMonthNameColor(int monthNameColor) {
        this.monthNameColor = monthNameColor;
        setupMonthNamePaint();
        invalidate();
    }

    /**
     * Should be between {@link YearView#TITLE_GRAVITY_CENTER}, {@link YearView#TITLE_GRAVITY_START} or {@link YearView#TITLE_GRAVITY_LEFT},
     * {@link YearView#TITLE_GRAVITY_END} or {@link YearView#TITLE_GRAVITY_RIGHT}
     * @param titleGravity the new title gravity
     */
    public void setTitleGravity(int titleGravity) {
        this.titleGravity = titleGravity;
    }

    /**
     * Should be between circle {@link YearView#SHAPE_CIRCLE} and square {@link YearView#SHAPE_SQUARE}
     * @param todayBackgroundShape the new background shape
     */
    public void setTodayBackgroundShape(int todayBackgroundShape){
        this.todayBackgroundShape = todayBackgroundShape;
        invalidate();
    }

    /**
     * Should be between bold {@link YearView#TODAY_FONT_BOLD}, italic{@link YearView#TODAY_FONT_ITALIC},
     * bold_italic{@link YearView#TODAY_FONT_BOLD_ITALIC} and normal{@link YearView#TODAY_FONT_NORMAL}
     * @param todayFontType the new font type for today
     */
    public void setTodayFontType(int todayFontType){
        this.todayFontType = todayFontType;
        setupTodayTextPaint();
        invalidate();
    }

    /**
     * Should be between bold {@link YearView#MONTH_NAME_FONT_BOLD}, italic{@link YearView#MONTH_NAME_FONT_ITALIC},
     * bold_italic{@link YearView#MONTH_NAME_FONT_BOLD_ITALIC} and normal{@link YearView#MONTH_NAME_FONT_NORMAL}
     * @param monthNameFontType the new font type for the month
     */
    public void setMonthNameFontType(int monthNameFontType){
        this.monthNameFontType = todayFontType;
        setupMonthNamePaint();
        invalidate();
    }

    /**
     * Should be between bold {@link YearView#DAY_NAME_FONT_BOLD}, italic{@link YearView#DAY_NAME_FONT_ITALIC},
     * bold_italic{@link YearView#DAY_NAME_FONT_BOLD_ITALIC} and normal{@link YearView#DAY_NAME_FONT_NORMAL}
     * @param dayNameFontType the new font type for the month
     */
    public void setdayNameFontType(int dayNameFontType){
        this.dayNameFontType = dayNameFontType;
        setupDayNamePaint();
        invalidate();
    }

    /**
     * Should be between bold {@link YearView#WEEKEND_NAME_FONT_BOLD}, italic{@link YearView#WEEKEND_NAME_FONT_ITALIC},
     * bold_italic{@link YearView#WEEKEND_NAME_FONT_BOLD_ITALIC} and normal{@link YearView#WEEKEND_NAME_FONT_NORMAL}
     * @param weekendFontType the new font type for the month
     */
    public void setWeekendNameFontType(int weekendFontType){
        this.weekendFontType = weekendFontType;
        setupWeekendPaint();
        invalidate();
    }

    /**
     * If true: a font type ( and a text color)  applied to the name of the days in the week,
     * will also be applied to the name of the days representing the weekend
     * @param transcendsWeekend
     */
    public void setDayNameTranscendsWeekend(boolean transcendsWeekend){
        dayNameTranscendsWeekend = transcendsWeekend;
        invalidate();
    }

    public int getYear() {
        return mYear;
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    private void doCleanup(){
        if(handler != null)
            handler.removeCallbacksAndMessages(null);
    }

    private void setupMonthNamePaint(){
        monthNamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        monthNamePaint.setColor(monthNameColor);
        monthNamePaint.setTextSize(mContext.getResources().getDimensionPixelSize(R.dimen.year_view_month_text_size));
        monthNamePaint.setTextAlign(Paint.Align.LEFT);
        switch (monthNameFontType){
            case MONTH_NAME_FONT_BOLD:
                monthNamePaint.setTypeface(Typeface.DEFAULT_BOLD);
                break;
            case MONTH_NAME_FONT_ITALIC:
                monthNamePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
                break;
            case MONTH_NAME_FONT_BOLD_ITALIC:
                monthNamePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
                break;
        }
    }

    private void setupDayNumberPaint(){
        dayNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dayNumberPaint.setColor(textColor);
        dayNumberPaint.setTextSize(mContext.getResources().getDimensionPixelSize(R.dimen.year_view_day_text_size));
        dayNumberPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void setupTodayTextPaint(){
        todayTodayTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        todayTodayTextPaint.setColor(todayTextColor);
        todayTodayTextPaint.setTextSize(mContext.getResources().getDimensionPixelSize(R.dimen.year_view_day_text_size));
        todayTodayTextPaint.setTextAlign(Paint.Align.LEFT);
        switch (todayFontType){
            case TODAY_FONT_BOLD:
                todayTodayTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
                break;
            case TODAY_FONT_ITALIC:
                todayTodayTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
                break;
            case TODAY_FONT_BOLD_ITALIC:
                todayTodayTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
                break;
        }
    }

    private void setupTodayBackgroundPaint(){
        todayTodayBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        todayTodayBackgroundPaint.setColor(todayBackgroundColor);
        todayTodayBackgroundPaint.setTextSize(mContext.getResources().getDimensionPixelSize(R.dimen.year_view_day_text_size));
        todayTodayBackgroundPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void setupDayNamePaint(){
        dayNamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dayNamePaint.setColor(dayNameColor);
        dayNamePaint.setTextSize(mContext.getResources().getDimensionPixelSize(R.dimen.year_view_day_text_size));
        dayNamePaint.setTextAlign(Paint.Align.LEFT);
        switch (dayNameFontType){
            case DAY_NAME_FONT_BOLD:
                dayNamePaint.setTypeface(Typeface.DEFAULT_BOLD);
                break;
            case DAY_NAME_FONT_ITALIC:
                dayNamePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
                break;
            case DAY_NAME_FONT_BOLD_ITALIC:
                dayNamePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
                break;
        }
    }

    private void setupWeekendPaint(){
        weekendDayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        weekendDayPaint.setColor(weekendColor);
        weekendDayPaint.setTextSize(mContext.getResources().getDimensionPixelSize(R.dimen.year_view_day_text_size));
        weekendDayPaint.setTextAlign(Paint.Align.LEFT);
        switch (weekendFontType){
            case WEEKEND_NAME_FONT_BOLD:
                weekendDayPaint.setTypeface(Typeface.DEFAULT_BOLD);
                break;
            case WEEKEND_NAME_FONT_ITALIC:
                weekendDayPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
                break;
            case WEEKEND_NAME_FONT_BOLD_ITALIC:
                weekendDayPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
                break;
        }
    }

    private void setupSelectionPaint(){
        selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectionPaint.setColor(ColorUtils.setAlphaComponent(selectionColor, SELECTION_ALPHA));
        selectionPaint.setStrokeJoin(Paint.Join.ROUND);
        selectionPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        selectionPaint.setStrokeWidth(SELECTION_STROKE);
    }

    private MonthGestureListener monthGestureListener;

    public void setMonthGestureListener(MonthGestureListener monthGestureListener) {
        this.monthGestureListener = monthGestureListener;
    }

    public interface MonthGestureListener{
        //first day of the month in millis
        void onClickMonth(long timeInMillis);
        void onLongClickMonth(long timeInMillis);
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
        public void onLongPress(MotionEvent ev) {
            if(monthGestureListener != null) {
                long timeInMillis = getClickedMonth((int)ev.getX(), (int)ev.getY());
                monthGestureListener.onLongClickMonth(timeInMillis);
                invalidate();
            }
        }

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
