package dev.noahtownsend.linkedlicense.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.noahtownsend.linkedlicense.License
import dev.noahtownsend.linkedlicense.compose.generated.resources.Res
import dev.noahtownsend.linkedlicense.compose.generated.resources.license_by
import org.jetbrains.compose.resources.stringResource

/**
 * A scrollable list of [licenses], sorted by author. Each row is expandable/collapsible to
 * reveal the full [License.licenseText].
 *
 * Theme-agnostic: draws only from the ambient [MaterialTheme] the caller already wraps this
 * in - it never hardcodes colors or applies its own [MaterialTheme].
 */
@Composable
fun LicensesList(
    licenses: List<License>,
    modifier: Modifier = Modifier,
) {
    val sortedLicenses = remember(licenses) { licenses.sortedBy { it.author.lowercase() } }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(sortedLicenses, key = { "${it.author}|${it.elementLicensed}|${it.shortName}" }) { license ->
            LicenseRow(license = license)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun LicenseRow(license: License) {
    var expanded by rememberSaveable(license) { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = license.elementLicensed,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = stringResource(Res.string.license_by, license.author),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = license.shortName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp),
            )

            ChevronGlyph(expanded = expanded, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = license.licenseText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                        ).padding(12.dp),
            )
        }
    }
}
