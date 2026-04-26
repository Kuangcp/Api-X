package app.ui

import app.AppSettings
import app.AppSettingsStore
import app.EnvironmentsState
import app.SettingsDialogWindow
import app.EnvironmentManagerDialogWindow
import app.GlobalSearchDialogWindow
import app.CollectionSettingsDialog
import app.typographyFromSettings
import androidx.compose.runtime.Composable
import db.CollectionRepository
import http.ExchangeFontMetrics
import tree.TreeSelection
import tree.UiCollection

@Composable
fun Dialogs(
    showSettings: Boolean,
    isDarkTheme: Boolean,
    appSettings: AppSettings,
    exchangeMetrics: ExchangeFontMetrics,
    onCloseSettings: () -> Unit,
    onSavedSettings: (AppSettings) -> Unit,
    showEnvironmentManager: Boolean,
    environmentsState: EnvironmentsState,
    onCloseEnvironmentManager: () -> Unit,
    onSavedEnvironments: (EnvironmentsState) -> Unit,
    showGlobalSearch: Boolean,
    tree: List<UiCollection>,
    repository: CollectionRepository,
    onCloseGlobalSearch: () -> Unit,
    onPickRequest: (String) -> Unit,
    showCollectionSettings: Boolean,
    collectionSettingsTarget: TreeSelection?,
    onCloseCollectionSettings: () -> Unit,
    onRefreshTree: () -> Unit,
) {
    if (showSettings) {
        SettingsDialogWindow(
            visible = showSettings,
            isDarkTheme = isDarkTheme,
            typographyBase = typographyFromSettings(appSettings),
            onCloseRequest = onCloseSettings,
            onSaved = { saved -> AppSettingsStore.replace(saved); onSavedSettings(saved) },
        )
    }

    if (showEnvironmentManager) {
        EnvironmentManagerDialogWindow(
            visible = showEnvironmentManager,
            isDarkTheme = isDarkTheme,
            appBackgroundHex = appSettings.backgroundHex,
            typographyBase = typographyFromSettings(appSettings),
            initial = environmentsState,
            onCloseRequest = onCloseEnvironmentManager,
            onSaved = onSavedEnvironments,
        )
    }

    if (showGlobalSearch) {
        GlobalSearchDialogWindow(
            visible = showGlobalSearch,
            isDarkTheme = isDarkTheme,
            appBackgroundHex = appSettings.backgroundHex,
            typographyBase = typographyFromSettings(appSettings),
            tree = tree,
            repository = repository,
            onCloseRequest = onCloseGlobalSearch,
            onPickRequest = onPickRequest,
        )
    }

    if (showCollectionSettings) {
        CollectionSettingsDialog(
            visible = showCollectionSettings,
            target = collectionSettingsTarget,
            repository = repository,
            isDarkTheme = isDarkTheme,
            typographyBase = typographyFromSettings(appSettings),
            exchangeMetrics = exchangeMetrics,
            onCloseRequest = {
                onCloseCollectionSettings()
                onRefreshTree()
            },
        )
    }
}