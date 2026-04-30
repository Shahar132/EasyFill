package com.example.easyfill_project.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.easyfill_project.R

@Composable
fun GuidanceScreen() {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 5.dp)
    ) {

        Spacer(modifier = Modifier.height(20.dp))


        Image(
            painter = painterResource(id = R.drawable.userguideillustration),
            contentDescription = "Guidance header",
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(16.dp))

        ExpandableItem(
            title = "מהי מטרת האפליקציה?",
            content = "האפליקציה נועדה לעזור לך למלא טפסים בצורה פשוטה וברורה."
        )
        ExpandableItem(
            title = "איך מתחילים תהליך?",
            content = "בחרי במסך הבית את האפשרות 'התחלת תהליך' ופעלי לפי ההנחיות."
        )

        ExpandableItem(
            title = "איך שומרים נתונים?",
            content = "המידע נשמר באופן אוטומטי במהלך השימוש באפליקציה."
        )
    }
}

@Composable
fun ExpandableItem(
    title: String,
    content: String
) {

    // Controls if item is open or closed
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {

        Column {

            // Top row (clickable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        expanded = !expanded // toggle open/close
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp
                )

                // Arrow icon changes direction
                Icon(
                    imageVector = if (expanded)
                        Icons.Default.KeyboardArrowUp
                    else
                        Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand"
                )
            }

            // Expanded content
            if (expanded) {
                Divider()

                Text(
                    text = content,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp
                )
            }
        }
    }
}