package com.life.mindfulnessapp.test_compose

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@OptIn(ExperimentalFoundationApi::class)
@Preview
@Composable
fun LayoutExample() {
    Column(modifier = Modifier.fillMaxSize()) {
        // 水平排列
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text("A")
            Text("B")
            Text(
                "C", fontSize = 22.sp, modifier = Modifier
                    .size(100.dp)
                    .background(Color.Yellow)
            )
        }

        // 层叠
        Box(modifier = Modifier
            .size(100.dp)
            .combinedClickable(
                onClick = { println("单击") },
                onDoubleClick = { println("双击") },
                onLongClick = { println("长按") }
            )
            .background(color = Color.Red)) {

            Text("覆盖文字", modifier = Modifier.align(Alignment.BottomEnd))
            Text("aaaa")
            Column(modifier = Modifier.background(Color.Blue)) {
                Text("ABC")
                Text("DEF")

            }
        }

        // 懒加载垂直列表
        LazyColumn {
            items(10) { index ->
                Text("Item #$index")
            }
        }
    }


}
