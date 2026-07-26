package com.example.easyfill_project.forms_screens.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun RadioOption(
    text: String,
    value: String,
    selectedValue: String,
    onSelect: (String) -> Unit
) {
    val isSelected = selectedValue == value

    Row(
        modifier = Modifier
            .wrapContentWidth()
            // 1. Force the row to be at least 48dp tall for a perfect touch target:
            .defaultMinSize(minHeight = 48.dp)
            // 1. Make the entire Row handle the selection and accessibility state:
            .selectable(
                selected = isSelected,
                onClick = {
                    onSelect(if (isSelected) "" else value)
                },
                role = Role.RadioButton
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            // 2. Set onClick to null so the parent Row handles the tap and accessibility
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.onSurface,
                unselectedColor = MaterialTheme.colorScheme.onSurface
            )
        )

        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}