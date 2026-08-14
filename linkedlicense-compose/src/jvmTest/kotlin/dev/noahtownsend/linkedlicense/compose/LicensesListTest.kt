package dev.noahtownsend.linkedlicense.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.noahtownsend.linkedlicense.License
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LicensesListTest {
    private val licenses =
        listOf(
            License.MIT(elementLicensed = "Kotlin", author = "JetBrains", year = "2011"),
            License.Apache2(elementLicensed = "Ktor", author = "JetBrains"),
        )

    @Test
    fun `LicensesList renders one row per license`() =
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    LicensesList(licenses = licenses)
                }
            }

            onNodeWithText("Kotlin").assertExists()
            onNodeWithText("Ktor").assertExists()
        }

    @Test
    fun `LicensesList expands a row on click to show licenseText`() =
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    LicensesList(licenses = licenses)
                }
            }

            onNodeWithText("Kotlin").performClick()

            onNodeWithText("MIT License", substring = true).assertExists()
        }
}
