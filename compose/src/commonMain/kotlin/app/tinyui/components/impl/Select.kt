package app.tinyui.components.impl

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tinyui.components.NodeScope
import app.tinyui.components.textFieldStyle

/**
 * Choices are the `Option` children, read as data; the selection is the page's prop. With `onClick` the tap is the
 * page's: the menu stays shut and the page shows the choices its own way (a sheet), the field still showing the pick.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectComponent(scope: NodeScope) {
    val options = scope.node.options()
    val selected = scope.get<String>("value")
    val enabled = scope.get<Boolean>("enabled") ?: true
    var expanded by remember { mutableStateOf(false) }
    // the tap is the page's once it listens for it: the menu is not this component's to show
    val pageOpens = scope.has("onClick")
    // disabled, or taken over by the page, while open: the menu goes, so nothing can be picked from it any more
    if ((!enabled || pageOpens) && expanded) expanded = false
    val chosen = options.firstOrNull { it.first == selected }?.second ?: ""
    val style = textFieldStyle()
    val label = scope.get<String>("label")?.takeIf { it.isNotEmpty() }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (!enabled) Unit else if (pageOpens) scope.dispatch("onClick") else expanded = it }, modifier = scope.modifier(clickable = false)) {
        OutlinedTextField(
            // read-only: the text is the choice, so a new choice is a new state
            state = remember(chosen) { TextFieldState(chosen) },
            readOnly = true,
            colors = style.colors,
            contentPadding = style.contentPadding,
            enabled = enabled,
            lineLimits = TextFieldLineLimits.SingleLine,
            label = label?.let { { Text(it) } },
            placeholder = scope.get<String>("placeholder")?.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)// a floating label sits on the top border and adds to the height, so the box is no longer one line and its padding
                .fillMaxWidth().fieldMinHeight(style, singleLine = label == null),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for ((value, text) in options) {
                DropdownMenuItem(text = { Text(text) }, enabled = enabled, onClick = { expanded = false; if (scope.has("onSelect")) scope.dispatch("onSelect", payload("value", value)) })
            }
        }
    }
}
