package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.mochyy.mochymusic.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.constants.VideoQuality

@Composable
internal fun VideoQualityDialog(
    selected: VideoQuality,
    onSelect: (VideoQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF202024),
        title = { Text(stringResource(R.string.bibz_video_quality), color = Color.White) },
        text = {
            Column {
                Text(
                    stringResource(R.string.bibz_video_quality_dialog),
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
                    VideoQuality.entries.forEach { quality ->
                        Row(
                            modifier = Modifier.fillMaxWidth().selectable(
                                selected = quality == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(quality) },
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = quality == selected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color.White,
                                    unselectedColor = Color.White.copy(alpha = 0.6f),
                                ),
                            )
                            Text(quality.label, color = Color.White, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), color = Color.White) } },
    )
}
