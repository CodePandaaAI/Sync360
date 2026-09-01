package com.liftley.sync360.presentation.send.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.designsystem.icons.Close
import com.liftley.sync360.domain.model.FileReceiveCode
import com.liftley.sync360.presentation.send.model.FileReceiveCodePrompt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileReceiveCodeSheet(
    prompt: FileReceiveCodePrompt,
    onCodeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isCodeValid = FileReceiveCode.isValid(prompt.code)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header: title + close button in one row instead of a separate
            // "Cancel" link buried under the keyboard.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sending to ${prompt.deviceName}",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Enter the code shown on that device",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss, colors = IconButtonDefaults.iconButtonColors(
                        MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Icon(
                        imageVector = Close,
                        contentDescription = "Cancel"
                    )
                }
            }

            BasicTextField(
                value = prompt.code,
                onValueChange = { raw ->
                    val digitsOnly = raw.filter { it.isDigit() }
                        .take(FileReceiveCode.DIGIT_COUNT)
                    onCodeChange(digitsOnly)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics {
                        contentDescription = "Four-digit transfer code"
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clearAndSetSemantics { },
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            repeat(FileReceiveCode.DIGIT_COUNT) { index ->
                                ReceiveCodeDigit(
                                    digit = prompt.code.getOrNull(index),
                                    isActive = index == prompt.code.length,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(0f)
                        ) {
                            innerTextField()
                        }
                    }
                }
            )

            Button(
                onClick = {
                    keyboardController?.hide()
                    onConfirm()
                },
                enabled = isCodeValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(prompt.sendButtonLabel)
            }
        }
    }
}

@Composable
private fun ReceiveCodeDigit(
    digit: Char?,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        digit != null -> MaterialTheme.colorScheme.primary
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth by animateDpAsState(
        targetValue = if (isActive || digit != null) 2.dp else 1.dp,
        label = "digitBorderWidth"
    )

    Box(
        modifier = modifier
            .height(72.dp)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit?.toString().orEmpty(),
            style = MaterialTheme.typography.displaySmall
        )
    }
}
