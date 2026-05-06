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
import androidx.compose.foundation.background
//make it scrollable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.easyfill_project.R

@Composable
fun GuidanceScreen() {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 5.dp)
    ) {

        Spacer(modifier = Modifier.height(20.dp))


        Image(
            painter = painterResource(id = R.drawable.userguideillustration),
            contentDescription = "Guidance header",
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(16.dp))

        ExpandableItem(
            title = "מהי מטרת האפליקציה?",
            content = "האפליקציה נועדה לעזור לך להתמודד עם תהליכים בירוקרטיים בצורה פשוטה וברורה,\n" +
                    "היא מלווה אותך שלב אחר שלב במילוי טפסים, מסבירה כל חלק בצורה קלה להבנה, ועוזרת לך להתקדם בקצב שלך."

        )
        ExpandableItem(
            title = "איך מתחילים להשתמש באפליקציה?",
            content = "לאחר התחברות, בחר/י במסך הבית את האפשרות 'התחלת תהליך' והמשיך/י לפי ההנחיות הבאות."
        )

        ExpandableItem(
            title = "כיצד האפליקציה עוזרת לי במילוי הטפסים במצב של לחץ?",
            content = "האפליקציה מאפשרת לך להתאים את הממשק למה שנוח לך – כמו שינוי גודל הטקסט והצגת הסברים נוספים לכל שלב. בנוסף, היא מאפשרת מילוי שדות מידע אוטומטי,ומזהה מתי התהליך מרגיש מלחיץ, ומתאימה את עצמה כדי להקל עליך ולעזור לך להמשיך בקצב רגוע."
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
                    .background(MaterialTheme.colorScheme.surface)

                    .clickable {
                        expanded = !expanded // toggle open/close
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface) // background from theme
                ) {
                    Text(
                        text = content,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface //  text color from theme
                    )
                }
            }
        }
    }
}