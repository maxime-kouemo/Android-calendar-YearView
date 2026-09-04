package com.mamboa.yearview.compose.pathprovider

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mamboa.yearview.core.CustomShapeProvider

class ComposePathProvider(
    val path: Path,
    val innerPadding: Dp = 0.dp
) : CustomShapeProvider