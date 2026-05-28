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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.runtime.*

//text to speech imports
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.platform.LocalContext
import com.example.easyfill_project.texttospeech.TextToSpeechManager

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

        Spacer(modifier = Modifier.height(50.dp))

        DemoFormCard(
            title = "טופס בקשה לסיוע בדיור",
            description = "טופס זה מיועד למימוש הזכאות למענקים והלוואות בתחום הדיור בנושאים האלה: סיוע בשכר דירה, התאמת דירה לנכות ועוד." ,

            onClick = { navController.navigate("housingAssistanceForm") }
        )

        Spacer(modifier = Modifier.height(35.dp))

        DemoFormCard(
            title = "טופס עדכון פרטי חשבון בנק",
            description = "טופס עדכון פרטי חשבון בנק משמש להסדרת העברת התגמולים, הקצבאות וההחזרים הכספיים מאגף השיקום.",
            onClick = { navController.navigate("bankDetailsForm") }
        )
    }
}

@Composable
fun DemoFormCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val ttsManager = remember { TextToSpeechManager(context) }

    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    Card(
        modifier = Modifier
            .width(340.dp)
            .height(100.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Description,//icon form
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            IconButton(onClick = { showInfo = true }) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "מידע" ,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = {
                ttsManager.stop()
                showInfo = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ttsManager.stop()
                        showInfo = false
                    }
                ) {
                    Text(
                        text = "הבנתי",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    IconButton(
                        onClick = {
                            ttsManager.speak(description)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "השמעה",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            text = {
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        )
    }

}