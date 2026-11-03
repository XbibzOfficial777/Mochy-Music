package com.mochyy.mochymusic.ui.screens.gesture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AppTheme
import com.mochyy.mochymusic.constants.AppThemeKey
import com.mochyy.mochymusic.constants.CoverTransition
import com.mochyy.mochymusic.constants.CoverTransitionKey
import com.mochyy.mochymusic.constants.PureBlackKey
import com.mochyy.mochymusic.ui.screens.audiolab.LabCard
import com.mochyy.mochymusic.ui.screens.settings.AppThemePicker
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.PreferencesCache
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference

/** The fork's themes live in the official Customisation page after the settings refactor. */
@Composable
internal fun BibzThemeSection() {
    val context = LocalContext.current
    val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)
    val stored by rememberEnumPreference(
        AppThemeKey,
        defaultValue = AppTheme.FROSTED,
    )
    // An official look module may temporarily change PureBlackKey. Show the effective look;
    // when that module restores the key the user's glass selection comes back automatically.
    val selected = when {
        pureBlack -> AppTheme.PURE_BLACK
        stored == AppTheme.LIQUID_GLASS -> AppTheme.LIQUID_GLASS
        else -> AppTheme.FROSTED
    }
    Column {
        SectionTitle(stringResource(R.string.theme))
        Spacer(Modifier.height(14.dp))
        LabCard(padding = 16.dp) {
            AppThemePicker(selected = selected, onSelect = { theme ->
                PreferencesCache.edit(context.applicationContext.dataStore) { prefs ->
                    prefs[AppThemeKey] = theme.name
                    prefs[PureBlackKey] = theme == AppTheme.PURE_BLACK
                }
            })
            val description = when (selected) {
                AppTheme.FROSTED -> R.string.bibz_theme_frosted_desc
                AppTheme.PURE_BLACK -> R.string.bibz_theme_black_desc
                AppTheme.LIQUID_GLASS -> R.string.bibz_theme_glass_desc
            }
            Text(
                text = stringResource(description),
                color = Color.White.copy(alpha = 0.65f),
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** Slide/fade remains available after upstream combined its old Appearance screen. */
@Composable
internal fun BibzCoverTransitionSection() {
    val (choice, setChoice) = rememberEnumPreference(CoverTransitionKey, CoverTransition.SLIDE)
    Column {
        SectionTitle(stringResource(R.string.bibz_cover_animation))
        Spacer(Modifier.height(14.dp))
        LabCard(padding = 16.dp) {
            Text(
                text = stringResource(R.string.bibz_cover_animation_desc),
                color = Color.White.copy(alpha = 0.65f),
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoverTransition.entries.forEach { option ->
                    Row(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = if (choice == option) 0.14f else 0.06f))
                            .selectable(selected = choice == option, role = Role.RadioButton, onClick = { setChoice(option) })
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == option, onClick = null)
                        Text(
                            text = stringResource(if (option == CoverTransition.SLIDE) R.string.bibz_cover_slide else R.string.bibz_cover_fade),
                            color = Color.White,
                            fontFamily = InterFontFamily,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp)
}
