package com.example.easyfill_project.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

import com.example.easyfill_project.R

@Composable
fun GuidanceSlidesScreen(navController: NavHostController) {
    val pagerState = rememberPagerState(pageCount = { 3 })

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> GuidanceCard(
                    image = R.drawable.personal_setting_card,
                    title = "הגדרת התאמה אישית",
                    text = "באפליקציה תוכל לבצע התאמה אישית כרצונך, \nתוכל לבחור צבעים אחרים, מוזיקת רקע וגודל טקסט."
                )

                1 -> GuidanceCard(
                    image = R.drawable.distress_setection,
                    title = "זיהוי מצוקה מותאם אישית",
                    text = "באפליקציה אנחנו נזהה תנועות ידיים, הבעות פנים ופרמטרי קול חריגים על מנת שנוכל לעזור לך. "
                )

                2 -> GuidanceCard(
                    image = R.drawable.chtbot,
                    title = "עוזר דיגיטלי",
                    text = "במידה ונזהה אות מצוקה או חריגה, נתריא לך על כך \nונציע שינויים שתוכל/י לבצע במידה ותבחר/י."
                )
            }
        }

        DotsIndicator(
            totalDots = 3,
            selectedIndex = pagerState.currentPage
        )
        if (pagerState.currentPage == 2) {

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 16.dp),

                contentAlignment = Alignment.CenterEnd
            ) {
                OutlinedButton(
                    onClick = {
                        navController.popBackStack()
                    },
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(
                        2.dp,
                        MaterialTheme.colorScheme.onSurface
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("חזרה לדף הבית")
                }
            }
        }
    }
}

@Composable
fun GuidanceCard(
    image: Int,
    title: String,
    text: String
) {
    Card(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .height(600.dp),
        elevation = CardDefaults.cardElevation(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            3.dp,
            MaterialTheme.colorScheme.secondary
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            Image(
                painter = painterResource(id = image),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(16.dp))


            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}


@Composable
fun DotsIndicator(
    totalDots: Int,
    selectedIndex: Int
) {
    Row(
        modifier = Modifier
            .padding(bottom = 24.dp)
            // Group the dots into a single clean TalkBack announcement:
            .semantics {
                contentDescription = "עמוד ${selectedIndex + 1} מתוך $totalDots"
            },
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(totalDots) { index ->
            Text(
                text = if (index == selectedIndex) "●" else "○",
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurface,
                // Hide individual dot symbols from screen readers:
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}

