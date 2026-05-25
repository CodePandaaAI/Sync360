package com.liftley.sync360.features.sync.presentation

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun formatTimestampHourMinute(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
