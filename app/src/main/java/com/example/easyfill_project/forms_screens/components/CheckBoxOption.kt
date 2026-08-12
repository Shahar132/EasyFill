package com.example.easyfill_project.forms_screens.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize // Added import
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.toggleable // Added import
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role // Added import
import androidx.compose.ui.unit.dp

@Composable
fun CheckBoxOption(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .wrapContentWidth()
            // 1. Ensure 48dp minimum height for easy touch targets:
            .defaultMinSize(minHeight = 48.dp)
            // 2. Merge Checkbox + Text into a single toggleable element for TalkBack:
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Checkbox
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            // 3. Set to null so the parent Row handles the tap and screen reader state:
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.onSurface,
                uncheckedColor = MaterialTheme.colorScheme.onSurface,
                checkmarkColor = MaterialTheme.colorScheme.surface
            )
        )

        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}