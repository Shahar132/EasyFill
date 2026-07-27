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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.easyfill_project.chatbot.personalization.PersonalizationCatalog

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
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Groups all contrast options as one radio-button group.
        Column(
            modifier = Modifier.selectableGroup()
        ) {
            PersonalizationCatalog.contrastModes.forEach { option ->
                ContrastOptionCard(
                    title = option.displayName,
                    selected = selectedMode == option.mode,
                    onClick = {
                        onModeSelected(option.mode)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(265.dp))

        OutlinedButton(
            onClick = {
                navController.navigate("Personal Settings")
            },
            modifier = Modifier
                .wrapContentWidth(Alignment.End)
                .padding(top = 24.dp),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.onSurface
            ),
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
                    contentDescription = "חזרה למסך הקודם"
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
fun ContrastOptionCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.secondary
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()

                // Makes the full row the only clickable radio option.
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton
                )

                // Combines the title and selected state into one
                // accessibility item.
                .semantics(
                    mergeDescendants = true
                ) { }

                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,

                // The complete row handles the click.
                onClick = null,

                // Prevents the radio icon from appearing as a second
                // accessibility object.
                modifier = Modifier.clearAndSetSemantics { }
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