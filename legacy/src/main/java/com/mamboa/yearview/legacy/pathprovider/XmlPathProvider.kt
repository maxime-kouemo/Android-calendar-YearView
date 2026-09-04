package com.mamboa.yearview.legacy.pathprovider

import android.graphics.Path
import androidx.annotation.DimenRes
import com.mamboa.yearview.core.CustomShapeProvider

class XmlPathProvider(
    val path: Path,
    @DimenRes val innerPadding: Int = 0
): CustomShapeProvider
