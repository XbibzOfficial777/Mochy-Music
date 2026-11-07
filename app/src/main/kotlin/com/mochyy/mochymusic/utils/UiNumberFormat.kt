package com.mochyy.mochymusic.utils

import java.util.Locale

/**
 * Numbers that are shown to the user, formatted with an explicit locale.
 *
 * `String.format(...)` without a `Locale` silently falls back to the device default. Android lint
 * flags that as `DefaultLocale` because the call site cannot say whether the fallback was intended,
 * and because it is a common source of bugs when the same string is also parsed back or compared.
 *
 * Every readout that goes through here is display text (EQ gains, storage sizes, release dates,
 * equaliser percentages), so the user's formatting locale is the intended one. [Locale.Category.FORMAT]
 * is used on purpose instead of the display locale: it follows the user's number/region preference,
 * so a listener who reads the app in English but lives in a comma-decimal country still gets the
 * numbers their other apps show.
 *
 * Centralising it here keeps the decision in one place instead of repeating `Locale` arguments at
 * ~30 call sites, and it means a future change (for example trimming trailing zeros for gains) is
 * applied everywhere at once.
 */
object UiNumberFormat {

    /** `String.format` with the user's formatting locale applied explicitly. */
    fun decimal(pattern: String, vararg args: Any): String =
        String.format(Locale.getDefault(Locale.Category.FORMAT), pattern, *args)
}
