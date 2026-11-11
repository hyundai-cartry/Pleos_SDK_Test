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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Window
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.listener.WindowLockStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import com.example.hyundai_cartry.ui.ButtonRow
import com.example.hyundai_cartry.ui.CarScreen
import com.example.hyundai_cartry.ui.LogPanel
import com.example.hyundai_cartry.ui.PillButton
import com.example.hyundai_cartry.ui.SectionLabel
import com.example.hyundai_cartry.ui.StatusCard
import com.example.hyundai_cartry.ui.ValueCard
import com.example.hyundai_cartry.ui.theme.Hyundai_cartryTheme
import kotlin.math.roundToInt

// 뒷좌석 창문 잠금 상태 + 뒷좌석 창문 열림 정도를 확인하는 화면
class WindowLockActivity : ComponentActivity() {

    companion object {
        private const val TAG = "WindowLockTest"
        private val REAR_WINDOWS = setOf(WindowValue.ROW_2_LEFT, WindowValue.ROW_2_RIGHT)
    }

    private lateinit var vehicle: Vehicle
    private lateinit var carWindow: Window

    // ───── 차량이 알려준 지원 영역 ─────
    // 잠금: 뒷좌석이 포함된 영역 전부 (4개 전체 / 뒷좌석 왼쪽 / 뒷좌석 오른쪽)를 내부적으로 감시
    private val lockAreas = mutableListOf<WindowArea>()
    private val positionAreas = mutableStateListOf<WindowArea>()
    private val positionBounds = mutableMapOf<WindowArea, AreaBounds<Int>>()

    // ───── 화면 상태값 ─────
    private var lockCapText by mutableStateOf("확인 중...")
    private val lockValues = mutableStateMapOf<String, Boolean>()   // 영역 이름 → 잠금 값 (내부 계산용)
    private var positionCapText by mutableStateOf("확인 중...")
    private val positionTexts = mutableStateMapOf<String, String>()
    private var logText by mutableStateOf("")

