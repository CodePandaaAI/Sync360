package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.designsystem.Spacing
import com.liftley.sync360.core.designsystem.SyncDimens
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.presentation.SyncUiState
import com.liftley.sync360.features.sync.presentation.SyncEvent
import com.liftley.sync360.core.platform.FilePickerKind

@Composable
fun SharePanel(
    isDesktop: Boolean,
    uiState: SyncUiState,
    activeDevice: DeviceProfile,
    onEvent: (SyncEvent) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    if (isDesktop) {
        // Desktop Transmission Panel
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Transmission Panel",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                
                OutlinedTextField(
                    value = uiState.outgoingText,
                    onValueChange = { onEvent(SyncEvent.UpdateOutgoingText(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Write or paste text to broadcast to ${activeDevice.name}…") },
                    minLines = 4,
                    maxLines = 6,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outlineVariant,
                        focusedContainerColor = colorScheme.surface,
                        unfocusedContainerColor = colorScheme.surface
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onEvent(SyncEvent.PasteFromClipboard) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste Clipboard",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste Clipboard", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onEvent(SyncEvent.SendMessage(uiState.outgoingText)) },
                        enabled = uiState.outgoingText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("Broadcast Text", fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Local File Picking Controls
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Send Local Media & Files",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onEvent(SyncEvent.OpenFilePicker(FilePickerKind.Media)) },
                            shape = RoundedCornerShape(14.dp),
                            color = colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Media File Icon",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Image / Video File", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onEvent(SyncEvent.OpenFilePicker(FilePickerKind.Any)) },
                            shape = RoundedCornerShape(14.dp),
                            color = colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Document Icon",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Any Document / File", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SyncDimens.cornerMedium),
            color = colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = "Share with ${activeDevice.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )

                OutlinedTextField(
                    value = uiState.outgoingText,
                    onValueChange = { onEvent(SyncEvent.UpdateOutgoingText(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Type something to share…",
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(SyncDimens.cornerSmall),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outlineVariant,
                        focusedContainerColor = colorScheme.surface,
                        unfocusedContainerColor = colorScheme.surface
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)
                ) {
                    OutlinedButton(
                        onClick = { onEvent(SyncEvent.PasteFromClipboard) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(SyncDimens.cornerSmall),
                        contentPadding = PaddingValues(vertical = Spacing.sm + Spacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste from clipboard",
                            modifier = Modifier.size(Spacing.lg)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs + 2.dp))
                        Text("Paste", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { onEvent(SyncEvent.SendMessage(uiState.outgoingText)) },
                        enabled = uiState.outgoingText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(SyncDimens.cornerSmall),
                        contentPadding = PaddingValues(vertical = Spacing.sm + Spacing.xs)
                    ) {
                        Text("Send", fontWeight = FontWeight.SemiBold)
                    }
                }

                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = "Send Local Media & Files",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)
                    ) {
                        FilePickerTile(
                            icon = Icons.Default.PermMedia,
                            label = "Image / Video",
                            contentDescription = "Pick image or video",
                            onClick = { onEvent(SyncEvent.OpenFilePicker(FilePickerKind.Media)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilePickerTile(
                            icon = Icons.Default.Folder,
                            label = "Any File",
                            contentDescription = "Pick any file",
                            onClick = { onEvent(SyncEvent.OpenFilePicker(FilePickerKind.Any)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
