package com.example.easyfill_project.screen

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.easyfill_project.R
import androidx.compose.ui.platform.LocalContext

@Composable
fun BackgroundSoundsScreen() {

    val context = LocalContext.current

    var selectedSound by remember {
        mutableStateOf(SoundManager.selectedSound)
    }


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

        SoundOption(
            title = "ללא צליל",
            selected = selectedSound == "none",
            onClick = { selectedSound = "none"
                SoundManager.stop()
            }
        )

        SoundOption(
            title = "צלילי טבע מרגיעים",
            selected = selectedSound == "nature",
            onClick = { selectedSound = "nature"
                SoundManager.play(context, "nature", R.raw.nature_sound)            }
        )

        SoundOption(
            title = "מוזיקה למדיטציה",
            selected = selectedSound == "calm",
            onClick = { selectedSound = "calm"
                SoundManager.play(context,"calm", R.raw.calm_music)
            }
        )

        SoundOption(
            title = "צלילי נגינה מרגיעים",
            selected = selectedSound == "instruments",
            onClick = { selectedSound = "instruments"
                SoundManager.play(context,"instruments",R.raw.violin_sound)
            }
        )
    }
}

@Composable
fun SoundOption(
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
            containerColor = MaterialTheme.colorScheme.surface // color for card - > surface
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