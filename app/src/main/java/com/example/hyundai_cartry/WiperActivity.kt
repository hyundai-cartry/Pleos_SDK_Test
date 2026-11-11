package com.example.hyundai_cartry

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Wiper
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.WiperStateListener
import com.example.hyundai_cartry.ui.ButtonRow
import com.example.hyundai_cartry.ui.CarColors
import com.example.hyundai_cartry.ui.CarScreen
import com.example.hyundai_cartry.ui.DualStatusCard
import com.example.hyundai_cartry.ui.LogPanel
import com.example.hyundai_cartry.ui.PillButton
import com.example.hyundai_cartry.ui.theme.Hyundai_cartryTheme

class WiperActivity : ComponentActivity() {

    companion object {
        private const val TAG = "WiperTest"
    }

    // ───── 확인할 영역: 앞유리 / 뒷유리 ─────
    private val frontArea = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private val rearArea = WindowArea(WindowValue.REAR_WINDSHIELD)

    private lateinit var vehicle: Vehicle   // Vehicle SDK 진입점
    private lateinit var wiper: Wiper       // 와이퍼 API 묶음

    // ───── 화면에 보여줄 상태값 ─────
    private var frontSwitchText by mutableStateOf("-")
    private var frontStateText by mutableStateOf("-")
    private var rearSwitchText by mutableStateOf("-")
    private var rearStateText by mutableStateOf("-")
    private var logText by mutableStateOf("")

