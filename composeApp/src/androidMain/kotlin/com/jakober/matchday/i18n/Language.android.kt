package com.jakober.matchday.i18n

import java.util.Locale

actual fun deviceLanguage(): String = Locale.getDefault().language
