# Android-calendar-YearView
Attempt to reproduce the Year view of Samsung's Android Calendar.

Some features:
- Set the number of columns and the number of rows. (columns * rows should always = 12)
- change the vertical and horizontal spacing between months
- Set the first day of the week (monday, tuesday, .. sunday)
- customize the day of today (background colors, background shapes(square, circle),  text color, font type (bold, bold italic, italic, normal), typeface)
- Select weekend your own weekend days
- Customize the weekend days (text color, font type (bold, bold italic, italic, normal), typeface)
- Customize the month names (text color, gravity ex: left/start, center, right/end, distance below month name, font type (bold, bold italic, italic, normal), typeface)
- Customize the week names (text color, ont type (bold, bold italic, italic, normal))
- long click listener on a month, the animation color when the user clicks on a month is customizable 
- simple click listener on a month, the animation color when the user clicks on a month is customizable 

What is remaining to be done:
- Allow the typefaces to be changed at runtime with accessors
- Add parameters to change the size of each group of text
- unit test
- compiling into a library
