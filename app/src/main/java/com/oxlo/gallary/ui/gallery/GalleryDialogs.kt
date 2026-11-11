package com.oxlo.gallary.ui.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.oxlo.gallary.data.db.TagEntity
import com.oxlo.gallary.ui.components.RatingStars

@Composable
fun TagPickerDialog(
    existingTags: List<TagEntity>,
    title: String = "Add tag",
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("e.g. Holiday, Family, Work") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (existingTags.isNotEmpty()) {
                    Text(
                        text = "Existing tags",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        existingTags.take(12).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { text = tag.name }
                            ) {
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSubmit(text.trim())
                        onDismiss()
                    }
                },
                enabled = text.isNotBlank()
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RatingDialog(
    title: String,
    initialRating: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var rating by remember { mutableStateOf(initialRating) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                RatingStars(
                    rating = rating,
                    onRatingChange = { rating = it },
                    starSize = 34.dp
                )
                Text(
                    text = when (rating) {
                        0 -> "No rating"
                        1 -> "★ Poor"
                        2 -> "★★ Fair"
                        3 -> "★★★ Good"
                        4 -> "★★★★ Great"
                        else -> "★★★★★ Favourite"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(rating)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TagColorRow(selected: Long, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TAG_COLORS.forEach { color ->
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(color),
                modifier = Modifier
                    .clickable { onSelect(color) }
                    .padding(1.dp)
            ) {
                Text(
                    text = if (selected == color) "✓" else "",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

val TAG_COLORS = listOf(
    0xFF7C5CFF,
    0xFF38E1C4,
    0xFFFF7A9A,
    0xFFFFC542,
    0xFF4FA3FF,
    0xFF9BE15D,
    0xFFFF9F5A,
    0xFFB388FF
)

@Composable
fun PersonNameDialog(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Give this face a name so you can find every photo of them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSubmit(text.trim())
                        onDismiss()
                    }
                },
                enabled = text.isNotBlank()
            ) { Text("Tag") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
