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
import ai.pleos.playground.vehicle.api.Light
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.listener.LightStateListener
import com.example.hyundai_cartry.ui.ButtonRow
import com.example.hyundai_cartry.ui.CarScreen
import com.example.hyundai_cartry.ui.LogPanel
import com.example.hyundai_cartry.ui.PillButton
import com.example.hyundai_cartry.ui.StatusCard
import com.example.hyundai_cartry.ui.ValueCard
import com.example.hyundai_cartry.ui.theme.Hyundai_cartryTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 전조등(하향등, Low Beam) 상태를 확인하는 화면
//   getCurrentLowBeamLightState : 현재 상태 1회 조회
//   registerLowBeamLightState   : 상태가 바뀔 때마다 알림
//   (참고) getLowBeamLightSwitch : 운전자가 놓은 라이트 스위치 위치 1회 조회 — 실시간 알림 API는 없음
class LowBeamActivity : ComponentActivity() {

    companion object {
        private const val TAG = "LowBeamTest"
    }

    private lateinit var vehicle: Vehicle
    private lateinit var light: Light
    private var listenerRegistered = false

    // ───── 화면 상태값 ─────
    private var capText by mutableStateOf("확인 중...")
    private var stateText by mutableStateOf("확인 중")
    private var switchText by mutableStateOf("-")
    private var changeCount by mutableIntStateOf(0)   // 리스너로 값이 들어온 횟수
    private var logText by mutableStateOf("")

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    // ─────────────────────────────────────────────
    // 하향등 상태 실시간 리스너
    // ─────────────────────────────────────────────
    private val lowBeamListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) {
            Log.d(TAG, "[변경] $state")
            runOnUiThread {
                changeCount++
                stateText = stateLabel(state)
                addLog("상태 변경 → ${state?.name ?: "null"}")
            }
            // 상태가 바뀌었으면 스위치 위치도 함께 다시 읽어 비교
            readSwitch()
        }

        override fun onFailed(e: Exception) {
            Log.e(TAG, "리스너 오류", e)
            runOnUiThread { addLog("리스너 오류: ${e.message}") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Hyundai_cartryTheme {
                CarScreen(
                    category = "전조등",
                    headline = "전조등이 켜졌나요?",
                    description = "Console의 Exterior Lights Mode를 Low beam으로 바꾸면 하향등이 켜져요.",
                    onBack = { finish() },
                    side = { LogPanel(logText) },
                ) {
                    StatusCard("🔦", "하향등 (Low Beam)", stateText, caption = capText)
                    StatusCard("🎚️", "라이트 스위치", switchKo(switchText), caption = "참고용 · 실시간 알림 없음")
                    ValueCard("🔁", "변경 알림", "${changeCount}회", "하향등이 켜지고 꺼진 횟수")

                    ButtonRow {
                        PillButton("현재 값 다시 조회", onClick = { readAll() }, icon = "↻")
                    }
                }
            }
        }

        // ── 1단계: SDK 생성 + 초기화 → Light API 가져오기 ──
        vehicle = Vehicle(applicationContext)
        vehicle.initialize()
        light = vehicle.getLight()

        // ── 2단계: 지원 여부 확인 → 지원하면 조회 + 감시 시작 ──
        checkCapability()
    }

    private fun checkCapability() {
        // Deprecated 함수와 구분되도록 람다 파라미터 타입을 명시
        light.checkLowBeamLightStateCapabilities(
            onComplete = { config: CapabilityConfig, states: List<LightState> ->
                Log.d(TAG, "capability access=${config.accessType}, 지원 상태=$states")
                runOnUiThread {
                    capText = "접근: ${config.accessType} / 지원 값: ${states.joinToString { it.name }}"
                    readAll()                                         // getCurrentLowBeamLightState
                    light.registerLowBeamLightState(lowBeamListener)  // registerLowBeamLightState
                    listenerRegistered = true
                }
            },
            onFailed = { e ->
                Log.e(TAG, "capability 조회 실패", e)
                runOnUiThread { capText = "지원 여부 조회 실패: ${e.message}" }
            }
        )
    }

    // 하향등 상태 1회 조회
    private fun readState() {
        light.getCurrentLowBeamLightState(
            onComplete = { state ->
                Log.d(TAG, "[조회] 상태 $state")
                runOnUiThread {
                    stateText = stateLabel(state)
                    addLog("상태 조회 → ${state?.name ?: "null"}")
                }
            },
            onFailed = { e ->
                Log.e(TAG, "상태 조회 실패", e)
                runOnUiThread {
                    stateText = "조회 실패"
                    addLog("상태 조회 실패: ${e.message}")
                }
            }
        )
    }

    // (참고) 라이트 스위치 위치 1회 조회
    private fun readSwitch() {
        light.getLowBeamLightSwitch(
            onComplete = { sw ->
                Log.d(TAG, "[조회] 스위치 $sw")
                runOnUiThread { switchText = sw?.name ?: "null" }
            },
            onFailed = { e ->
                Log.e(TAG, "스위치 조회 실패", e)
                runOnUiThread { switchText = "조회 실패" }
            }
        )
    }

    private fun readAll() {
        readState()
        readSwitch()
    }

    private fun stateLabel(state: LightState?) = when (state) {
        LightState.ON -> "켜짐"
        LightState.OFF -> "꺼짐"
        LightState.DAYTIME_RUNNING -> "주간 주행등"
        null -> "알 수 없음"
    }

    // 스위치 이름 → 한국어
    private fun switchKo(raw: String) = when (raw) {
        "-" -> "확인 중"
        "null" -> "알 수 없음"
        "OFF" -> "꺼짐"
        "ON" -> "켜짐"
        "AUTOMATIC" -> "자동"
        "DAYTIME_RUNNING" -> "주간 주행등"
        else -> raw
    }

    private fun addLog(line: String) {
        val stamped = "${timeFormat.format(Date())}  $line"
        logText = (listOf(stamped) + logText.lines().filter { it.isNotBlank() }).take(12).joinToString("\n")
    }

    override fun onDestroy() {
        // ── 리스너 해제 → SDK 해제 ──
        if (listenerRegistered) light.unregisterLowBeamLightState(lowBeamListener)
        vehicle.release()
        super.onDestroy()
    }
}
