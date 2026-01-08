package com.mamboa

import android.os.Bundle
import android.util.Log // For performance logging
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat // For loading fonts efficiently
import androidx.fragment.app.Fragment
import com.mamboa.yearview.core.BackgroundItemStyle
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.ImageSource
import com.mamboa.yearview.core.MergeType
import com.mamboa.yearview.core.TitleGravity
import com.mamboa.yearview.compose.DayConfig
import com.mamboa.yearview.compose.MonthConfig
import com.mamboa.yearview.compose.YearView
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import java.time.DayOfWeek
import java.util.Random
import kotlin.system.measureTimeMillis

class MainActivity2Fragment : Fragment() {
    private val MIN_YEAR = 1945
    private val MAX_YEAR = 2045

    // Optimization: Instantiate Random once
    private val random = Random()

    private val heartPath by lazy {
        Path().apply {
            moveTo(50f, 25f)
            cubicTo(50f, 25f, 20f, 0f, 0f, 25f)
            cubicTo(0f, 45f, 20f, 70f, 50f, 95f)
            cubicTo(80f, 70f, 100f, 45f, 100f, 25f)
            cubicTo(80f, 0f, 50f, 25f, 50f, 25f)
        }
    }

    private var teddyBearsFontFamily: FontFamily? = null
    private var callingHeartFontFamily: FontFamily? = null
    private var pinchMyRideFontFamily: FontFamily? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pre-load fonts to avoid jank during initial composition
        context?.let { ctx ->
            teddyBearsFontFamily = FontFamily(
                Font(R.font.teddy_bears, FontWeight.Bold) // Or use ResourcesCompat for more complex loading
            )
            callingHeartFontFamily = FontFamily(
                Font(R.font.calling_heart, FontWeight.Bold)
            )
            pinchMyRideFontFamily = FontFamily(
                Font(R.font.pinch_my_ride_custom, FontWeight.Normal)
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val currentYear = remember { DateTime().year().get() } // Remember to avoid recalculation

                MaterialTheme { // Ensure MaterialTheme is correctly set up if using its components
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFFFFFFFF) // Consider defining colors in your theme
                    ) {
                        val years = remember { (MIN_YEAR..MAX_YEAR).toList() } // Remember the list
                        val pagerState = rememberPagerState(pageCount = { years.size })

                        // Scroll to the current year when the pager is first launched or if relevant keys change
                        LaunchedEffect(key1 = pagerState, key2 = currentYear) {
                            val initialPage = currentYear - MIN_YEAR
                            if (initialPage >= 0 && initialPage < pagerState.pageCount) {
                                pagerState.scrollToPage(initialPage)
                            }
                        }

                        val coroutineScope = rememberCoroutineScope()

                        Column(modifier = Modifier.fillMaxSize()) {
                            ScrollableTabRow(
                                selectedTabIndex = pagerState.currentPage,
                                edgePadding = 0.dp
                            ) {
                                years.forEachIndexed { index, year ->
                                    Tab(
                                        selected = pagerState.currentPage == index,
                                        onClick = {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        },
                                        text = { Text(text = year.toString()) }
                                    )
                                }
                            }

                            HorizontalPager(
                                state = pagerState,
                                // key = { page -> years[page] } // Add a stable key if page content identity relies on the year
                            ) { page ->
                                val year = years[page] // More direct access

                                val compositionTime = measureTimeMillis {
                                    YearViewPage(
                                        year = year,
                                        heartPath = heartPath,
                                        teddyBearsFont = teddyBearsFontFamily,
                                        callingHeartFont = callingHeartFontFamily,
                                        pinchMyRideFont = pinchMyRideFontFamily,
                                        titleGravityProvider = { getRandomTitleGravity() },
                                        monthNameColorProvider = { Color(getRandomColor()) },
                                        weekendDayColorProvider = { Color(getRandomColor()) }
                                    )
                                }
                                Log.d(
                                    "Performance",
                                    "YearViewPage for $year composed in $compositionTime ms"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getRandomTitleGravity(): TitleGravity {
        return TitleGravity.entries[random.nextInt(TitleGravity.entries.size)]
    }

    private fun getRandomColor(): Int {
        return android.graphics.Color.argb(
            255,
            random.nextInt(256),
            random.nextInt(256),
            random.nextInt(256)
        )
    }
}

@Composable
fun YearViewPage(
    year: Int,
    heartPath: Path,
    teddyBearsFont: FontFamily?,
    callingHeartFont: FontFamily?,
    pinchMyRideFont: FontFamily?,
    titleGravityProvider: () -> TitleGravity, // Pass as lambdas if they need to be dynamic per instance
    monthNameColorProvider: () -> Color,
    weekendDayColorProvider: () -> Color
) {

    val monthConfig = remember(year, teddyBearsFont, callingHeartFont) { // Add keys that affect this config
        MonthConfig(
            titleGravity = titleGravityProvider(), // Call the provider
            marginBelowMonthName = 0.dp,
            selectionBackgroundItemStyle = BackgroundItemStyle.ComposeStyle(
                color = Color(0xFF1976D2), // Consider defining in theme
                shape = BackgroundShape.RoundedSquare(cornerRadius = 8f),
                opacity = 30
            ),
            backgroundItemStyle = BackgroundItemStyle.ComposeStyle(
                color = Color(0xFF4CAF50), // Consider defining in theme
                shape = BackgroundShape.ComposeCustom(
                    composePath = heartPath, // Use the passed path
                    innerPadding = 32.dp
                ),
                opacity = 30,
                image = ImageSource.DrawableRes(com.mamboa.yearview.R.drawable.shopping),
                mergeType = MergeType.CLIP
            ),
            nameStyle = TextStyle(
                color = monthNameColorProvider(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = teddyBearsFont
            ),
            todayNameStyle = TextStyle(
                color = Color(0xffd00606),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = callingHeartFont
            ),
            nameFormat = "MMMM"
        )
    }

    val dayNameStyle = remember {
        TextStyle(
            color = Color.Black,
            fontSize = 8.sp
        )
    }

    val simpleDayStyle = remember {
        TextStyle(
            color = Color.Black,
            fontSize = 8.sp,
        )
    }

    val weekendDayStyle = remember(year, pinchMyRideFont) { // Add keys that affect this config
        TextStyle(
            color = weekendDayColorProvider(), // Call the provider
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = pinchMyRideFont // Use pre-loaded font
        )
    }

    val todayConfig = remember {
        DayConfig(
            backgroundItemStyle = BackgroundItemStyle.ComposeStyle(
                color = Color(0xFFd10606), // Consider defining in theme
                shape = BackgroundShape.Circle(radius = 8f),
            ),
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }

    val selectedDayConfig = remember {
        DayConfig(
            backgroundItemStyle = BackgroundItemStyle.ComposeStyle(
                color = Color(0xFF4CAF50), // Consider defining in theme
                shape = BackgroundShape.Circle(radius = 8f),
            ),
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }

    val context = LocalContext.current // For Toasts

    // Our page content
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxHeight(1f)) {
            YearView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                year = year,
                rows = 4, // Consider if these can be dynamic or are fixed
                columns = 3,
                arbitrarySelectedDay = "01-10-2025", // If this changes often, it will cause recomposition
                firstDayOfWeek = DayOfWeek.SUNDAY.value,
                verticalSpacing = 16.dp,
                horizontalSpacing = 16.dp,
                isDaySelectionVisuallySticky = true,
                enableMultiSelection = false,
                monthConfig = monthConfig, // Use remembered config
                dayNameStyle = dayNameStyle, // Use remembered style
                simpleDayStyle = simpleDayStyle, // Use remembered style
                weekendDayStyle = weekendDayStyle, // Use remembered style
                todayConfig = todayConfig, // Use remembered config
                selectedDayConfig = selectedDayConfig, // Use remembered config
                onDayClick = { timestamp ->
                    val dateTime = DateTime(timestamp)
                    val dayString = dateTime.toString("yyyy-MM-dd")
                    Log.d("Interaction", "Day clicked: $dayString (Year: $year)")
                    Toast.makeText(
                        context, // Use context from LocalContext.current
                        "Day clicked: $dayString",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onMonthClick = { timestamp ->
                    val dateTime = DateTime(timestamp)
                    val monthYearString = dateTime.toString("MMMM yyyy")
                    Log.d("Interaction", "Month clicked: $monthYearString (Year: $year)")
                    Toast.makeText(
                        context, // Use context from LocalContext.current
                        "Month clicked: $monthYearString",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }
}