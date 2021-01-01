package mamboa.yearview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Parcelable;
import android.support.annotation.FontRes;
import android.support.annotation.Nullable;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.graphics.ColorUtils;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Pair;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Created by mamboa on 9/4/2018.
 */
public class YearView extends View {
    private int mYear = 2018;
    private int verticalSpacing = 5;
    private int horizontalSpacing = 5;
    private int columns = 2;
    private int rows = 6;
    private int mWidth = 10;
    private int mHeight = 10;
    private int marginBelowMonthName = 5;
    private boolean mSundayFirst = false;
    private int monthTitleGravity = 1;
    private int firstDayOfWeek = 1; //since we are using Joda-Time, it goes from Monday: 1 to Sunday: 7
    private Context mContext;
    private final static int numDays = 7;
    private int monthSelectionColor = Color.BLUE;
    private int todayTextColor = Color.WHITE;
    private int todayBackgroundColor = Color.RED;
    private int selectedDayBackgroundColor = Color.BLUE;
    private int simpleDayTextColor = Color.BLACK;
    private int weekendTextColor = Color.BLACK;
    private int dayNameTextColor = Color.BLACK;
    private int monthNameTextColor = Color.BLACK;
    private int todayMonthNameTextColor = Color.BLACK;
    private int selectedDayTextColor = Color.WHITE;
    private BackgroundShape selectedDayBackgroundShape = BackgroundShape.SQUARE;
    private BackgroundShape todayBackgroundShape = BackgroundShape.CIRCLE;
    private FontType monthNameFontType = FontType.NORMAL;
    private FontType dayNameFontType = FontType.NORMAL;
    private FontType todayFontType = FontType.NORMAL;
    private FontType weekendFontType = FontType.NORMAL;
    private FontType simpleDayFontType = FontType.NORMAL;
    private FontType todayMonthNameFontType = FontType.NORMAL;
    private FontType selectedDayFontType = FontType.NORMAL;
    private int monthSelectionMargin = 5;
    private Typeface monthNameFontTypeFace = null;
    private Typeface weekendFontTypeFace = null;
    private Typeface dayNameFontTypeFace = null;
    private Typeface todayFontTypeFace = null;
    private Typeface simpleDayFontTypeFace = null;
    private Typeface todayMonthNameFontTypeFace = null;
    private Typeface selectedDayTypeFace = null;
    private int simpleDayTextSize = 0;
    private int weekendTextSize = 0;
    private int todayTextSize = 0;
    private int dayNameTextSize = 0;
    private int monthNameTextSize = 0;
    private int todayMonthNameTextSize = 0;
    private int selectedDayTextSize = 0;
    private final Paint.Align DEFAULT_ALIGN = Paint.Align.CENTER;
    private MonthGestureListener monthGestureListener;
    private final static String DAY_PATTERN = "yyyy-MM-dd";
    private boolean isDaySelectionVisuallySticky = false;

    private int[] weekendDays = new int[366];

    private int todayBackgroundRadius = 5;
    private int selectedDayBackgroundRadius = 5;
    private final static int DEFAULT_TEXT_SIZE = 10; //sp

    enum FontType {
        NORMAL,
        BOLD,
        ITALIC,
        BOLD_ITALIC
    }

    private Paint simpleDayNumberPaint;
    private Paint todayTextPaint;
    private Paint todayBackgroundPaint;
    private Paint selectionPaint;
    private Paint monthNamePaint;
    private Paint todayMonthNamePaint;
    private Paint weekendDayPaint;
    private Paint dayNamePaint;
    private Paint selectedDayTextPaint;
    private Paint selectedDayBackgroundPaint;

    //if true: a font type ( and a text color)  applied to the name of the days in the week,
    // will also be applied to the name of the days representing the weekend
    private boolean dayNameTranscendsWeekend = false;

    private Rect[] monthBlocks = null;
    private Rect[] originalMonthBlocks = null; //used for the visual click animation
    private ArrayList<Pair<Rect, String>> daysBlocks = null;
    private int[] lastRowPositionInMonth = null;
    private GestureDetector mGestureDetector;

    enum BackgroundShape {
        CIRCLE,
        SQUARE
    }

    public final static int TITLE_GRAVITY_CENTER = 1;
    public final static int TITLE_GRAVITY_LEFT = 2;
    public final static int TITLE_GRAVITY_RIGHT = 3;
    public final static int TITLE_GRAVITY_START = 2;
    public final static int TITLE_GRAVITY_END = 3;
    private int mOnDownDelay = 0;

    private final static float SELECTION_STROKE = 5.f;
    private final static int SELECTION_ALPHA = 255 / 2;

