package com.mochyy.mochymusic.utils

import org.json.JSONObject

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key)
