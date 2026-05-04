package com.example.easyfill_project.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.easyfill_project.R

@Composable
fun PersonalSettingScreen(navController: NavHostController) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "הגדרות התאמה אישית",
            fontSize = 28.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            PersonalSettingCard(
                title = "בחירת מוזיקה רקע",
                imageRes = R.drawable.backgroundmusic,
                modifier = Modifier.weight(1f),
                onClick = {
                    navController.navigate("backgroundSounds")
                }
            )

            PersonalSettingCard(
                title = "בחירת ניגודיות",
                imageRes = R.drawable.contrastcolors,
                modifier = Modifier.weight(1f),
                onClick = {
                    navController.navigate("contrastSettings")
                }
            )
        }

        Spacer(modifier = Modifier.height(25.dp))


        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Empty space (left)
            Spacer(modifier = Modifier.weight(0.3f))

            // Your card (middle)
            PersonalSettingCard(
                title = "בחירת גודל טקסט",
                imageRes = R.drawable.fontsize,
                modifier = Modifier.weight(1f),
                onClick = {
                    navController.navigate("fontSizeSettings")
                }
            )

            // Empty space (right)
           Spacer(modifier = Modifier.weight(0.3f))
        }

        }

    }


@Composable
fun PersonalSettingCard(
    title: String,
    imageRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(170.dp)
            .clickable {
                onClick()
            },
        elevation = CardDefaults.cardElevation(6.dp)
    ) {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            Image(
                painter = painterResource(id = imageRes),
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentScale = ContentScale.Crop
            )

            Divider()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp
                )
            }

        }
    }
}