package com.mamboa.yearview.compose

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.ui.graphics.Path

/**
 * Extracts a Compose [Path] from a vector drawable resource by parsing its XML pathData.
 *
 * @param context The Android context used to access resources.
 * @param resId The drawable resource ID of the vector drawable.
 * @return The extracted [Path], or null if the drawable cannot be parsed.
 */
@SuppressLint("ResourceType")
internal fun extractPathFromVectorResource(
    context: Context,
    @androidx.annotation.DrawableRes resId: Int
): Path? {
    return try {
        val parser = context.resources.getXml(resId)
        val pathDataList = mutableListOf<String>()

        var eventType = parser.eventType
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "path") {
                for (i in 0 until parser.attributeCount) {
                    if (parser.getAttributeName(i) == "pathData") {
                        pathDataList.add(parser.getAttributeValue(i))
                    }
                }
            }
            eventType = parser.next()
        }
        parser.close()

        if (pathDataList.isEmpty()) return null

        val composePath = Path()
        for (pathData in pathDataList) {
            val nodes = androidx.compose.ui.graphics.vector.PathParser()
                .parsePathString(pathData)
                .toPath()
            composePath.addPath(nodes)
        }
        composePath
    } catch (_: org.xmlpull.v1.XmlPullParserException) {
        null
    } catch (_: java.io.IOException) {
        null
    } catch (_: android.content.res.Resources.NotFoundException) {
        null
    }
}
