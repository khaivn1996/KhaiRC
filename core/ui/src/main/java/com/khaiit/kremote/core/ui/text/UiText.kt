package com.khaiit.kremote.core.ui.text

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val value: String) : UiText
    data class Join(val parts: List<UiText>, val separator: String = ", ") : UiText
}

fun uiText(@StringRes id: Int, vararg args: Any) = UiText.Res(id, args.toList())

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Join -> parts.map { it.asString() }.joinToString(separator)
    is UiText.Res -> {
        if (args.isEmpty()) {
            stringResource(id)
        } else {
            val resolved = args.map { if (it is UiText) it.asString() else it }
            stringResource(id, *resolved.toTypedArray())
        }
    }
}
