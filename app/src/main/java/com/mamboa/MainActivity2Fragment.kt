package com.mamboa

import android.os.Bundle
import android.util.Log
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.fragment.app.Fragment
import com.mamboa.yearview.compose.ComposeBackgroundStyle
import com.mamboa.yearview.compose.DayConfig
import com.mamboa.yearview.compose.LocalYearViewDebug
import com.mamboa.yearview.compose.MonthConfig
import com.mamboa.yearview.compose.YearView
import com.mamboa.yearview.compose.YearViewState
import com.mamboa.yearview.compose.rememberYearViewSelectionState
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.ImageSource
import com.mamboa.yearview.core.MergeType
import com.mamboa.yearview.core.TitleGravity
import com.mamboa.yearview.core.datetime.DayOfWeekConstants
import com.mamboa.yearview.core.datetime.timeproviders.ICalendarDateTimeProvider
import com.mamboa.yearview.core.datetime.timeproviders.KotlinxTimeProvider
import com.mamboa.yearview.core.imageprovider.ResourceImageProvider
import com.mamboa.yearview.core.pathprovider.ResourcePathProvider
import kotlinx.coroutines.launch
import java.util.Random

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
                val dateTimeProvider: ICalendarDateTimeProvider = remember { KotlinxTimeProvider() }
                val currentYear = remember { dateTimeProvider.currentYear() }

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
                                beyondViewportPageCount = 3,
                                // key = { page -> years[page] } // Add a stable key if page content identity relies on the year
                            ) { page ->
                                val year = years[page] // More direct access

                                YearViewPage(
                                    year = year,
                                    shapePath = heartPath,
                                    teddyBearsFont = teddyBearsFontFamily,
                                    callingHeartFont = callingHeartFontFamily,
                                    pinchMyRideFont = pinchMyRideFontFamily,
                                    titleGravityProvider = { getRandomTitleGravity() },
                                    monthNameColorProvider = { Color(getRandomColor()) },
                                    weekendDayColorProvider = { Color(getRandomColor()) }
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
    shapePath: Path,
    teddyBearsFont: FontFamily?,
    callingHeartFont: FontFamily?,
    pinchMyRideFont: FontFamily?,
    titleGravityProvider: () -> TitleGravity, // Pass as lambdas if they need to be dynamic per instance
    monthNameColorProvider: () -> Color,
    weekendDayColorProvider: () -> Color
) {
    val monthConfig = remember(year, teddyBearsFont, callingHeartFont, pinchMyRideFont) {
        MonthConfig(
            titleGravity = titleGravityProvider(),
            marginBelowMonthName = 4.dp,
            selectionBackgroundItemStyle = ComposeBackgroundStyle(
                color = Color(0xFF1976D2),
                shape = BackgroundShape.RoundedSquare(cornerRadius = 8f),
                opacity = 30
            ),
            backgroundItemStyle = ComposeBackgroundStyle(
                color = Color(0xFF4CAF50),
                shape = BackgroundShape.Custom(
                    provider = ResourcePathProvider(
                        drawableRes = R.drawable.heart,
                        innerPadding = R.dimen.inner_radius
                    )
                ),
                opacity = 30,
                image = ImageSource.Provided(provider = ResourceImageProvider(R.drawable.shopping)),
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
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = callingHeartFont
            ),
            nameFormat = "MMMM",
            simpleDayConfig = DayConfig(
                textStyle = TextStyle(
                    color = Color.Black,
                    fontSize = 8.sp,
                )
            ),
            weekendDayConfig = DayConfig(
                textStyle = TextStyle(
                    color = weekendDayColorProvider(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = pinchMyRideFont
                )
            )
        )
    }

    val dayNameStyle = remember {
        TextStyle(
            color = weekendDayColorProvider(),
            fontSize = 8.sp
        )
    }

    val todayConfig = remember {
        DayConfig(
            backgroundItemStyle = ComposeBackgroundStyle(
                color = Color(0xFFd10606), // Consider defining in theme
                shape = BackgroundShape.Circle(radius = 1.0f),
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
            backgroundItemStyle = ComposeBackgroundStyle(
                color = Color(0xFF4CAF50), // Consider defining in theme
                shape = BackgroundShape.Circle(radius = 1.0f),
            ),
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }

    val context = LocalContext.current // For Toasts
    val dateTimeProvider: ICalendarDateTimeProvider = remember { KotlinxTimeProvider() }

    val yearViewState = remember(year, monthConfig, dayNameStyle, todayConfig, selectedDayConfig) {
        YearViewState(
            year = year,
            rows = 4,
            columns = 3,
            firstDayOfWeek = DayOfWeekConstants.SUNDAY,
            isDaySelectionVisuallySticky = true,
            enableMultiSelection = false,
            monthConfig = monthConfig,
            dayNameStyle = dayNameStyle,
            todayConfig = todayConfig,
            selectedDayConfig = selectedDayConfig,
        )
    }

    // Selection is owned by an observable holder, so it survives configuration changes
    // and can be read or cleared from anywhere in this composable.
    val selection = rememberYearViewSelectionState(selectedDay = "2026-01-10")

    // Single YearView with debug touch-areas enabled via CompositionLocal
    CompositionLocalProvider(LocalYearViewDebug provides true) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxHeight(1f)) {
                YearView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    dateTimeProvider = dateTimeProvider,
                    state = yearViewState,
                    selection = selection,
                    onDayClick = { timestamp ->
                        val date = dateTimeProvider.fromMillis(timestamp)
                        val dayString =
                            dateTimeProvider.format(date, "yyyy-MM-dd", java.util.Locale.ROOT)
                        Log.d("Interaction", "Day clicked: $dayString (Year: $year)")
                        Toast.makeText(
                            context,
                            "Day clicked: $dayString",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onMonthClick = { timestamp ->
                        val date = dateTimeProvider.fromMillis(timestamp)
                        val monthYearString =
                            dateTimeProvider.format(date, "MMMM yyyy", java.util.Locale.ROOT)
                        Log.d("Interaction", "Month clicked: $monthYearString (Year: $year)")
                        Toast.makeText(
                            context,
                            "Month clicked: $monthYearString",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                )
            }
        }
    }
}