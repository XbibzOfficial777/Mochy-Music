package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.VideoQuality
import com.mochyy.mochymusic.ui.component.DefaultDialog

// how a quality reads in settings: "Auto", or the size itself
@Composable
internal fun videoQualityName(quality: VideoQuality): String =
    when (quality) {
        VideoQuality.AUTO -> stringResource(R.string.video_quality_auto)
        VideoQuality.Q2160 -> stringResource(R.string.bibz_quality_4k)
        else -> stringResource(R.string.video_quality_size, quality.shortSide)
    }

// picks how sharp youtube videos play, everywhere they show
@Composable
internal fun VideoQualityDialog(
    selected: VideoQuality,
    onSelect: (VideoQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.video_quality)) },
        buttons = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        horizontalAlignment = Alignment.Start,
    ) {
        val maxHeight = (LocalConfiguration.current.screenHeightDp.dp - 200.dp).coerceIn(100.dp, 360.dp)
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState()).selectableGroup()) {
            for (quality in VideoQuality.entries) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = quality == selected, role = Role.RadioButton, onClick = { onSelect(quality) })
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(selected = quality == selected, onClick = null)
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(videoQualityName(quality))
                        if (quality == VideoQuality.AUTO) {
                            Text(
                                text = stringResource(R.string.video_quality_auto_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
