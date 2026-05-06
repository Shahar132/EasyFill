package com.example.easyfill_project.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FontSizeSettingsScreen(
    selectedMode: FontSizeMode,
    onModeSelected: (FontSizeMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "בחירת גודל טקסט",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(20.dp))

        FontSizeOption(
            title = "טקסט קטן",
            selected = selectedMode == FontSizeMode.SMALL,
            onClick = { onModeSelected(FontSizeMode.SMALL) }
        )

        FontSizeOption(
            title = "טקסט רגיל",
            selected = selectedMode == FontSizeMode.NORMAL,
            onClick = { onModeSelected(FontSizeMode.NORMAL) }
        )

        FontSizeOption(
            title = "טקסט גדול",
            selected = selectedMode == FontSizeMode.LARGE,
            onClick = { onModeSelected(FontSizeMode.LARGE) }
        )
    }
}

@Composable
fun FontSizeOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}