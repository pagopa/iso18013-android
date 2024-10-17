package it.pagopa.iso_android.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pagopa.iso_android.ui.theme.IsoAndroidPocTheme

@Composable
fun BasePreview(content: @Composable () -> Unit) {
    IsoAndroidPocTheme {
        content()
    }
}

enum class KindOfText {
    BIG, MEDIUM, SMALL
}


@Composable
fun BigText(modifier: Modifier, text: String, color: Color) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = color
    )
}

@Composable
fun MediumText(modifier: Modifier, text: String, color: Color) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = color
    )
}

@Composable
fun SmallText(modifier: Modifier, text: String, color: Color) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

@Composable
fun CenteredComposable(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}

@Composable
fun HorizontallyCenteredText(
    modifier: Modifier,
    kindOfText: KindOfText,
    text: String,
    textColor: Color
) {
    Column(
        modifier = modifier.semantics { disabled() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (kindOfText) {
            KindOfText.BIG -> BigText(modifier = Modifier, text = text, color = textColor)
            KindOfText.MEDIUM -> MediumText(modifier = Modifier, text = text, color = textColor)
            KindOfText.SMALL -> SmallText(modifier = Modifier, text = text, color = textColor)
        }
    }
}

@Composable
fun TwoButtonsInARow(
    leftBtnText: String,
    leftBtnAction: () -> Unit,
    rightBtnText: String,
    rightBtnAction: () -> Unit
) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .weight(1f)
                .width(0.dp)
        ) {
            Button(modifier = Modifier.align(Alignment.Center), onClick = leftBtnAction) {
                Text(text = leftBtnText)
            }
        }
        Box(
            Modifier
                .weight(1f)
                .width(0.dp)
        ) {
            Button(modifier = Modifier.align(Alignment.Center), onClick = rightBtnAction) {
                Text(text = rightBtnText)
            }
        }
    }
}

@Composable
fun AnimateDottedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = MaterialTheme.colorScheme.onBackground,
    cycleDuration: Int = 1000 // Milliseconds
) {
    // Create an infinite transition
    val transition = rememberInfiniteTransition(label = "Dots Transition")

    // Define the animated value for the number of visible dots
    val visibleDotsCount = transition.animateValue(
        initialValue = 0,
        targetValue = 4,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = cycleDuration,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "Visible Dots Count"
    )

    // Display the text with dynamically changing dots based on the animation
    Text(
        text = text + ".".repeat(visibleDotsCount.value),
        color = color,
        modifier = modifier,
        fontSize = 18.sp,
        style = style
    )
}
