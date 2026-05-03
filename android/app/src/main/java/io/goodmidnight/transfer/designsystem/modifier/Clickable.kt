package io.goodmidnight.transfer.designsystem.modifier

import android.annotation.SuppressLint
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * [throttleClickable]
 * - An extension modifier that prevents consecutive rapid click interactions (debouncing/throttling).
 *
 * @param enabled Controls the enabled state of the click interaction.
 * @param onClickLabel Semantic label for the click interaction.
 * @param role The type of user interface element.
 * @param coroutineContext The coroutine context used for launching the click action.
 * @param throttleTime The delay (in milliseconds) required between valid clicks.
 * @param onClick The callback to be invoked when the component is clicked.
 */
@SuppressLint("ModifierFactoryUnreferencedReceiver")
fun Modifier.throttleClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    coroutineContext: CoroutineContext = Dispatchers.Main,
    throttleTime: Long = 250L,
    onClick: () -> Unit,
) = composed {
    val coroutineScope = rememberCoroutineScope { coroutineContext }
    var lastEmissionTime: Long by remember { mutableLongStateOf(0L) }

    clickable(
        onClick = {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastEmissionTime >= throttleTime) {
                coroutineScope.launch {
                    lastEmissionTime = currentTime
                    onClick()
                }
            }
            lastEmissionTime = currentTime
        },
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
    )
}

/**
 * [throttleClickable] (Overload with InteractionSource)
 * - An extension modifier that prevents consecutive rapid click interactions,
 *   while also supporting custom interaction sources and indications (like removing ripples).
 *
 * @param interactionSource The interaction source used to track state changes.
 * @param indication The visual indication drawn when the component is clicked (e.g., ripple).
 * @param enabled Controls the enabled state of the click interaction.
 * @param onClickLabel Semantic label for the click interaction.
 * @param role The type of user interface element.
 * @param coroutineContext The coroutine context used for launching the click action.
 * @param throttleTime The delay (in milliseconds) required between valid clicks.
 * @param onClick The callback to be invoked when the component is clicked.
 */
fun Modifier.throttleClickable(
    interactionSource: MutableInteractionSource = MutableInteractionSource(),
    indication: Indication? = null,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    coroutineContext: CoroutineContext = Dispatchers.Main,
    throttleTime: Long = 250L,
    onClick: () -> Unit,
) = composed {
    val coroutineScope = rememberCoroutineScope { coroutineContext }
    var lastEmissionTime: Long by remember { mutableLongStateOf(0L) }

    noRippleClickable(
        onClick = {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastEmissionTime >= throttleTime) {
                coroutineScope.launch {
                    lastEmissionTime = currentTime
                    onClick()
                }
            }
            lastEmissionTime = currentTime
        },
        interactionSource = remember { interactionSource },
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role
    )
}

/**
 * [noRippleClickable]
 * - An extension modifier that adds click behavior without the default visual ripple effect.
 */
@SuppressLint("ModifierFactoryUnreferencedReceiver")
fun Modifier.noRippleClickable(
    interactionSource: MutableInteractionSource = MutableInteractionSource(),
    indication: Indication? = null,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
) = composed {
    clickable(
        interactionSource = remember { interactionSource },
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        onClick = onClick,
        role = role
    )
}