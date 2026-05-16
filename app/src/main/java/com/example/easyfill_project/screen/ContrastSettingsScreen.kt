package com.example.easyfill_project.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
fun ContrastSettingsScreen(
    selectedMode: ContrastMode,
    onModeSelected: (ContrastMode) -> Unit,
    navController: NavHostController
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