package com.zhongjh.multimedia.locale

import android.content.Context

object AlbumLocale {

    @JvmStatic
    var contextWrapper: ((Context) -> Context)? = null

    fun wrap(base: Context): Context =
        contextWrapper?.invoke(base) ?: base
}
