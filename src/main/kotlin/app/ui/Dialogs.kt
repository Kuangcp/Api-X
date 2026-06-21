package app.ui

import app.settings.AppSettings
import app.settings.AppSettingsStore
import app.settings.EnvironmentsState
import app.settings.SettingsDialogWindow
import app.dialog.EnvironmentManagerDialogWindow
import app.dialog.GlobalSearchDialogWindow
import app.dialog.OpenApiImportDialog
import app.dialog.CollectionSettingsDialog
import app.ui.typographyFromSettings
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
    appSettingsStore: AppSettingsStore,
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
    showCreateCollection: Boolean,
    onCloseCreateCollection: () -> Unit,
    onCreateCollection: (String, String) -> Unit,
) {
    if (showCreateCollection) {
        OpenApiImportDialog(
            visible = showCreateCollection,
            isDarkTheme = isDarkTheme,
            typographyBase = typographyFromSettings(appSettings),
            onCloseRequest = onCloseCreateCollection,
            onCreate = { name, openApiUrl ->
                onCloseCreateCollection()
                onCreateCollection(name, openApiUrl)
            },
        )
    }
    if (showSettings) {
        SettingsDialogWindow(
            visible = showSettings,
            isDarkTheme = isDarkTheme,
            typographyBase = typographyFromSettings(appSettings),
            initial = appSettings,
            onCloseRequest = onCloseSettings,
            onSaved = { saved -> appSettingsStore.replace(saved); onSavedSettings(saved) },
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