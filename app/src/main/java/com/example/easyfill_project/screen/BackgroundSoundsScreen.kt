package com.example.easyfill_project.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.easyfill_project.chatbot.personalization.PersonalizationCatalog

@Composable
fun BackgroundSoundsScreen(navController: NavHostController) {

    val context = LocalContext.current
    val selectedSound = SoundManager.selectedSound

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {

        Text(
            text = "בחירת צלילי רקע",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "בחר/י צליל רקע אחד שיעזור לך בזמן השימוש באפליקציה",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        SoundOptionCard(
            title = "ללא צליל",
            selected = selectedSound == "none",
            onClick = {
                SoundManager.stop()
            }
        )

        PersonalizationCatalog.sounds.forEach { sound ->
            SoundOptionCard(
                title = sound.displayName,
                selected = selectedSound == sound.key,
                onClick = {
                    SoundManager.play(
                        context = context,
                        soundName = sound.key,
                        soundRes = sound.soundRes
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        OutlinedButton(
            onClick = { navController.navigate("Personal Settings") },
            modifier = Modifier
                .wrapContentWidth(Alignment.End)
                .padding(top = 24.dp),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
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
fun SoundOptionCard(
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
        )
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