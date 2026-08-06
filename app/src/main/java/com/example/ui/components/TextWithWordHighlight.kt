package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.HighlightRange
import com.example.ui.theme.HighlightTextOnYellow
import com.example.ui.theme.HighlightYellow

@Composable
fun InteractiveHighlightedText(
    text: String,
    highlightRange: HighlightRange,
    isSpeaking: Boolean,
    onWordClick: (startIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary

    val annotatedString = buildAnnotatedString {
        if (text.isBlank()) {
            withStyle(SpanStyle(color = textColor.copy(alpha = 0.5f))) {
                append("No text loaded. Take a photo, pick an image from gallery, or type text above.")
            }
        } else {
            val start = highlightRange.start.coerceIn(0, text.length)
            val end = highlightRange.end.coerceIn(start, text.length)

            if (isSpeaking && start < end) {
                // Text before highlight
                if (start > 0) {
                    append(text.substring(0, start))
                }
                // Highlighted word
                withStyle(
                    SpanStyle(
                        background = HighlightYellow,
                        color = HighlightTextOnYellow,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(text.substring(start, end))
                }
                // Text after highlight
                if (end < text.length) {
                    append(text.substring(end))
                }
            } else {
                append(text)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, primaryColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        ClickableText(
            text = annotatedString,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 18.sp,
                lineHeight = 26.sp,
                color = textColor
            ),
            onClick = { offset ->
                if (text.isNotBlank() && offset in text.indices) {
                    // Find start of the word clicked
                    var wordStart = offset
                    while (wordStart > 0 && !text[wordStart - 1].isWhitespace()) {
                        wordStart--
                    }
                    onWordClick(wordStart)
                }
            }
        )
    }
}
