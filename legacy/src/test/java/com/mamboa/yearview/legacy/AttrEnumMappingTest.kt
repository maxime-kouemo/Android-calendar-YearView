package com.mamboa.yearview.legacy

import com.mamboa.yearview.core.FontType
import com.mamboa.yearview.core.MergeType
import com.mamboa.yearview.core.TitleGravity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the contract between `attrs.xml` `<enum>` values and the Kotlin enums
 * they are read back into.
 *
 * `YearView` resolves these attributes by **ordinal** — indexing into `FontType.entries`
 * with the raw integer — so an XML value that does not match its declaration order silently
 * selects the wrong constant — there is no compiler or lint check for this.
 * That is precisely how `month_title_gravity`'s `left` and `start` came to be
 * swapped, mirroring gravity in RTL when the caller asked for a fixed edge.
 */
class AttrEnumMappingTest {

    @Test
    fun `month_title_gravity values match TitleGravity ordinals`() {
        assertMatchesOrdinals("yv_month_title_gravity", TitleGravity.entries.map { it.name })
    }

    /**
     * Attribute names share one global namespace with the consuming app and every other
     * library it depends on, so an unprefixed generic name such as `rows` or `debug_mode`
     * surfaces as a resource-merge failure in the *consumer's* build, not here.
     */
    @Test
    fun `every attribute is namespaced with the yv prefix`() {
        val unprefixed = attributeElements()
            .map { it.getAttribute("name") }
            .filterNot { it.startsWith("yv_") }

        assertTrue(
            "these attrs.xml entries are missing the `yv_` prefix and can collide with " +
                "another library: $unprefixed",
            unprefixed.isEmpty()
        )
    }

    /**
     * Anything that is a real on-screen length must be `format="dimension"`.
     *
     * These were `format="integer"`, i.e. raw pixels, so the same layout rendered at
     * wildly different physical sizes between an mdpi and an xxhdpi screen.
     */
    @Test
    fun `length attributes are declared as dimensions`() {
        val lengthAttrs = listOf(
            "yv_vertical_spacing",
            "yv_horizontal_spacing",
            "yv_margin_below_month_name",
            "yv_month_selection_margin",
            "yv_today_background_radius",
            "yv_selected_day_background_radius",
            "yv_multi_selection_background_radius"
        )
        val byName = attributeElements().associateBy { it.getAttribute("name") }

        lengthAttrs.forEach { name ->
            val attr = byName[name] ?: error("attribute '$name' not found in attrs.xml")
            assertEquals(
                "$name is a length and must be format=\"dimension\" so it scales with density",
                "dimension",
                attr.getAttribute("format")
            )
        }
    }

    @Test
    fun `every font_type attribute matches FontType ordinals`() {
        val expected = FontType.entries.map { it.name }
        val fontTypeAttrs = enumAttributeNames().filter { it.endsWith("font_type") }

        assertTrue("expected to find font_type attributes", fontTypeAttrs.isNotEmpty())
        fontTypeAttrs.forEach { assertMatchesOrdinals(it, expected) }
    }

    @Test
    fun `every merge_type attribute matches MergeType ordinals`() {
        val expected = MergeType.entries.map { it.name }
        val mergeTypeAttrs = enumAttributeNames().filter { it.endsWith("merge_type") }

        assertTrue("expected to find merge_type attributes", mergeTypeAttrs.isNotEmpty())
        mergeTypeAttrs.forEach { assertMatchesOrdinals(it, expected) }
    }

    @Test
    fun `every shape attribute matches the BackgroundShapeCode constants`() {
        val expected = mapOf(
            "circle" to BackgroundShapeCode.CIRCLE,
            "square" to BackgroundShapeCode.SQUARE,
            "rounded_square" to BackgroundShapeCode.ROUNDED_SQUARE,
            "star" to BackgroundShapeCode.STAR,
            "custom" to BackgroundShapeCode.CUSTOM
        )
        val shapeAttrs = enumAttributeNames().filter { it.endsWith("shape") }

        assertTrue("expected to find shape attributes", shapeAttrs.isNotEmpty())
        shapeAttrs.forEach { attr ->
            assertEquals("$attr does not match BackgroundShapeCode", expected, enumValuesOf(attr))
        }
    }

    @Test
    fun `firstDayOfWeek uses ISO day numbering`() {
        assertEquals(
            mapOf(
                "monday" to 1,
                "tuesday" to 2,
                "wednesday" to 3,
                "thursday" to 4,
                "friday" to 5,
                "saturday" to 6,
                "sunday" to 7
            ),
            enumValuesOf("yv_first_day_of_week")
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Asserts that every `enum` element in the attribute declares a value equal to the
     * position of the matching constant in [enumNames], comparing the XML's snake_case
     * name against the Kotlin UPPER_SNAKE_CASE one.
     */
    private fun assertMatchesOrdinals(attributeName: String, enumNames: List<String>) {
        val declared = enumValuesOf(attributeName)

        assertEquals(
            "$attributeName declares a different number of values than the Kotlin enum",
            enumNames.size,
            declared.size
        )

        declared.forEach { (xmlName, ordinal) ->
            assertTrue(
                "$attributeName: value $ordinal for '$xmlName' is outside the enum range",
                ordinal in enumNames.indices
            )
            assertEquals(
                "$attributeName: XML '$xmlName' = $ordinal but ordinal $ordinal is " +
                    "${enumNames[ordinal]}. Fix attrs.xml to match the Kotlin declaration order.",
                enumNames[ordinal],
                xmlName.uppercase()
            )
        }
    }

    private fun enumValuesOf(attributeName: String): Map<String, Int> {
        val attr = attributeElements().firstOrNull { it.getAttribute("name") == attributeName }
            ?: error("attribute '$attributeName' not found in attrs.xml")

        val enums = attr.getElementsByTagName("enum")
        return (0 until enums.length).associate { i ->
            val element = enums.item(i) as Element
            element.getAttribute("name") to element.getAttribute("value").toInt()
        }
    }

    private fun enumAttributeNames(): List<String> =
        attributeElements()
            .filter { it.getElementsByTagName("enum").length > 0 }
            .map { it.getAttribute("name") }

    private fun attributeElements(): List<Element> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(attrsFile())
        val nodes = document.getElementsByTagName("attr")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun attrsFile(): File {
        val candidates = listOf(
            File("src/main/res/values/attrs.xml"),
            File("legacy/src/main/res/values/attrs.xml"),
            File("../legacy/src/main/res/values/attrs.xml")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error(
                "attrs.xml not found. Working directory is ${File("").absolutePath}; " +
                    "tried ${candidates.joinToString { it.path }}"
            )
    }
}
