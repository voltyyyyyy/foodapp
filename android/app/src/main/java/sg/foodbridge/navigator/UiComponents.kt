package sg.foodbridge.navigator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

fun foodSupportIcon(type: String): ImageVector = when (type) {
    "Hot meals", "Cooked Meals" -> Icons.Outlined.Restaurant
    "Groceries", "Food Rations" -> Icons.Outlined.ShoppingBag
    "Food bank" -> Icons.Outlined.Inventory2
    "Delivery" -> Icons.Outlined.LocalShipping
    "Food Vouchers" -> Icons.Outlined.LocalOffer
    "Fresh Produce" -> Icons.Outlined.Eco
    else -> Icons.Outlined.VolunteerActivism
}

/** A decorative icon beside readable text, with a single accessible announcement. */
@Composable fun IconFact(text: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(18.dp), tint = Muted)
        Text(text, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.weight(1f))
    }
}

/** Secondary information remains available without competing with the next action. */
@Composable fun ExpandableSection(
    title: String,
    icon: ImageVector,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse $title" else "Expand $title") { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = Teal)
            Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = Muted)
        }
        if (expanded) Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
