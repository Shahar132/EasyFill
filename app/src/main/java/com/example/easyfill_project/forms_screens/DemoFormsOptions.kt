package com.example.easyfill_project.forms_screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
fun DemoFormsOptions(navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "בחר טופס שברצונך למלא",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(36.dp))

        DemoFormCard(
            title = "טופס בקשה לסיוע בדיור",
            onClick = {
                navController.navigate("housingAssistanceForm")
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        DemoFormCard(
            title = "טופס בקשה לעדכון פרטי חשבון בנק",
            onClick = {
                navController.navigate("bankDetailsForm")
            }
        )
    }
}

@Composable
fun DemoFormCard(
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(300.dp)
            .height(150.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}