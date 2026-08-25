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

    @Test
    fun `LicensesList expands a row with notice to show both notice and licenseText`() =
        runComposeUiTest {
            val noticeText = "Copyright 2016-2024 JetBrains s.r.o and contributors"
            val licensesWithNotice =
                listOf(
                    License.Apache2(
                        elementLicensed = "Futures Kotlin Extensions",
                        author = "The Android Open Source Project",
                        notice = noticeText,
                    ),
                )

            setContent {
                MaterialTheme {
                    LicensesList(licenses = licensesWithNotice)
                }
            }

            onNodeWithText("Futures Kotlin Extensions").performClick()

            onNodeWithText(noticeText).assertExists()
            onNodeWithText("Apache License", substring = true).assertExists()
        }
}
