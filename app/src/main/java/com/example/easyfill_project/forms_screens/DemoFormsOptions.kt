package com.example.easyfill_project.forms_screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.easyfill_project.texttospeech.TextToSpeechManager

// Card appearance settings for all forms.
private val FORM_CARD_WIDTH = 340.dp
private val FORM_CARD_HEIGHT = 105.dp
private val FORM_CARD_SPACING = 12.dp
private val FORM_CARD_CORNER_RADIUS = 16.dp
private val FORM_CARD_BORDER_WIDTH = 1.dp

private data class DisplayForm(
    val title: String,
    val description: String = "",
    val route: String? = null,
    val isActive: Boolean = false
)

@Composable
fun DemoFormsOptions(
    navController: NavHostController
) {
    val activeForm = FormsRegistry.forms.firstOrNull()

    val displayedForms = buildList {
        // The real and active form.
        if (activeForm != null) {
            add(
                DisplayForm(
                    title = activeForm.title,
                    description = activeForm.description,
                    route = activeForm.route,
                    isActive = true
                )
            )
        }

        // Demonstration forms only.
        addAll(
            listOf(
                DisplayForm(
                    title = "בקשה להכרה בנכות בעקבות חבלה"
                ),
                DisplayForm(
                    title = "בקשה לבדיקה מחדש (החמרת מצב)"
                ),
                DisplayForm(
                    title = "בקשה לעדכון פרטי חשבון בנק"
                ),
                DisplayForm(
                    title = "בקשה למימוש רכב רפואי ראשון"
                ),
                DisplayForm(
                    title = "טופס עדכון פרטים אישיים"
                ),
                DisplayForm(
                    title = "בקשה לסיוע בכיסוי חובות"
                ),
                DisplayForm(
                    title = "בקשה להחלפת רכב רפואי"
                ),
                DisplayForm(
                    title = "טופס להנפקת תעודת נכה צה\"ל"
                ),
                DisplayForm(
                    title = "הצהרה לעורך דין על החכרת קרקע"
                )
            )
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = 24.dp,
            bottom = 40.dp
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FORM_CARD_SPACING)
    ) {
        item {
            Text(
                text = "בחר טופס שברצונך למלא",
                modifier = Modifier.padding(bottom = 22.dp),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }

        items(
            items = displayedForms,
            key = { form -> form.title }
        ) { form ->
            FormCard(
                title = form.title,
                description = form.description,
                isActive = form.isActive,
                onClick = {
                    form.route?.let { route ->
                        navController.navigate(route)
                    }
                }
            )
        }
    }
}

@Composable
private fun FormCard(
    title: String,
    description: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    var showInfo by remember {
        mutableStateOf(false)
    }

    val context = LocalContext.current

    val ttsManager = remember {
        TextToSpeechManager(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    Card(
        modifier = Modifier
            .width(FORM_CARD_WIDTH)
            .height(FORM_CARD_HEIGHT)
            .clickable(
                enabled = isActive,
                onClick = onClick
            ),
        shape = RoundedCornerShape(FORM_CARD_CORNER_RADIUS),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp
        ),
        border = BorderStroke(
            width = FORM_CARD_BORDER_WIDTH,
            color = MaterialTheme.colorScheme.secondary
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (isActive) {
                IconButton(
                    onClick = {
                        showInfo = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = "מידע על הטופס",
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                // Visual icon only for demonstration cards.
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (showInfo && isActive) {
        AlertDialog(
            onDismissRequest = {
                ttsManager.stop()
                showInfo = false
            },

            // Uses the colors selected by the user.
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onBackground,
            iconContentColor = MaterialTheme.colorScheme.onSurface,

            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    IconButton(
                        onClick = {
                            ttsManager.stop()
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
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        ttsManager.stop()
                        showInfo = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        text = "הבנתי",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
    }
}