    // ─────────────────────────────────────────────
    // 와이퍼 "동작 상태" 실시간 리스너 (앞/뒤 각각 하나씩)
    // ─────────────────────────────────────────────
    // 등록과 해제에 같은 객체를 써야 하므로 변수로 만들어 둡니다.
    private val frontStateListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) {
            Log.d(TAG, "[앞] 상태 변경 → $state")
            runOnUiThread {
                frontStateText = state?.name ?: "null"
                addLog("앞유리 상태 변경 → ${state?.name}")
            }
        }
        override fun onFailed(e: Exception) {
            Log.e(TAG, "[앞] 상태 리스너 오류", e)
            runOnUiThread { addLog("앞유리 리스너 오류: ${e.message}") }
        }
    }

    private val rearStateListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) {
            Log.d(TAG, "[뒤] 상태 변경 → $state")
            runOnUiThread {
                rearStateText = state?.name ?: "null"
                addLog("뒷유리 상태 변경 → ${state?.name}")
            }
        }
        override fun onFailed(e: Exception) {
            Log.e(TAG, "[뒤] 상태 리스너 오류", e)
            runOnUiThread { addLog("뒷유리 리스너 오류: ${e.message}") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Hyundai_cartryTheme {
                CarScreen(
                    category = "와이퍼",
                    headline = "와이퍼가 움직이나요?",
                    description = "Console에서 Windshield Wiper Switch를 바꿔 보세요.",
                    onBack = { finish() },
                    side = { LogPanel(logText) },
                ) {
                    // 유리 하나당 카드 하나: 스위치(조작)와 동작 상태를 나란히 표시
                    DualStatusCard(
                        icon = "🌧️", title = "앞유리",
                        firstLabel = "스위치 (조작)", firstValue = wiperKo(frontSwitchText),
                        secondLabel = "동작 상태", secondValue = wiperKo(frontStateText),
                    )
                    DualStatusCard(
                        icon = "🌧️", title = "뒷유리",
                        firstLabel = "스위치 (조작)", firstValue = wiperKo(rearSwitchText),
                        secondLabel = "동작 상태", secondValue = wiperKo(rearStateText),
                    )
                    Text(
                        "동작 상태는 바뀌면 자동으로 갱신되고, 스위치는 실시간 알림이 없어 다시 조회로 확인해요.",
                        color = CarColors.TextMuted, fontSize = 16.sp, lineHeight = 24.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    // 스위치는 실시간 알림 API가 없으므로 버튼으로 다시 조회합니다.
                    ButtonRow {
                        PillButton("현재 값 다시 조회", onClick = { refreshAll() }, icon = "↻")
                    }
                }
            }
        }

        // ── 1단계: Vehicle 객체 생성 + 초기화(서비스 연결 시작) ──
        // 연결은 비동기지만, 연결 전 호출은 SDK가 대기열에 넣었다가 연결되면 실행합니다.
        vehicle = Vehicle(applicationContext)
        vehicle.initialize()

        // ── 2단계: 와이퍼 API 객체 가져오기 ──
        wiper = vehicle.getWiper()

        // ── 3단계: 처음 한 번 현재 값 조회 ──
        refreshAll()

        // ── 4단계: 동작 상태 실시간 감시 시작 ──
        wiper.registerWiperState(frontArea, frontStateListener)
        wiper.registerWiperState(rearArea, rearStateListener)
    }

    // 앞/뒤 유리의 스위치와 상태를 모두 1회 조회
    private fun refreshAll() {
        readSwitch(frontArea, "앞유리") { frontSwitchText = it }
        readState(frontArea, "앞유리") { frontStateText = it }
        readSwitch(rearArea, "뒷유리") { rearSwitchText = it }
        readState(rearArea, "뒷유리") { rearStateText = it }
    }

    // getCurrentWiperSwitch: 운전자가 와이퍼 레버를 어디에 두었는지 조회
    private fun readSwitch(area: WindowArea, name: String, show: (String) -> Unit) {
        wiper.getCurrentWiperSwitch(
            area,
            onComplete = { switch ->
                Log.d(TAG, "[$name] 스위치 = $switch")
                runOnUiThread {
                    show(switch?.name ?: "null")
                    addLog("$name 스위치 조회 → ${switch?.name}")
                }
            },
            onFailed = { e ->
                Log.e(TAG, "[$name] 스위치 조회 실패", e)
                runOnUiThread {
                    show("조회 실패")
                    addLog("$name 스위치 조회 실패: ${e.message}")
                }
            }
        )
    }

    // getCurrentWiperState: 와이퍼가 실제로 동작 중인지 조회
    private fun readState(area: WindowArea, name: String, show: (String) -> Unit) {
        wiper.getCurrentWiperState(
            area,
            onComplete = { state ->
                Log.d(TAG, "[$name] 상태 = $state")
                runOnUiThread {
                    show(state?.name ?: "null")
                    addLog("$name 상태 조회 → ${state?.name}")
                }
            },
            onFailed = { e ->
                Log.e(TAG, "[$name] 상태 조회 실패", e)
                runOnUiThread {
                    show("조회 실패")
                    addLog("$name 상태 조회 실패: ${e.message}")
                }
            }
        )
    }

    // SDK 값 이름 → 화면에 보여줄 한국어
    private fun wiperKo(raw: String): String = when {
        raw == "-" -> "확인 중"
        raw == "null" -> "알 수 없음"
        raw == "OFF" -> "꺼짐"
        raw == "ON" -> "작동 중"
        raw == "AUTO" -> "자동"
        raw == "MIST" -> "미스트"
        raw == "SERVICE" -> "정비 모드"
        raw == "OTHER" -> "기타"
        raw.startsWith("INTERMITTENT_LEVEL_") -> "간헐 ${raw.removePrefix("INTERMITTENT_LEVEL_")}단"
        raw.startsWith("CONTINUOUS_LEVEL_") -> "연속 ${raw.removePrefix("CONTINUOUS_LEVEL_")}단"
        else -> raw
    }

    // 최근 로그 8줄만 화면에 유지
    private fun addLog(line: String) {
        logText = (listOf(line) + logText.lines().filter { it.isNotBlank() }).take(8).joinToString("\n")
    }

    override fun onDestroy() {
        // ── 5단계: 리스너 해제 → SDK 해제 ──
        wiper.unregisterWiperState(frontArea, frontStateListener)
        wiper.unregisterWiperState(rearArea, rearStateListener)
        vehicle.release()
        super.onDestroy()
    }
}