package dev.noahtownsend.linkedlicense.compose

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.noahtownsend.linkedlicense.License
import dev.noahtownsend.linkedlicense.compose.generated.resources.Res
import dev.noahtownsend.linkedlicense.compose.generated.resources.licenses
import org.jetbrains.compose.resources.stringResource

/**
 * A clickable row labeled with the translated "Licenses" string that manages its own
 * dialog-visibility state internally, showing [LicensesDialog] when tapped and dismissing it
 * on the close button or scrim tap.
 *
 * This is the "full solution" - a caller that wants a single drop-in composable, with no
 * state of their own to manage, should use this. Callers that need to drive visibility from
 * their own trigger (e.g. a settings-screen navigation item) should use [LicensesDialog]
 * directly instead. Theme-agnostic: draws only from the ambient [MaterialTheme].
 */
@Composable
fun LicensesButton(
    licenses: List<License>,
    modifier: Modifier = Modifier,
) {
    var dialogVisible by remember { mutableStateOf(false) }

    Text(
        text = stringResource(Res.string.licenses),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.clickable { dialogVisible = true },
    )

    if (dialogVisible) {
        LicensesDialog(
            licenses = licenses,
            onDismissRequest = { dialogVisible = false },
        )
    }
}
