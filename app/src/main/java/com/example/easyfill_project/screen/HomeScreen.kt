package com.example.easyfill_project.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.example.easyfill_project.R
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavHostController


// This is a simple Composable function that represents your Home screen
@Composable
fun HomeScreen(navController: NavHostController) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Page headline
        Text(
            text = "מילוי קל",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(36.dp))

        // First card
        Card(
            modifier = Modifier
                .width(230.dp)
                .height(180.dp)
                .clickable {
                    // navigate to the relevant screen
                    navController.navigate("uploadPdf")
                },
            elevation = CardDefaults.cardElevation(6.dp)
        ) {

            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                // Top area - icon/image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.fillforms),
                        contentDescription = "Start process",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Divider()

                // Bottom area - text
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {//0xFFE3F2FD
                    Text(
                        text = "התחלת תהליך מיצוי הזכויות",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // second card
        Card(
            modifier = Modifier
                .width(230.dp)
                .height(180.dp)
                .clickable {
                    // Later: navigate to the relevant screen
                    // innerNavController.navigate("rightsProcess")
                },
            elevation = CardDefaults.cardElevation(6.dp)
        ) {

            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                // Top area - icon/image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.progressbar),
                        contentDescription = "Start process",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Divider()

                // Bottom area - text
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "מעקב אחר ההתקדמות שלי",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface //text color in card
                    )
                }
            }
        }


    }
}