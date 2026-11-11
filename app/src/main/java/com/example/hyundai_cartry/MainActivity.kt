package com.example.hyundai_cartry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hyundai_cartry.ui.CarScreen
import com.example.hyundai_cartry.ui.MenuTile
import com.example.hyundai_cartry.ui.theme.Hyundai_cartryTheme

// 앱 시작 화면: 각 기능 테스트 화면으로 이동하는 타일 메뉴
class MainActivity : ComponentActivity() {

    // 메뉴 한 칸의 정보
    private data class MenuItem(
        val icon: String,
        val title: String,
        val subtitle: String,
        val target: Class<*>,
    )

    // 새 기능을 추가할 때는 여기에 한 줄만 추가하면 됩니다.
    private val menuItems = listOf(
        MenuItem("📍", "현재 위치", "마지막 위치 · 실시간 위치", LocationActivity::class.java),
        MenuItem("🌧️", "와이퍼", "스위치 조작 · 동작 상태", WiperActivity::class.java),
        MenuItem("🪟", "뒷좌석 창문", "잠금 상태 · 열림 정도", WindowLockActivity::class.java),
        MenuItem("❄️", "앞유리 김서림", "성에 제거 · 공조 풍량", DefrostActivity::class.java),
        MenuItem("🛑", "브레이크 페달", "밟음 여부 · 밟은 횟수", BrakeActivity::class.java),
        MenuItem("⚙️", "기어", "P · R · N · D 선택 상태", GearActivity::class.java),
        MenuItem("↔️", "방향 지시등", "왼쪽 · 오른쪽 · 꺼짐", TurnSignalActivity::class.java),
        MenuItem("🔦", "전조등", "하향등 상태 · 라이트 스위치", LowBeamActivity::class.java),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Hyundai_cartryTheme {
                CarScreen(
                    category = "기능 테스트",
                    headline = "어떤 기능을 확인해볼까요?",
                    description = "Console에서 차량을 조작하면 앱에 그대로 반영되는지 확인할 수 있어요.",
                    onBack = null,
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        // 화면 폭에 따라 한 줄에 놓을 타일 수 결정
                        val columns = when {
                            maxWidth > 1100.dp -> 4
                            maxWidth > 760.dp -> 3
                            else -> 2
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            menuItems.chunked(columns).forEach { rowItems ->
                                // IntrinsicSize.Min + fillMaxHeight: 한 줄의 타일 높이를 가장 큰 타일에 맞춤
                                Row(
                                    modifier = Modifier.height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        MenuTile(
                                            icon = item.icon,
                                            title = item.title,
                                            subtitle = item.subtitle,
                                            onClick = { open(item.target) },
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                        )
                                    }
                                    // 마지막 줄이 덜 찼으면 빈칸으로 폭 맞추기
                                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 전달받은 Activity 화면을 엽니다.
    // 열린 화면에서 뒤로 가기를 누르면 그 화면이 종료되고(onDestroy → SDK release)
    // 다시 이 메뉴로 돌아옵니다.
    private fun open(target: Class<*>) {
        startActivity(Intent(this, target))
    }
}
