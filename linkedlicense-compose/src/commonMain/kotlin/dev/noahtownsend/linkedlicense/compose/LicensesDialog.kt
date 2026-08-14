package dev.noahtownsend.linkedlicense.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.noahtownsend.linkedlicense.License
import dev.noahtownsend.linkedlicense.compose.generated.resources.Res
import dev.noahtownsend.linkedlicense.compose.generated.resources.licenses
import org.jetbrains.compose.resources.stringResource

/**
 * Test tag applied to the close [IconButton] in [LicensesDialog]'s top bar, for UI tests that
 * need to find it without depending on locale-specific content descriptions.
 */
const val LICENSES_DIALOG_CLOSE_BUTTON_TAG = "linkedlicense_dialog_close_button"

/**
 * A full-screen dialog wrapping [LicensesList], with a top bar containing a title and an "X"
 * close [IconButton] that calls [onDismissRequest].
 *
 * Independently usable - a caller can drive its visibility from its own state/trigger without
 * using [LicensesButton]. Theme-agnostic: draws only from the ambient [MaterialTheme].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesDialog(
    licenses: List<License>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                LicensesDialogTopBar(onCloseClick = onDismissRequest)
            },
        ) { padding ->
            LicensesList(
                licenses = licenses,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicensesDialogTopBar(onCloseClick: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(Res.string.licenses),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        actions = {
            IconButton(
                onClick = onCloseClick,
                modifier = Modifier.testTag(LICENSES_DIALOG_CLOSE_BUTTON_TAG),
            ) {
                CloseGlyph(tint = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}