    private int selectedMonthID = -1;
    private String selectedDay = "";

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
        try {
            mYear = a.getInteger(R.styleable.YearView_current_year, mYear);
            rows = a.getInteger(R.styleable.YearView_rows, rows);
            columns = a.getInteger(R.styleable.YearView_columns, columns);
            verticalSpacing = a.getInteger(R.styleable.YearView_vertical_spacing, verticalSpacing);
            horizontalSpacing = a.getInteger(R.styleable.YearView_horizontal_spacing, horizontalSpacing);
            monthTitleGravity = a.getInteger(R.styleable.YearView_month_title_gravity, monthTitleGravity);
            marginBelowMonthName = a.getInteger(R.styleable.YearView_margin_below_month_name, marginBelowMonthName);
            monthSelectionColor = a.getColor(R.styleable.YearView_month_selection_color, monthSelectionColor);
            simpleDayTextColor = a.getColor(R.styleable.YearView_simple_day_text_color, simpleDayTextColor);
            weekendTextColor = a.getColor(R.styleable.YearView_weekend_text_color, weekendTextColor);
            firstDayOfWeek = a.getInteger(R.styleable.YearView_firstDayOfWeek, firstDayOfWeek);
            todayTextColor = a.getColor(R.styleable.YearView_today_text_color, todayTextColor);
            todayBackgroundColor = a.getColor(R.styleable.YearView_today_background_color, todayBackgroundColor);
            todayBackgroundRadius = a.getInteger(R.styleable.YearView_today_background_radius, todayBackgroundRadius);
            selectedDayBackgroundRadius = a.getInteger(R.styleable.YearView_selected_day_background_radius, selectedDayBackgroundRadius);
            dayNameTextColor = a.getInteger(R.styleable.YearView_day_name_text_color, dayNameTextColor);
            monthNameTextColor = a.getInteger(R.styleable.YearView_month_name_text_color, monthNameTextColor);
            todayMonthNameTextColor = a.getInteger(R.styleable.YearView_today_month_name_text_color, todayMonthNameTextColor);
            selectedDayTextColor = a.getInteger(R.styleable.YearView_selected_day_text_color, selectedDayTextColor);
            selectedDayBackgroundColor = a.getColor(R.styleable.YearView_selected_day_background_color, selectedDayBackgroundColor);
            todayBackgroundShape = BackgroundShape.values()[a.getInteger(R.styleable.YearView_today_background_shape, BackgroundShape.CIRCLE.ordinal())];
            selectedDayBackgroundShape = BackgroundShape.values()[a.getInteger(R.styleable.YearView_selected_day_background_shape, BackgroundShape.SQUARE.ordinal())];

            monthNameFontType = FontType.values()[a.getInteger(R.styleable.YearView_month_name_font_type, FontType.NORMAL.ordinal())];
            dayNameFontType = FontType.values()[a.getInteger(R.styleable.YearView_day_name_font_type, FontType.NORMAL.ordinal())];
            todayFontType = FontType.values()[a.getInteger(R.styleable.YearView_today_font_type, FontType.NORMAL.ordinal())];

            weekendFontType = FontType.values()[a.getInteger(R.styleable.YearView_weekend_font_type, FontType.NORMAL.ordinal())];
            simpleDayFontType = FontType.values()[a.getInteger(R.styleable.YearView_simple_day_font_type, FontType.NORMAL.ordinal())];
            todayMonthNameFontType = FontType.values()[a.getInteger(R.styleable.YearView_today_month_name_font_type, FontType.NORMAL.ordinal())];
            selectedDayFontType = FontType.values()[a.getInteger(R.styleable.YearView_selected_day_font_type, FontType.NORMAL.ordinal())];

            dayNameTranscendsWeekend = a.getBoolean(R.styleable.YearView_name_week_transcend_weekend, dayNameTranscendsWeekend);
            isDaySelectionVisuallySticky = a.getBoolean(R.styleable.YearView_is_day_selection_visually_sticky, isDaySelectionVisuallySticky);
            monthSelectionMargin = a.getInteger(R.styleable.YearView_month_selection_margin, monthSelectionMargin);

            monthNameFontTypeFace = buildFont(a.getResourceId(R.styleable.YearView_month_name_font, 0), a);
            weekendFontTypeFace = buildFont(a.getResourceId(R.styleable.YearView_weekend_font, 0), a);
            dayNameFontTypeFace = buildFont(a.getResourceId(R.styleable.YearView_day_name_font, 0), a);
            todayFontTypeFace = buildFont(a.getResourceId(R.styleable.YearView_today_font, 0), a);
            simpleDayFontTypeFace = buildFont(a.getResourceId(R.styleable.YearView_simple_day_font, 0), a);
            todayMonthNameFontTypeFace = buildFont(a.getResourceId(R.styleable.YearView_today_month_name_font, 0), a);
            selectedDayTypeFace = buildFont(a.getResourceId(R.styleable.YearView_selected_day_font, 0), a);

            int defaultTextSize = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SIZE, getResources().getDisplayMetrics()) + 0.5);

            simpleDayTextSize = a.getDimensionPixelSize(R.styleable.YearView_simple_day_text_size, defaultTextSize);
            weekendTextSize = a.getDimensionPixelSize(R.styleable.YearView_weekend_text_size, defaultTextSize);
            todayTextSize = a.getDimensionPixelSize(R.styleable.YearView_today_text_size, defaultTextSize);
            dayNameTextSize = a.getDimensionPixelSize(R.styleable.YearView_day_name_text_size, defaultTextSize);
            monthNameTextSize = a.getDimensionPixelSize(R.styleable.YearView_month_name_text_size, defaultTextSize);
            todayMonthNameTextSize = a.getDimensionPixelSize(R.styleable.YearView_today_month_name_text_size, defaultTextSize);
            selectedDayTextSize = a.getDimensionPixelSize(R.styleable.YearView_selected_day_text_size, defaultTextSize);

            selectedDay = a.getString(R.styleable.YearView_selected_day_text);
            if (!isDaySelectionVisuallySticky) {
                selectedDay = "";
            }

            //here we get the week end days defined in the xml's "app:weekend_days=..."
            int weekendDaysID = a.getResourceId(R.styleable.YearView_weekend_days, 0);
            if (weekendDaysID > 0)
                weekendDays = a.getResources().getIntArray(weekendDaysID);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            a.recycle();
        }

        mOnDownDelay = ViewConfiguration.getTapTimeout();

        setupSimpleDayNumberPaint();
        setupWeekendPaint();
        setupDayNamePaint();
        setupMonthNamePaint();
        setupTodayMonthNamePaint();
        setupTodayTextPaint();
        setupTodayBackgroundPaint();
        setupMonthSelectionPaint();
        setupSelectedDayTextPaint();
        setupSelectedDayBackgroundPaint();

        mGestureDetector = new GestureDetector(context, new CalendarGestureListener());
        mSundayFirst = true;

        handler = new Handler();
    }

    private Typeface buildFont(@FontRes int fontID, TypedArray typeArray) {
        return fontID == 0 || typeArray == null ? null : Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? typeArray.getResources().getFont(fontID) : ResourcesCompat.getFont(mContext, fontID);
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
        if (mContext != null)
            mContext = null;
        doCleanup();
        super.onDetachedFromWindow();
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
        Parcelable superState = super.onSaveInstanceState();

        //save here
        return superState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
        //restore here
        requestLayout();
    }

    /**
     * Split the view in a grid of columns * rows having 12 blocks in all. Each block will be used to draw a month inside
     */
    private void splitViewInBlocks() {
        monthBlocks = new Rect[12];
        originalMonthBlocks = new Rect[12];
        daysBlocks = new ArrayList<>(366);

        // allows us to compensate a right side padding
        //that happens because when we draw text we use Align.Center
        int horizontalCompensationPadding = (columns % 2 != 0) ? (horizontalSpacing * columns / 2) : (horizontalSpacing * columns / rows);

        int k = 0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                int currentHorizontalSpacing = horizontalSpacing / 2;
                int currentVerticalSpacing = verticalSpacing / 2;

                int left = horizontalCompensationPadding + (j == 0 ? currentHorizontalSpacing * 2 : currentHorizontalSpacing) + (j * mWidth / columns); //+ horizontal spacing to compensate for the align center
                int top = currentVerticalSpacing + (i * mHeight / rows);
                int right = (j + 1) * mWidth / columns - (j == columns - 1 ? currentHorizontalSpacing * 2 : currentHorizontalSpacing);
                int bottom = (i + 1) * mHeight / rows - currentVerticalSpacing;
                monthBlocks[k] = new Rect(left, top, right, bottom);
                originalMonthBlocks[k] = new Rect(left, top, right, bottom);
                k++;
            }
        }
    }

    private void drawMonths(Canvas canvas) {
        lastRowPositionInMonth = new int[12];
        DateTime dateTime = new DateTime().withDate(mYear, 2, 1).withHourOfDay(12);

        for (int i = 0; i <= 11; i++) {
            DateTime monthTime = dateTime.withMonthOfYear(i + 1);
            int dayOfWeek = dateTime.withMonthOfYear(i + 1).dayOfWeek().get();
            if (firstDayOfWeek != DateTimeConstants.SUNDAY)
                dayOfWeek -= firstDayOfWeek;

            drawAMonth(canvas, i, dayOfWeek, monthTime.dayOfMonth().getMaximumValue(), monthTime.monthOfYear().getAsText(Utils.getCurrentLocale(mContext)));
        }
    }

    private void drawSelection(Canvas canvas) {
        if (selectedMonthID > -1) {
            canvas.drawRect(
                    originalMonthBlocks[selectedMonthID].left - monthSelectionMargin - horizontalSpacing,
                    originalMonthBlocks[selectedMonthID].top - monthSelectionMargin,
                    originalMonthBlocks[selectedMonthID].right + monthSelectionMargin - horizontalSpacing,
                    lastRowPositionInMonth[selectedMonthID] + monthSelectionMargin,
                    selectionPaint
            );

            if (!isClearSelectionLaunched) {
                handler.postDelayed(clearSelectionRunnable, mOnDownDelay * 2);
                isClearSelectionLaunched = true;
            }
        }
    }

    /**
     * Draws the label name of a month
     *
     * @param canvas    the canvas' reference
     * @param index     the index of the month ([0,11])
     * @param monthName the String name of the month
     */
    private void drawMonthName(Canvas canvas, int index, String monthName) {
        Paint paint = null;
        int today = new DateTime().getDayOfMonth();
        try {
            if (isToday(index, today))
                paint = new Paint(todayMonthNamePaint);
            else
                paint = new Paint(monthNamePaint);
        } catch (Exception ex) {
            ex.getStackTrace();
            paint = new Paint(monthNamePaint);
        }


        Rect textBounds = new Rect();
        paint.getTextBounds(monthName, 0, monthName.length(), textBounds);

        int xStart = 0;
        int yValue = monthBlocks[index].top + textBounds.height();
        int width = textBounds.width();
        //
        switch (monthTitleGravity) {
            case TITLE_GRAVITY_START: //TITLE_GRAVITY_LEFT
                xStart = monthBlocks[index].left + width / 2 - horizontalSpacing / 2; //if ALIGN.LEFT monthBlocks[index].left + horizontalSpacing/2;
                break;
            case TITLE_GRAVITY_CENTER:
                xStart = (monthBlocks[index].left + monthBlocks[index].right) / 2 - horizontalSpacing;  //if ALIGN.LEFT (monthBlocks[index].left + monthBlocks[index].right)/2 - width /2;
                break;
            case TITLE_GRAVITY_END: //or TITLE_GRAVITY_RIGHT
                xStart = monthBlocks[index].right - width / 2 - horizontalSpacing * 2;  //if ALIGN.LEFT  monthBlocks[index].right - width - horizontalSpacing/2;
                break;
        }

        canvas.drawText(monthName + "", xStart, yValue, paint);

        //shift the rest below so that clicking the name won't trigger the month animation
        int left = monthBlocks[index].left;
        int top = monthBlocks[index].top + textBounds.height() * 2 + marginBelowMonthName;
        int right = monthBlocks[index].right;
        int bottom = monthBlocks[index].bottom;
        monthBlocks[index] = new Rect(left, top, right, bottom);

        //test
        /*Paint selectionPaintTest = new Paint(selectionPaint);
        canvas.drawRect(monthBlocks[index],selectionPaintTest);*/
    }

    /**
     * Draws a month
     *
     * @param canvas
     * @param month       the index of the month as in the list of monthBlock
     * @param firstDay    the first day of the month
     * @param daysInMonth the maximum number of days in that month
     * @param monthName   the string name of a month
     */
    private void drawAMonth(Canvas canvas, int month, int firstDay, int daysInMonth, String monthName) {
        drawMonthName(canvas, month, monthName);

        int xUnit = monthBlocks[month].width() / numDays;
        int yUnit = monthBlocks[month].height() / numDays;

        int dayOfMonth = 1 - firstDay;
        for (int y = 0; y <= 7; y++) {
            for (int x = 0; x < 7; x++) {
                int xValue = monthBlocks[month].left + (xUnit * x);
                int yValue = monthBlocks[month].top + (yUnit * y);

                DateTime dateTime = new DateTime()
                        .withYear(mYear)
                        .withMonthOfYear(month + 1)
                        .withDayOfWeek(getDayIndex(x));

                //draw day titles
                if (y == 0) {
                    DateTime.Property pDoW = dateTime.dayOfWeek();
                    String dayName = pDoW.getAsShortText(Utils.getCurrentLocale(mContext)).substring(0, 1);

                    //weekend days
                    if (isDayPresentInWeekendDays(dateTime.getDayOfWeek()) && !dayNameTranscendsWeekend) {
                        //todo: add background for title ?
                        canvas.drawText(dayName + "", xValue, yValue, weekendDayPaint);
                    } else {
                        canvas.drawText(dayName + "", xValue, yValue, dayNamePaint);
                    }
                }

                //draw day numbers
                else {
                    if (dayOfMonth >= 1 && dayOfMonth <= daysInMonth) {
                        boolean isWeekEnd = isWeekend(month, dayOfMonth);
                        savePositionForSelection(canvas, xValue, yValue, dayOfMonth, isWeekEnd, mYear, month + 1);

                        if (isSelectedDay(month, dayOfMonth)) {
                            switch (selectedDayBackgroundShape) {
                                //todo: we might add other shapes in the future (triangel, etc.)
                                case CIRCLE:
                                    drawCircleAroundText(canvas, dayOfMonth, selectedDayTextPaint, selectedDayBackgroundPaint, xValue, yValue, selectedDayBackgroundRadius);
                                    break;
                                default:
                                    drawSquareAroundText(canvas, selectedDayTextPaint , selectedDayBackgroundPaint, dayOfMonth, xValue, yValue, selectedDayBackgroundRadius);
                            }
                        } else if (isToday(month, dayOfMonth)) {
                            switch (todayBackgroundShape) {
                                //todo: we might add other shapes in the future (triangel, etc.)
                                case CIRCLE:
                                    drawCircleAroundText(canvas, dayOfMonth, todayTextPaint, todayBackgroundPaint, xValue, yValue, todayBackgroundRadius);
                                    break;
                                default:
                                    drawSquareAroundText(canvas, todayTextPaint, todayBackgroundPaint, dayOfMonth, xValue, yValue, todayBackgroundRadius);
                            }
                        } else {
                            if (isWeekEnd) {
                                //todo: draw a color to the background ?
                                canvas.drawText(dayOfMonth + "", xValue, yValue, weekendDayPaint);
                            } else {
                                //todo: draw a color to the background ?
                                canvas.drawText(dayOfMonth + "", xValue, yValue, simpleDayNumberPaint);
                            }
                        }
                        lastRowPositionInMonth[month] = yValue; //we save the last line for the selection
                    }
                    dayOfMonth++;
                }
            }
        }
    }

    /**
     * Save the coordinates areas that, when pressed upon, will trigger the selection
     * @param canvas the reference to the canvas we are drawing on
     * @param xValue the x start value of the area of the selection
     * @param yValue the  y start value of the area of the selection
     * @param dayOfMonth the day of the month (this is use at debug time, when we need to see the available area for that the user can click on)
     * @param isWeekend is this a weekend day (this is use at debug time, when we need to see the available area for that the user can click on)
     * @param year the year of the view (this is use at debug time, when we need to see the available area for that the user can click on)
     * @param month the month of the view (this is use at debug time, when we need to see the available area for that the user can click on)
     */
    private void savePositionForSelection(Canvas canvas, int xValue, int yValue, int dayOfMonth, boolean isWeekend, int year, int month) {
        String date = year + "-" + month + "-" + dayOfMonth;

        //border around date's day
        Paint paint = isWeekend ? weekendDayPaint : simpleDayNumberPaint;
        Rect textBounds = new Rect();
        paint.getTextBounds(dayOfMonth + "", 0, (dayOfMonth + "").length(), textBounds);

        //dividing factor for the width of the selection will change to 0.9 if dayOfMonth is a digit
        double factor = dayOfMonth < 10 ? 0.9 : 1.5;

        //border around date's day
        Rect border = new Rect();
        border.left = xValue - (int) (textBounds.width() / factor);
        border.top = yValue - (int) (textBounds.height() * 1.5);
        border.right = xValue + (int) (textBounds.width() / factor);
        border.bottom = yValue + textBounds.height() / 2;
        daysBlocks.add(new Pair<>(border, date));

        // test visually the touch area that will acknowledge the day click
        //canvas.drawRect(border, paint);
    }

    /**
     * Draws a square around a given day
     * @param canvas     the canvas
     * @param textPaint  the text paint
     * @param backgroundPaint  the background paint
     * @param dayOfMonth the day of the month
     * @param xValue     the x of where to draw the day of the month's value
     * @param yValue     the y of where to draw the day of the month's value
     * @param margin     around the month value
     */
    private void drawSquareAroundText(Canvas canvas, Paint textPaint, Paint backgroundPaint, int dayOfMonth, int xValue, int yValue, int margin) {
        RectF boxRect = new RectF();
        Rect bounds = new Rect();
        textPaint.getTextBounds(dayOfMonth + "", 0, (dayOfMonth + "").length(), bounds);

        //background square ALIGN.CENTER
        boxRect.left = xValue - (int) (bounds.width() / 2) - margin;
        boxRect.top = yValue - bounds.height() - margin;
        boxRect.right = xValue + (int) (bounds.width() / 2) + margin;
        boxRect.bottom = yValue + margin;

        //background square ALIGN.LEFT
        /*boxRect.left = xValue - margin;
        boxRect.top = yValue - diffAscDesc - diffTop - margin;
        boxRect.right = xValue + bounds.width() + margin;
        boxRect.bottom = yValue  + diffBottom + margin;*/

        canvas.drawRect(boxRect, backgroundPaint);
        canvas.drawText(dayOfMonth + "", xValue, yValue, textPaint);
    }

    /**
     * Draws a circle around a given day
     * @param canvas     the canvas
     * @param dayOfMonth the day of the month
     * @param textPaint  the text paint
     * @param backgroundPaint  the background paint
     * @param xValue     the x of where to draw the day of the month's value
     * @param yValue     the y of where to draw the day of the month's value
     * @param margin     radius the month value
     */
    private void drawCircleAroundText(Canvas canvas, int dayOfMonth, Paint textPaint, Paint backgroundPaint, int xValue, int yValue, int margin) {
        Rect bounds = new Rect();
        textPaint.getTextBounds(dayOfMonth + "", 0, (dayOfMonth + "").length(), bounds);

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        int diffAscDesc = (int) (Math.abs(fm.ascent + fm.descent));

        //background square ALIGN.CENTER
        int centerX = xValue;
        int centerY = yValue - diffAscDesc / 2;

        //background square ALIGN.LEFT
       /* int centerX = xValue + bounds.width()/2;
        int centerY = yValue - diffAscDesc/2;*/

        //we make sure that the circle will always surround the value of the digits
        int radius = (bounds.width() > bounds.height() ? bounds.width() : bounds.height()) / 2 + margin;

        canvas.drawCircle(centerX, centerY, radius, backgroundPaint);
        canvas.drawText(dayOfMonth + "", xValue, yValue, textPaint);
    }

    /**
     * Tells if the current date defined by the month and the day of the {@link YearView#mYear) refers
     * to the real life current date
     *
     * @param month      whose index between [0 - 11] is given
     * @param dayOfMonth whose index is given
     * @return true if we are today false otherwise
     */
    private boolean isToday(int month, int dayOfMonth) {
        DateTime dateTime = new DateTime()
                .withYear(mYear)
                .withMonthOfYear(month + 1) //the variable monthBlocks goes from 0 to 11 while Joda Time's months go from 1 to 12
                .withDayOfMonth(dayOfMonth);
        return dateTime.toLocalDate().equals(new LocalDate());
    }

    /**
     * Returns true if the current date defined by the month and the day of the {@link YearView#mYear) refers
     * to the date selected by the user
     *
     * @param month      whose index between [0 - 11] is given
     * @param dayOfMonth whose index is given
     * @return true if we are today false otherwise
     */
    private boolean isSelectedDay(int month, int dayOfMonth) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern(DAY_PATTERN).withLocale(Locale.ROOT);
        DateTime dateTime = new DateTime()
                .withYear(mYear)
                .withMonthOfYear(month + 1) //the variable monthBlocks goes from 0 to 11 while Joda Time's months go from 1 to 12
                .withDayOfMonth(dayOfMonth);
        return selectedDay.equals(formatter.print(dateTime));
    }

    /**
     * Determine if a given day in a given mon is a weekend day. Notice that here we consider that
     * a weekend day is customizable
     *
     * @param month      the current month
     * @param dayOfMonth the day of the mont
     * @return it the day is considered a week end day
     */
    private boolean isWeekend(int month, final int dayOfMonth) {
        DateTime dateTime = new DateTime()
                .withYear(mYear)
                .withMonthOfYear(month + 1) //the variable monthBlocks goes from 0 to 11 while Joda Time's months go from 1 to 12
                .withDayOfMonth(dayOfMonth);
        return isDayPresentInWeekendDays(dateTime.getDayOfWeek());
    }

    /**
     * Detects if a day is present in the list of custom weekend days
     *
     * @param dayIndex defines a day according to {@link org.joda.time.DateTimeConstants}, from SUNDAY
     *                 to SATURDAY
     * @return if a day is present in the list of weekend days
     */
    private boolean isDayPresentInWeekendDays(int dayIndex) {
        if (weekendDays == null || weekendDays.length == 0)
            return false;
        for (int i = 0; i < weekendDays.length; i++) {
            if (weekendDays[i] == dayIndex)
                return true;
        }
        return false;
    }

    /**
     * Returns the day index according to DateTimeConstants Joda-Time, taking into account what the user
     * wants as first day of the week
     *
     * @param position of the day ([0;7[)
     * @return
     */
    private int getDayIndex(int position) {
        return firstDayOfWeek == DateTimeConstants.MONDAY || position + firstDayOfWeek <= 7 ?
                firstDayOfWeek + position :
                (firstDayOfWeek + position) % 7;
    }

    /**
     * Set the weekend days according to {@link org.joda.time.DateTimeConstants}.
     * Example: newWeekendDays = {DateTimeConstants.SATURDAY, DateTimeConstants.SUNDAY}
     * The order of the days doesn't count
     *
     * @param newWeekendDays
     */
    public void setWeekendDays(int[] newWeekendDays) {
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

    public void setMonthSelectionColor(int monthSelectionColor) {
        this.monthSelectionColor = monthSelectionColor;
        setupMonthSelectionPaint();
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

    public void setSimpleDayTextColor(int simpleDayTextColor) {
        this.simpleDayTextColor = simpleDayTextColor;
        setupSimpleDayNumberPaint();
        invalidate();
    }

    public void setWeekendTextColor(int weekendTextColor) {
        this.weekendTextColor = weekendTextColor;
        setupWeekendPaint();
        invalidate();
    }

    public void setTodayBackgroundRadius(int todayBackgroundRadius) {
        this.todayBackgroundRadius = todayBackgroundRadius;
        setupTodayBackgroundPaint();
        invalidate();
    }

    public void setDayNameTextColor(int dayNameTextColor) {
        this.dayNameTextColor = dayNameTextColor;
        setupDayNamePaint();
        invalidate();
    }

    public void setMonthNameTextColor(int monthNameTextColor) {
        this.monthNameTextColor = monthNameTextColor;
        setupMonthNamePaint();
        invalidate();
    }

    public void setTodayMonthNameTextColor(int todayMonthNameTextColor) {
        this.todayMonthNameTextColor = todayMonthNameTextColor;
        setupTodayMonthNamePaint();
        invalidate();
    }

    public void setMonthSelectionMargin(int monthSelectionMargin) {
        this.monthSelectionMargin = monthSelectionMargin;
        setupMonthSelectionPaint();
        invalidate();
    }

    public void setMonthNameFontTypeFace(Typeface monthNameFontTypeFace) {
        this.monthNameFontTypeFace = monthNameFontTypeFace;
        setupMonthNamePaint();
        invalidate();
    }

    public void setWeekendFontTypeFace(Typeface weekendFontTypeFace) {
        this.weekendFontTypeFace = weekendFontTypeFace;
        setupWeekendPaint();
        invalidate();
    }

    public void setDayNameFontTypeFace(Typeface dayNameFontTypeFace) {
        this.dayNameFontTypeFace = dayNameFontTypeFace;
        setupDayNamePaint();
        invalidate();
    }

    public void setTodayFontTypeFace(Typeface todayFontTypeFace) {
        this.todayFontTypeFace = todayFontTypeFace;
        setupTodayTextPaint();
        invalidate();
    }

    public void setSimpleDayFontTypeFace(Typeface simpleDayFontTypeFace) {
        this.simpleDayFontTypeFace = simpleDayFontTypeFace;
        setupSimpleDayNumberPaint();
        invalidate();
    }

    public void setTodayMonthNameFontTypeFace(Typeface todayMonthNameFontTypeFace) {
        this.todayMonthNameFontTypeFace = todayMonthNameFontTypeFace;
        setupTodayMonthNamePaint();
        invalidate();
    }

    /**
     * Updates the text size. If below 0 it will take the default text size{@link YearView#DEFAULT_TEXT_SIZE}
     *
     * @param simpleDayTextSize
     */
    public void setSimpleDayTextSize(int simpleDayTextSize) {
        if (simpleDayTextSize < 0)
            this.simpleDayTextSize = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SIZE, getResources().getDisplayMetrics()) + 0.5);
        else
            this.simpleDayTextSize = simpleDayTextSize;
        setupSimpleDayNumberPaint();
        invalidate();
    }

    /**
     * Updates the text size. If below 0 it will take the default text size{@link YearView#DEFAULT_TEXT_SIZE}
     *
     * @param weekendTextSize
     */
    public void setWeekendTextSize(int weekendTextSize) {
        if (weekendTextSize < 0)
            this.weekendTextSize = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SIZE, getResources().getDisplayMetrics()) + 0.5);
        else
            this.weekendTextSize = weekendTextSize;
        setupWeekendPaint();
        invalidate();
    }

    /**
     * Updates the text size. If below 0 it will take the default text size{@link YearView#DEFAULT_TEXT_SIZE}
     *
     * @param todayTextSize
     */
    public void setTodayTextSize(int todayTextSize) {
        if (todayTextSize < 0)
            this.todayTextSize = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SIZE, getResources().getDisplayMetrics()) + 0.5);
        else
            this.todayTextSize = todayTextSize;
        setupTodayTextPaint();
        invalidate();
    }

    /**
     * Updates the text size. If below 0 it will take the default text size{@link YearView#DEFAULT_TEXT_SIZE}
     *
     * @param dayNameTextSize
     */
    public void setDayNameTextSize(int dayNameTextSize) {
        if (dayNameTextSize < 0)
            this.dayNameTextSize = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SIZE, getResources().getDisplayMetrics()) + 0.5);
        else
            this.dayNameTextSize = dayNameTextSize;
        setupDayNamePaint();
        invalidate();
    }

    /**
     * Updates the text size. If below 0 it will take the default text size{@link YearView#DEFAULT_TEXT_SIZE}
     *
     * @param monthNameTextSize
     */
    public void setMonthNameTextSize(int monthNameTextSize) {
        if (monthNameTextSize < 0)
            this.monthNameTextSize = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SIZE, getResources().getDisplayMetrics()) + 0.5);
        else
            this.monthNameTextSize = monthNameTextSize;
        setupMonthNamePaint();
        invalidate();
    }

    /**
     * Updates the text size. If below 0 it will take the default text size{@link YearView#DEFAULT_TEXT_SIZE}
     *
     * @param todayMonthNameTextSize
     */
    public void setTodayMonthNameTextSize(int todayMonthNameTextSize) {
        if (todayMonthNameTextSize < 0)
            this.todayMonthNameTextSize = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SIZE, getResources().getDisplayMetrics()) + 0.5);
        else
            this.todayMonthNameTextSize = todayMonthNameTextSize;
        setupTodayMonthNamePaint();
        invalidate();
    }

    /**
     * Should be between {@link YearView#TITLE_GRAVITY_CENTER}, {@link YearView#TITLE_GRAVITY_START} or {@link YearView#TITLE_GRAVITY_LEFT},
     * {@link YearView#TITLE_GRAVITY_END} or {@link YearView#TITLE_GRAVITY_RIGHT}. Will be set
     * to TITLE_GRAVITY_CENTER if undefined
     *
     * @param monthTitleGravity the new title gravity
     */
    public void setMonthTitleGravity(int monthTitleGravity) {
        if (monthTitleGravity != TITLE_GRAVITY_CENTER && monthTitleGravity != TITLE_GRAVITY_END && monthTitleGravity != TITLE_GRAVITY_LEFT)
            monthTitleGravity = TITLE_GRAVITY_CENTER;
        else
            this.monthTitleGravity = monthTitleGravity;
        invalidate();
    }

    /**
     * Should be between circle and square
     *
     * @param todayBackgroundShape the new background shape. Will be set to circle if undefined
     */
    public void setTodayBackgroundShape(BackgroundShape todayBackgroundShape) {
        if (todayBackgroundShape != BackgroundShape.CIRCLE && todayBackgroundShape != BackgroundShape.SQUARE)
            this.todayBackgroundShape = BackgroundShape.CIRCLE;
        else
            this.todayBackgroundShape = todayBackgroundShape;
        invalidate();
    }

    /**
     * Should be between circle and square
     *
     * @param selectedDayBackgroundShape the new background shape. Will be set to square if undefined
     */
    public void setSelectedDayBackgroundShape(BackgroundShape selectedDayBackgroundShape) {
        if (selectedDayBackgroundShape != BackgroundShape.CIRCLE && selectedDayBackgroundShape != BackgroundShape.SQUARE)
            this.selectedDayBackgroundShape = BackgroundShape.SQUARE;
        else
            this.selectedDayBackgroundShape = selectedDayBackgroundShape;
        invalidate();
    }

    /**
     * Should be between bold, italic,
     * bold_italic and normal
     *
     * @param todayFontType the new font type for today
     */
    public void setTodayFontType(FontType todayFontType) {
        this.todayFontType = todayFontType;
        setupTodayTextPaint();
        invalidate();
    }

    /**
     * Should be between bold, italic,
     * bold_italic and normal
     *
     * @param monthNameFontType the new font type for the month
     */
    public void setMonthNameFontType(FontType monthNameFontType) {
        this.monthNameFontType = monthNameFontType;
        setupMonthNamePaint();
        invalidate();
    }

    /**
     * Should be between bold, italic,
     * bold_italic and normal
     *
     * @param todayMonthNameFontType the new font type for the month
     */
    public void setTodayMonthNameFontType(FontType todayMonthNameFontType) {
        this.todayMonthNameFontType = todayMonthNameFontType;
        setupTodayMonthNamePaint();
        invalidate();
    }

    /**
     * Should be between bold, italic,
     * bold_italic and normal
     *
     * @param dayNameFontType the new font type for the month
     */
    public void setDayNameFontType(FontType dayNameFontType) {
        this.dayNameFontType = dayNameFontType;
        setupDayNamePaint();
        invalidate();
    }

    /**
     * Should be between bold, italic,
     * bold_italic and normal
     *
     * @param weekendFontType the new font type for the month
     */
    public void setWeekendNameFontType(FontType weekendFontType) {
        this.weekendFontType = weekendFontType;
        setupWeekendPaint();
        invalidate();
    }

    /**
     * Should be between bold, italic,
     * bold_italic and normal
     *
     * @param simpleDayFontType the new font type for the month
     */
    public void setSimpleDayFontType(FontType simpleDayFontType) {
        this.simpleDayFontType = simpleDayFontType;
        setupSimpleDayNumberPaint();
        invalidate();
    }

    /**
     * Should be between bold, italic,
     * bold_italic and normal
     *
     * @param selectedDayFontType the new font type for the month
     */
    public void setSelectedDayFontType(FontType selectedDayFontType) {
        this.selectedDayFontType = selectedDayFontType;
        setupSimpleDayNumberPaint();
        invalidate();
    }

    /**
     * If true: a font type ( and a text color)  applied to the name of the days in the week,
     * will also be applied to the name of the days representing the weekend
     *
     * @param transcendsWeekend
     */
    public void setDayNameTranscendsWeekend(boolean transcendsWeekend) {
        dayNameTranscendsWeekend = transcendsWeekend;
        invalidate();
    }

    public boolean isDaySelectionVisuallySticky() {
        return isDaySelectionVisuallySticky;
    }

    public void setIfDaySelectionVisuallySticky(boolean isDaySelectionVisuallySticky) {
        this.isDaySelectionVisuallySticky = isDaySelectionVisuallySticky;
        if (!isDaySelectionVisuallySticky) {
            selectedDay = "";
        }
        invalidate();
    }

    /**
     * Returns the timestamp of the currently selected day. Will return 0 if there is no selected date
     * @return timestamp of the selected day
     */
    public long getSelectedDay() {
        if (!TextUtils.isEmpty(selectedDay)) {
            try {
                DateTimeFormatter formatter = DateTimeFormat.forPattern(DAY_PATTERN).withLocale(Locale.ROOT);
                DateTime dateTime = formatter.parseDateTime(selectedDay);
                return dateTime.getMillis();
            } catch (Exception ex) {
                ex.printStackTrace();
                return 0;
            }
        }
        return 0;
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

    private void doCleanup() {
        if (handler != null)
            handler.removeCallbacksAndMessages(null);
    }

    private void setupMonthNamePaint() {
        monthNamePaint  = setupTextPaint(monthNameTextColor, monthNameTextSize, monthNameFontType, DEFAULT_ALIGN, monthNameFontTypeFace);
    }

    private void setupTodayMonthNamePaint() {
        todayMonthNamePaint  = setupTextPaint(todayMonthNameTextColor, todayMonthNameTextSize, todayMonthNameFontType, DEFAULT_ALIGN, todayMonthNameFontTypeFace);
    }

    private void setupSimpleDayNumberPaint() {
        simpleDayNumberPaint = setupTextPaint(simpleDayTextColor, simpleDayTextSize, simpleDayFontType, DEFAULT_ALIGN, simpleDayFontTypeFace);
    }

    private void setupTodayTextPaint() {
        todayTextPaint = setupTextPaint(todayTextColor, todayTextSize, todayFontType, DEFAULT_ALIGN, todayFontTypeFace);
    }

    private void setupTodayBackgroundPaint() {
        todayBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        todayBackgroundPaint.setColor(todayBackgroundColor);
        todayBackgroundPaint.setTextSize(todayTextSize);
        todayBackgroundPaint.setTextAlign(DEFAULT_ALIGN);
    }

    private void setupSelectedDayTextPaint() {
        selectedDayTextPaint = setupTextPaint(selectedDayTextColor, selectedDayTextSize, selectedDayFontType, DEFAULT_ALIGN, selectedDayTypeFace);
    }

    private void setupSelectedDayBackgroundPaint() {
        selectedDayBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedDayBackgroundPaint.setColor(selectedDayBackgroundColor);
        selectedDayBackgroundPaint.setTextSize(selectedDayTextSize);
        selectedDayBackgroundPaint.setTextAlign(DEFAULT_ALIGN);
    }

    private void setupDayNamePaint() {
        dayNamePaint = setupTextPaint(dayNameTextColor, dayNameTextSize, dayNameFontType, DEFAULT_ALIGN, dayNameFontTypeFace);
    }

    private void setupWeekendPaint() {
        weekendDayPaint = setupTextPaint(weekendTextColor, weekendTextSize, weekendFontType, DEFAULT_ALIGN, weekendFontTypeFace);
    }

    private void setupMonthSelectionPaint() {
        selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectionPaint.setColor(ColorUtils.setAlphaComponent(monthSelectionColor, SELECTION_ALPHA));
        selectionPaint.setStrokeJoin(Paint.Join.ROUND);
        selectionPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        selectionPaint.setStrokeWidth(SELECTION_STROKE);
        selectionPaint.setTextAlign(DEFAULT_ALIGN);
    }

    private Paint setupTextPaint(int textColor, int textSize, FontType fontType, Paint.Align paintAlign, Typeface typeface) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(textColor);
        paint.setTextSize(textSize);
        paint.setTextAlign(paintAlign);
        switch (fontType) {
            case BOLD:
                paint.setTypeface(typeface != null ? Typeface.create(typeface, Typeface.BOLD) : paint.setTypeface(Typeface.DEFAULT_BOLD));
                break;
            case ITALIC:
                paint.setTypeface(typeface != null ? Typeface.create(typeface, Typeface.ITALIC) : paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)));
                break;
            case BOLD_ITALIC:
                paint.setTypeface(typeface != null ? Typeface.create(typeface, Typeface.BOLD_ITALIC) : paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)));
                break;
            default: // NORMAL
                paint.setTypeface(typeface != null ? typeface : paint.setTypeface(Typeface.DEFAULT));
        }
        return paint;
    }

    public void setMonthGestureListener(MonthGestureListener monthGestureListener) {
        this.monthGestureListener = monthGestureListener;
    }

    public interface MonthGestureListener {
        //first day of the month in millis
        void onMonthClick(long timeInMillis);

        void onMonthLongClick(long timeInMillis);

        void onDayClick(long timeInMillis);

        void onDayLongClick(long timeInMillis);
    }

    /**
     * Get the firstDay at first hour of the clicked month
     *
     * @param x
     * @param y
     * @return
     */
    private long getClickedMonth(int x, int y) {
        for (int i = 0; i < originalMonthBlocks.length; i++) {
            if (originalMonthBlocks[i].contains(x, y)) {
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

    /**
     * Get the first hour of the clicked day
     *
     * @param x
     * @param y
     * @return
     */
    private long getClickedDay(int x, int y) {
        for (int i = 0; i < daysBlocks.size(); i++) {
            if (daysBlocks.get(i).first.contains(x, y)) {
                DateTimeFormatter formatter = DateTimeFormat.forPattern(DAY_PATTERN).withLocale(Locale.ROOT);
                DateTime dateTime = formatter.parseDateTime(daysBlocks.get(i).second);
                return dateTime.getMillis();
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
            boolean isDayClicked = false;
            if (monthGestureListener != null) {
                long timeInMillis = getClickedDay((int) ev.getX(), (int) ev.getY());
                isDayClicked = isDayClicked(timeInMillis);
                invalidate();
            }

            if (monthGestureListener != null && !isDayClicked) {
                long timeInMillis = getClickedMonth((int) ev.getX(), (int) ev.getY());
                if (timeInMillis != 0) {
                    monthGestureListener.onMonthClick(timeInMillis);
                    invalidate();
                }
            }
            return true;
        }

        @Override
        public void onLongPress(MotionEvent ev) {
            boolean isDayClicked = false;
            if (monthGestureListener != null) {
                long timeInMillis = getClickedDay((int) ev.getX(), (int) ev.getY());
                isDayClicked = isDayClicked(timeInMillis);
                invalidate();
            }

            if (monthGestureListener != null && !isDayClicked) {
                long timeInMillis = getClickedMonth((int) ev.getX(), (int) ev.getY());
                if (timeInMillis != 0) {
                    monthGestureListener.onMonthLongClick(timeInMillis);
                    invalidate();
                }
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

        private boolean isDayClicked(long timeInMillis) {
            boolean isDayClicked = false;
            if (timeInMillis != 0) {
                DateTimeFormatter formatter = DateTimeFormat.forPattern(DAY_PATTERN).withLocale(Locale.ROOT);
                String newSelectedDate = formatter.print(timeInMillis);
                if (isDaySelectionVisuallySticky) {
                    if (selectedDay.equals(newSelectedDate)) {
                        selectedDay = "";
                    } else {
                        selectedDay = newSelectedDate;
                        monthGestureListener.onDayClick(timeInMillis);
                    }
                } else {
                    monthGestureListener.onDayClick(timeInMillis);
                }
                isDayClicked = true;
            }
            return isDayClicked;
        }
    }
}