    private val lockListeners = mutableListOf<Pair<WindowArea, WindowLockStateListener>>()
    private val positionListeners = mutableListOf<Pair<WindowArea, WindowPositionListener>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Hyundai_cartryTheme {
                CarScreen(
                    category = "뒷좌석 창문",
                    headline = "뒷좌석 창문이 잠겼나요?",
                    description = "Console의 Window Lock과 Window Position을 바꾸면 바로 반영돼요.",
                    onBack = { finish() },
                    side = { LogPanel(logText) },
                ) {
                    // ── 잠금: 한 줄로 표시 ──
                    StatusCard("🔒", "뒷좌석 창문 잠금", rearLockLabel(), caption = lockCapText)

                    // ── 열림 정도 ──
                    SectionLabel("열림 정도")
                    if (positionAreas.isEmpty()) {
                        val v = if (positionCapText.startsWith("확인 중")) "확인 중" else "미지원"
                        StatusCard("🪟", "창문 위치", v, caption = positionCapText)
                    }
                    positionAreas.forEach { area ->
                        // positionTexts 값은 "40%|원래 값 4 · 범위 0~10" 형태
                        val parts = (positionTexts[areaName(area)] ?: "-").split("|")
                        ValueCard(
                            icon = "🪟",
                            title = areaKo(area),
                            value = if (parts[0] == "-") "확인 중" else parts[0],
                            caption = parts.getOrNull(1),
                        )
                    }

                    ButtonRow {
                        PillButton("현재 값 다시 조회", onClick = { refreshAll() }, icon = "↻")
                    }
                }
            }
        }

        vehicle = Vehicle(applicationContext)
        vehicle.initialize()
        carWindow = vehicle.getWindow()

        loadLockCapability()
        loadPositionCapability()
    }

    // ─────────────────────────────────────────────
    // 잠금
    // ─────────────────────────────────────────────

    // 뒷좌석 잠금 판정: 하나의 값(true/false)으로 합침
    //   - 4개 전체 영역이 true  → 잠김
    //   - 뒷좌석 왼쪽 AND 오른쪽이 모두 true → 잠김
    //   - 그 외 → 해제
    private fun isRearLocked(): Boolean {
        val combinedLocked = lockAreas
            .filter { it.values.size > 1 }                       // 여러 창문을 묶은 영역 (4개 전체)
            .any { lockValues[areaName(it)] == true }
        val leftLocked = lockValues[WindowValue.ROW_2_LEFT.name] == true
        val rightLocked = lockValues[WindowValue.ROW_2_RIGHT.name] == true
        return combinedLocked || (leftLocked && rightLocked)
    }

    private fun rearLockLabel(): String {
        if (lockValues.isEmpty()) return "확인 중"
        return if (isRearLocked()) "잠김" else "해제"
    }

    private fun loadLockCapability() {
        carWindow.checkWindowLockCapabilities(
            onComplete = { config: CapabilityConfig, areas: List<WindowArea> ->
                // 뒷좌석 창문이 포함된 영역만 사용 (4개 전체 / ROW_2_LEFT / ROW_2_RIGHT)
                val rearRelated = areas.filter { area -> area.values.any { it in REAR_WINDOWS } }
                Log.d(TAG, "잠금 capability access=${config.accessType}, 사용 영역=${rearRelated.map { areaName(it) }}")
                runOnUiThread {
                    lockCapText = if (rearRelated.isEmpty()) "뒷좌석 잠금을 지원하지 않음"
                    else "접근: ${config.accessType}"
                    lockAreas.clear()
                    lockAreas.addAll(rearRelated)
                    rearRelated.forEach { area ->
                        readLock(area)       // getCurrentWindowLockState
                        registerLock(area)   // registerWindowLockState
                    }
                }
            },
            onFailed = { e ->
                Log.e(TAG, "잠금 capability 조회 실패", e)
                runOnUiThread { lockCapText = "지원 영역 조회 실패: ${e.message}" }
            }
        )
    }

    // 영역 하나의 잠금 값을 저장하고, 합친 결과가 바뀌면 로그에 남김
    private fun updateLock(area: WindowArea, value: Boolean?) {
        val before = isRearLocked()
        val name = areaName(area)
        if (value == null) lockValues.remove(name) else lockValues[name] = value
        Log.d(TAG, "[$name] 잠금 값 = $value → 뒷좌석 잠금 = ${isRearLocked()}")
        if (before != isRearLocked()) addLog("뒷좌석 잠금 → ${isRearLocked()}")
    }

    private fun readLock(area: WindowArea) {
        carWindow.getCurrentWindowLockState(
            area,
            onComplete = { locked -> runOnUiThread { updateLock(area, locked) } },
            onFailed = { e ->
                Log.e(TAG, "[${areaName(area)}] 잠금 조회 실패", e)
                runOnUiThread { addLog("잠금 조회 실패: ${e.message}") }
            }
        )
    }

    private fun registerLock(area: WindowArea) {
        val listener = object : WindowLockStateListener {
            override fun onWindowLockStateUpdated(state: Boolean?) {
                runOnUiThread { updateLock(area, state) }
            }
            override fun onFailed(e: Exception) {
                Log.e(TAG, "[${areaName(area)}] 잠금 리스너 오류", e)
                runOnUiThread { addLog("잠금 리스너 오류: ${e.message}") }
            }
        }
        carWindow.registerWindowLockState(area, listener)
        lockListeners += area to listener
    }

    // ─────────────────────────────────────────────
    // 열림 정도 (이전과 동일)
    // ─────────────────────────────────────────────
    private fun loadPositionCapability() {
        carWindow.checkWindowPositionCapabilities(
            onComplete = { config: CapabilityConfig, map: Map<WindowArea, AreaBounds<Int>> ->
                val rear = map.filterKeys { area -> area.values.any { it in REAR_WINDOWS } }
                Log.d(TAG, "위치 capability access=${config.accessType}, " +
                        map.entries.joinToString { "${areaName(it.key)}=${it.value.minValue}~${it.value.maxValue}" })
                runOnUiThread {
                    positionCapText = rear.entries.joinToString("\n") {
                        "${areaName(it.key)} 값 범위: ${it.value.minValue} ~ ${it.value.maxValue}"
                    }.ifEmpty { "뒷좌석 창문 위치를 지원하지 않음" }
                    positionBounds.clear()
                    positionBounds.putAll(rear)
                    positionAreas.clear()
                    positionAreas.addAll(rear.keys)
                    rear.keys.forEach { area ->
                        readPosition(area)
                        registerPosition(area)
                    }
                }
            },
            onFailed = { e ->
                Log.e(TAG, "위치 capability 조회 실패", e)
                runOnUiThread { positionCapText = "지원 영역 조회 실패: ${e.message}" }
            }
        )
    }

    private fun readPosition(area: WindowArea) {
        val name = areaName(area)
        carWindow.getCurrentWindowPosition(
            area,
            onComplete = { pos -> runOnUiThread { positionTexts[name] = positionLabel(area, pos) } },
            onFailed = { e ->
                Log.e(TAG, "[$name] 위치 조회 실패", e)
                runOnUiThread { positionTexts[name] = "조회 실패|${e.message}" }
            }
        )
    }

    private fun registerPosition(area: WindowArea) {
        val name = areaName(area)
        val listener = object : WindowPositionListener {
            override fun onWindowPositionUpdated(position: Int?) {
                runOnUiThread {
                    positionTexts[name] = positionLabel(area, position)
                    addLog("$name 위치 변경 → 원래 값 $position")
                }
            }
            override fun onFailed(e: Exception) {
                Log.e(TAG, "[$name] 위치 리스너 오류", e)
                runOnUiThread { addLog("$name 위치 리스너 오류: ${e.message}") }
            }
        }
        carWindow.registerWindowPosition(area, listener)
        positionListeners += area to listener
    }

    private fun positionLabel(area: WindowArea, raw: Int?): String {
        // "큰 값|작은 설명" 형태로 반환 → 화면에서 '|' 기준으로 나눠 표시
        if (raw == null) return "알 수 없음"
        val b = positionBounds[area] ?: return "$raw|범위 정보 없음"
        val span = b.maxValue - b.minValue
        if (span <= 0) return "$raw"
        val percent = ((raw - b.minValue) * 100.0 / span).roundToInt().coerceIn(0, 100)
        return "$percent% 열림|원래 값 $raw · 범위 ${b.minValue}~${b.maxValue} (10% 단위)"
    }

    // 영역 → 화면에 보여줄 한국어 이름
    private fun areaKo(area: WindowArea) = area.values.joinToString(" · ") {
        when (it) {
            WindowValue.ROW_2_LEFT -> "뒷좌석 왼쪽"
            WindowValue.ROW_2_RIGHT -> "뒷좌석 오른쪽"
            WindowValue.ROW_1_LEFT -> "운전석"
            WindowValue.ROW_1_RIGHT -> "조수석"
            else -> it.name
        }
    }

    // ─────────────────────────────────────────────
    // 공통
    // ─────────────────────────────────────────────
    private fun areaName(area: WindowArea) = area.values.joinToString("+") { it.name }

    private fun refreshAll() {
        lockAreas.forEach { readLock(it) }
        positionAreas.forEach { readPosition(it) }
    }

    private fun addLog(line: String) {
        logText = (listOf(line) + logText.lines().filter { it.isNotBlank() }).take(10).joinToString("\n")
    }

    override fun onDestroy() {
        lockListeners.forEach { (area, l) -> carWindow.unregisterWindowLockState(area, l) }
        positionListeners.forEach { (area, l) -> carWindow.unregisterWindowPosition(area, l) }
        vehicle.release()
        super.onDestroy()
    }
}