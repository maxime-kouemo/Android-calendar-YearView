# Android-calendar-YearView
Attempt to reproduce the Year view of Samsung's Android Calendar. It uses Joda-Time (https://github.com/JodaOrg/joda-time)

Some features:
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

What is remaining to be done:
- unit test
- compiling into a library
