package dev.noahtownsend.linkedlicense.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.noahtownsend.linkedlicense.License
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LicensesDialogTest {
    private val licenses =
        listOf(
            License.MIT(elementLicensed = "Kotlin", author = "JetBrains", year = "2011"),
        )

    @Test
    fun `LicensesDialog calls onDismissRequest when close button is clicked`() =
        runComposeUiTest {
            var dismissed = false

            setContent {
                MaterialTheme {
                    LicensesDialog(
                        licenses = licenses,
                        onDismissRequest = { dismissed = true },
                    )
                }
            }

            onNodeWithTag(LICENSES_DIALOG_CLOSE_BUTTON_TAG).performClick()

            assertTrue(dismissed)
        }
}
