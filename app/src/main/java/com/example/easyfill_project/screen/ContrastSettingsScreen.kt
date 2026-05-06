package com.example.easyfill_project.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ContrastSettingsScreen(
    selectedMode: ContrastMode,
    onModeSelected: (ContrastMode) -> Unit
) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {

            Text(
                text = "בחירת ניגודיות",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground//text on background color
            )

            Spacer(modifier = Modifier.height(20.dp))

            ContrastOption(
                title = " גווני כחול רגילים",
                selected = selectedMode == ContrastMode.DEFAULT,
                onClick = { onModeSelected(ContrastMode.DEFAULT) }
            )

            ContrastOption(
                title = "צבעי שחור-לבן",
                selected = selectedMode == ContrastMode.HIGH,
                onClick = { onModeSelected(ContrastMode.HIGH) }
            )

            ContrastOption(
                title = "צבעי סגול לילך ",
                selected = selectedMode == ContrastMode.LOW,
                onClick = { onModeSelected(ContrastMode.LOW) }
            )
        }
    }


@Composable
fun ContrastOption(
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
            containerColor = MaterialTheme.colorScheme.surface // card background
        ),
        elevation = CardDefaults.cardElevation(0.dp) // optional
    ){
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                //.background(MaterialTheme.colorScheme.surface),
            ,verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick

            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface//text color on card
            )
        }
    }
}