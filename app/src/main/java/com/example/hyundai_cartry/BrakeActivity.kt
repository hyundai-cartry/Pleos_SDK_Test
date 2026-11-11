package com.example.hyundai_cartry

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Brake
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import com.example.hyundai_cartry.ui.ButtonRow
import com.example.hyundai_cartry.ui.CarScreen
import com.example.hyundai_cartry.ui.LogPanel
import com.example.hyundai_cartry.ui.PillButton
import com.example.hyundai_cartry.ui.StatusCard
import com.example.hyundai_cartry.ui.ValueCard
import com.example.hyundai_cartry.ui.theme.Hyundai_cartryTheme

// 브레이크 페달 상태(밟음/안 밟음)를 확인하는 화면
class BrakeActivity : ComponentActivity() {

    companion object {
        private const val TAG = "BrakeTest"
    }

    private lateinit var vehicle: Vehicle
    private lateinit var brake: Brake

    // ───── 화면 상태값 ─────
    private var capText by mutableStateOf("확인 중...")
    private var statusText by mutableStateOf("확인 중")
    private var pressCount by mutableIntStateOf(0)   // 밟은 횟수 (false → true 로 바뀐 횟수)
    private var lastStatus: Boolean? = null
    private var logText by mutableStateOf("")

    private var listenerRegistered = false

    // ─────────────────────────────────────────────
    // 브레이크 페달 상태 실시간 리스너
    // ─────────────────────────────────────────────
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) {
            Log.d(TAG, "브레이크 변경 → $status")
            runOnUiThread { updateStatus(status, "변경") }
        }
        override fun onFailed(e: Exception) {
            Log.e(TAG, "브레이크 리스너 오류", e)
            runOnUiThread { addLog("리스너 오류: ${e.message}") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Hyundai_cartryTheme {
                CarScreen(
                    category = "브레이크 페달",
                    headline = "브레이크를 밟고 있나요?",
                    description = "Console의 Motion에서 Brake Pedal 값을 올리면 '밟음'으로 바뀌어요. 밟은 정도(%)는 SDK에서 제공하지 않아요.",
                    onBack = { finish() },
                    side = { LogPanel(logText) },
                ) {
                    StatusCard("🛑", "브레이크 페달", statusText, caption = capText)
                    ValueCard("🔢", "밟은 횟수", "${pressCount}회", "안 밟음 → 밟음으로 바뀐 횟수")

                    ButtonRow {
                        PillButton("현재 값 다시 조회", onClick = { readStatus() }, icon = "↻")
                    }
                }
            }
        }

        // ── 1단계: SDK 생성 + 초기화 → Brake API 가져오기 ──
        vehicle = Vehicle(applicationContext)
        vehicle.initialize()
        brake = vehicle.getBrake()

        // ── 2단계: 지원 여부 확인 → 지원하면 조회 + 감시 시작 ──
        checkCapability()
    }

    private fun checkCapability() {
        brake.checkBrakePedalStatusCapabilities(
            onComplete = { config: CapabilityConfig ->
                Log.d(TAG, "브레이크 capability access=${config.accessType}")
                runOnUiThread {
                    capText = "접근: ${config.accessType}"
                    readStatus()                                   // getCurrentBrakePedalStatus
                    brake.registerBrakePedalStatus(brakeListener)  // registerBrakePedalStatus
                    listenerRegistered = true
                }
            },
            onFailed = { e ->
                Log.e(TAG, "브레이크 capability 조회 실패", e)
                runOnUiThread { capText = "지원 여부 조회 실패: ${e.message}" }
            }
        )
    }

    // 현재 상태 1회 조회
    private fun readStatus() {
        brake.getCurrentBrakePedalStatus(
            onComplete = { status ->
                Log.d(TAG, "브레이크 조회 = $status")
                runOnUiThread { updateStatus(status, "조회") }
            },
            onFailed = { e ->
                Log.e(TAG, "브레이크 조회 실패", e)
                runOnUiThread {
                    statusText = "조회 실패"
                    addLog("조회 실패: ${e.message}")
                }
            }
        )
    }

    // 화면 갱신 + 밟은 횟수 세기
    private fun updateStatus(status: Boolean?, from: String) {
        // 안 밟음(false) → 밟음(true)으로 바뀐 경우만 1회로 셈
        if (lastStatus == false && status == true) pressCount++
        lastStatus = status

        statusText = when (status) {
            true -> "밟음"
            false -> "안 밟음"
            null -> "알 수 없음"
        }
        addLog("$from → $status")
    }

    private fun addLog(line: String) {
        logText = (listOf(line) + logText.lines().filter { it.isNotBlank() }).take(10).joinToString("\n")
    }

    override fun onDestroy() {
        // ── 리스너 해제 → SDK 해제 ──
        if (listenerRegistered) brake.unregisterBrakePedalStatus(brakeListener)
        vehicle.release()
        super.onDestroy()
    }
}