package dev.noahtownsend.linkedlicense.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.noahtownsend.linkedlicense.License
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LicensesButtonTest {
    private val licenses =
        listOf(
            License.MIT(elementLicensed = "Kotlin", author = "JetBrains", year = "2011"),
        )

    @Test
    fun `LicensesButton opens the dialog on click`() =
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    LicensesButton(licenses = licenses)
                }
            }

            onNodeWithText("Licenses").performClick()

            onNodeWithTag(LICENSES_DIALOG_CLOSE_BUTTON_TAG).assertExists()
        }
}
