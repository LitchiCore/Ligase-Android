package com.limelight.ligase.feature.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limelight.R
import com.limelight.ligase.LigaseLanguageMode
import com.limelight.ligase.LigasePageScaffold
import com.limelight.ligase.LigaseSemanticTheme
import com.limelight.ligase.LigaseThemeMode
import com.limelight.ligase.feature.settings.presentation.SettingsUiState
import com.limelight.ligase.inputTitle
import com.limelight.ligase.library.messageResource
import com.limelight.ligase.ligaseNavigationContentBottomPadding

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onOpenInput: () -> Unit,
    onThemeSelected: (LigaseThemeMode) -> Unit,
    onLanguageSelected: (LigaseLanguageMode) -> Unit,
    onGlobalResolutionClick: () -> Unit,
    onAdvancedSettings: () -> Unit,
) {
    LigasePageScaffold(stringResource(R.string.ligase_settings_title)) { pageModifier ->
        val bottomPadding = ligaseNavigationContentBottomPadding()
        LazyColumn(
            modifier = pageModifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                top = 20.dp,
                end = 20.dp,
                bottom = bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionTitle(R.string.ligase_settings_input_title)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = state.inputEnabled, onClick = onOpenInput),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ligase_touch),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(
                                text = stringResource(inputTitle(state.selectedInput)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.ligase_settings_input_summary),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                SectionTitle(R.string.ligase_streaming_settings_title)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = state.resolutionEnabled,
                            onClick = onGlobalResolutionClick,
                        ),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (state.resolutionEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            LigaseSemanticTheme.colors.disabled
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ligase_monitor),
                            contentDescription = null,
                            tint = if (state.hasResolutionContent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LigaseSemanticTheme.colors.disabled
                            },
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.ligase_global_resolution),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = state.globalResolution?.label
                                    ?: stringResource(R.string.ligase_sync_unavailable_short),
                                color = if (state.hasResolutionContent) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    LigaseSemanticTheme.colors.disabled
                                },
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(state.hdrState.reason.messageResource()),
                                color = if (state.hasResolutionContent) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    LigaseSemanticTheme.colors.disabled
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                SectionTitle(R.string.ligase_settings_appearance_title)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeChoice(
                        mode = LigaseThemeMode.SYSTEM,
                        selected = state.themeMode == LigaseThemeMode.SYSTEM,
                        label = R.string.ligase_theme_system,
                        onClick = onThemeSelected,
                    )
                    ThemeChoice(
                        mode = LigaseThemeMode.LIGHT,
                        selected = state.themeMode == LigaseThemeMode.LIGHT,
                        label = R.string.ligase_theme_light,
                        onClick = onThemeSelected,
                    )
                    ThemeChoice(
                        mode = LigaseThemeMode.DARK,
                        selected = state.themeMode == LigaseThemeMode.DARK,
                        label = R.string.ligase_theme_dark,
                        onClick = onThemeSelected,
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                SectionTitle(R.string.ligase_settings_language_title)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LanguageChoice(
                        mode = LigaseLanguageMode.SYSTEM,
                        selected = state.languageMode == LigaseLanguageMode.SYSTEM,
                        label = R.string.ligase_language_system,
                        onClick = onLanguageSelected,
                    )
                    LanguageChoice(
                        mode = LigaseLanguageMode.SIMPLIFIED_CHINESE,
                        selected = state.languageMode == LigaseLanguageMode.SIMPLIFIED_CHINESE,
                        label = R.string.ligase_language_chinese,
                        onClick = onLanguageSelected,
                    )
                    LanguageChoice(
                        mode = LigaseLanguageMode.ENGLISH,
                        selected = state.languageMode == LigaseLanguageMode.ENGLISH,
                        label = R.string.ligase_language_english,
                        onClick = onLanguageSelected,
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(18.dp))
                SectionTitle(R.string.ligase_advanced_title)
                Text(
                    text = stringResource(R.string.ligase_advanced_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onAdvancedSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(stringResource(R.string.ligase_advanced_action))
                }
            }
        }
    }
}

@Composable
private fun RowScope.ThemeChoice(
    mode: LigaseThemeMode,
    selected: Boolean,
    @StringRes label: Int,
    onClick: (LigaseThemeMode) -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = { onClick(mode) },
        label = {
            Text(
                text = stringResource(label),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = LigaseSemanticTheme.colors.selected,
            selectedLabelColor = LigaseSemanticTheme.colors.textPrimary,
            selectedLeadingIconColor = LigaseSemanticTheme.colors.brandPrimary,
            disabledLabelColor = LigaseSemanticTheme.colors.disabled,
        ),
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun RowScope.LanguageChoice(
    mode: LigaseLanguageMode,
    selected: Boolean,
    @StringRes label: Int,
    onClick: (LigaseLanguageMode) -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = { onClick(mode) },
        label = {
            Text(
                text = stringResource(label),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = LigaseSemanticTheme.colors.selected,
            selectedLabelColor = LigaseSemanticTheme.colors.textPrimary,
            selectedLeadingIconColor = LigaseSemanticTheme.colors.brandPrimary,
            disabledLabelColor = LigaseSemanticTheme.colors.disabled,
        ),
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}
