# Android-calendar-YearView
Attempt to reproduce the Year view of Samsung's Android Calendar. It uses Joda-Time (https://github.com/JodaOrg/joda-time)

![Click here to view the demo](https://github.com/maxime-kouemo/Android-calendar-YearView/tree/master/demo_files/demo_year_view.gif)

Some implemented features:
- Set the number of columns and the number of rows. (columns * rows should always = 12)
- change the vertical and horizontal spacing between months
- Set the first day of the week (monday, tuesday, .. sunday)
- customize the day of today (background colors, background shapes(square, circle),  text color, font type (bold, bold italic, italic, normal), typeface, text size)
- Select weekend your own weekend days
- Customize the weekend days (text color, font type (bold, bold italic, italic, normal), typeface, text size)
- Customize the month names (text color, gravity ex: left/start, center, right/end, distance below month name, font type (bold, bold italic, italic, normal), typeface, text size)
- Customize today's month name (text color, font type (bold, bold italic, italic, normal), typeface, text size)
- Customize the week names (text color, ont type (bold, bold italic, italic, normal), typeface, text size)
- Customize simple days represented by number (text color, ont type (bold, bold italic, italic, normal), typeface, text size)
- long click listener on a month, the animation color when the user clicks on a month is customizable 
- simple click listener on a month, the animation color when the user clicks on a month is customizable 
- Allow the typefaces to be changed at runtime with accessors

Almost everything is customizable. The code below can be seen in the *year_fragment.xml* .
```
<mamboa.yearview.YearView
        android:id="@+id/yearView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_marginStart="8dp"
        android:layout_marginLeft="8dp"
        android:layout_marginTop="8dp"
        android:layout_marginEnd="8dp"
        android:layout_marginRight="8dp"
        android:layout_marginBottom="8dp"
        app:columns="3"
        app:current_year="2019"
        app:day_name_font="@null"
        app:day_name_font_type="italic"
        app:day_name_text_color="@android:color/holo_orange_dark"
        app:day_name_text_size="10sp"
        app:firstDayOfWeek="sunday"
        app:horizontal_spacing="15"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:margin_below_month_name="10"
        app:month_name_font="@font/teddy_bears"
        app:month_name_font_type="bold_italic"
        app:month_name_text_color="@android:color/holo_blue_dark"
        app:month_name_text_size="14sp"
        app:month_selection_color="@android:color/holo_purple"
        app:month_selection_margin="20"
        app:month_title_gravity="start"
        app:name_week_transcend_weekend="false"
        app:rows="4"
        app:simple_day_font="@null"
        app:simple_day_font_type="bold_italic"
        app:simple_day_text_color="@android:color/black"
        app:simple_day_text_size="10sp"
        app:today_background_color="#d10606"
        app:today_background_radius="2"
        app:today_background_shape="circle"
        app:today_font="@null"
        app:today_font_type="bold"
        app:today_month_name_font="@font/calling_heart"
        app:today_month_name_font_type="bold"
        app:today_month_name_text_color="#d00606"
        app:today_month_name_text_size="14sp"
        app:today_text_color="#fff"
        app:today_text_size="14sp"
        app:vertical_spacing="10"
        app:weekend_days="@array/weekendDays"
        app:weekend_font="@font/pinch_my_ride_custom"
        app:weekend_font_type="normal"
        app:weekend_text_color="@android:color/holo_green_dark"
        app:weekend_text_size="10sp" />
```



What is remaining to be done:
- unit test
- compiling into a library
