package com.example.easyfill_project.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
fun FontSizeSettingsScreen(
    selectedMode: FontSizeMode,
    onModeSelected: (FontSizeMode) -> Unit,
    navController: NavHostController
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

        Spacer(modifier = Modifier.height(140.dp))

        OutlinedButton(
            onClick = { navController.navigate("Personal Settings") },
            modifier = Modifier
                .wrapContentWidth(Alignment.End)
                .padding(top = 24.dp),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,  // background
                contentColor = MaterialTheme.colorScheme.onSurface   // text + icon
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Back"
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "חזרה למסך הקודם",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
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