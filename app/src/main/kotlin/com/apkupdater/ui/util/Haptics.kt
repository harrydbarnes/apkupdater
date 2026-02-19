package com.apkupdater.ui.util

import android.view.HapticFeedbackConstants
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

val HapticFeedbackType.Companion.ClockTick: HapticFeedbackType
    get() = HapticFeedbackType(HapticFeedbackConstants.CLOCK_TICK)

val HapticFeedbackType.Companion.VirtualKey: HapticFeedbackType
    get() = HapticFeedbackType(HapticFeedbackConstants.VIRTUAL_KEY)
