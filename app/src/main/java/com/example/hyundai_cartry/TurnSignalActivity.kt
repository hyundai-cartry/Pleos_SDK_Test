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
import ai.pleos.playground.vehicle.api.TurnSignal
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
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

// 방향 지시등 상태(OFF / LEFT / RIGHT)를 확인하는 화면
//   getCurrentTurnSignalState : 현재 상태 1회 조회
//   registerTurnSignalState   : 상태가 바뀔 때마다 알림
class TurnSignalActivity : ComponentActivity() {

    companion object {
        private const val TAG = "TurnSignalTest"
    }

    private lateinit var vehicle: Vehicle
    private lateinit var turnSignal: TurnSignal
    private var listenerRegistered = false

    // ───── 화면 상태값 ─────
    private var capText by mutableStateOf("확인 중...")
    private var stateText by mutableStateOf("확인 중")
    private var changeCount by mutableIntStateOf(0)   // 리스너로 값이 들어온 횟수
    private var logText by mutableStateOf("")

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    // ─────────────────────────────────────────────
    // 방향 지시등 상태 실시간 리스너
    // ─────────────────────────────────────────────
    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
            // Logcat에도 남겨서 Console 조작과 시각을 비교할 수 있게 함
            Log.d(TAG, "[변경] $state")
            runOnUiThread {
                changeCount++
                stateText = label(state)
                addLog("변경 → ${state?.name ?: "null"}")
            }
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
                    category = "방향 지시등",
                    headline = "방향 지시등이 켜졌나요?",
                    description = "Console의 Turn Signal State를 바꾸면 바로 반영돼요. Blink와 Flash는 같은 값으로 보이고, 비상등은 이 API로 확인할 수 없어요.",
                    onBack = { finish() },
                    side = { LogPanel(logText) },
                ) {
                    StatusCard("↔️", "방향 지시등", stateText, caption = capText)
                    ValueCard("🔁", "변경 알림", "${changeCount}회", "Console 조작이 앱에 전달된 횟수")

                    ButtonRow {
                        PillButton("현재 값 다시 조회", onClick = { readState() }, icon = "↻")
                    }
                }
            }
        }

        // ── 1단계: SDK 생성 + 초기화 → TurnSignal API 가져오기 ──
        vehicle = Vehicle(applicationContext)
        vehicle.initialize()
        turnSignal = vehicle.getTurnSignal()

        // ── 2단계: 지원 여부 확인 → 지원하면 조회 + 감시 시작 ──
        checkCapability()
    }

    private fun checkCapability() {
        turnSignal.checkTurnSignalStateCapabilities(
            onComplete = { config: CapabilityConfig ->
                Log.d(TAG, "capability access=${config.accessType}")
                runOnUiThread {
                    capText = "접근: ${config.accessType}"
                    readState()                                            // getCurrentTurnSignalState
                    turnSignal.registerTurnSignalState(turnSignalListener) // registerTurnSignalState
                    listenerRegistered = true
                }
            },
            onFailed = { e ->
                Log.e(TAG, "capability 조회 실패", e)
                runOnUiThread { capText = "지원 여부 조회 실패: ${e.message}" }
            }
        )
    }

    // 현재 상태 1회 조회
    private fun readState() {
        turnSignal.getCurrentTurnSignalState(
            onComplete = { state ->
                Log.d(TAG, "[조회] $state")
                runOnUiThread {
                    stateText = label(state)
                    addLog("조회 → ${state?.name ?: "null"}")
                }
            },
            onFailed = { e ->
                Log.e(TAG, "조회 실패", e)
                runOnUiThread {
                    stateText = "조회 실패"
                    addLog("조회 실패: ${e.message}")
                }
            }
        )
    }

    // SDK 값 → 화면 표시
    // null은 SDK가 모르는 값(예: 비상등처럼 양쪽 동시)이 들어온 경우일 수 있음
    private fun label(state: TurnSignalValue?) = when (state) {
        TurnSignalValue.LEFT -> "◀ 왼쪽"
        TurnSignalValue.RIGHT -> "오른쪽 ▶"
        TurnSignalValue.OFF -> "꺼짐"
        null -> "알 수 없음"
    }

    // 깜빡임 주기를 볼 수 있도록 시각(밀리초)을 함께 기록
    private fun addLog(line: String) {
        val stamped = "${timeFormat.format(Date())}  $line"
        logText = (listOf(stamped) + logText.lines().filter { it.isNotBlank() }).take(12).joinToString("\n")
    }

    override fun onDestroy() {
        // ── 리스너 해제 → SDK 해제 ──
        if (listenerRegistered) turnSignal.unregisterTurnSignalState(turnSignalListener)
        vehicle.release()
        super.onDestroy()
    }
}
