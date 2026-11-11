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
import ai.pleos.playground.vehicle.api.HVAC
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.listener.HvacDefrosterStateListener
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import com.example.hyundai_cartry.ui.ButtonRow
import com.example.hyundai_cartry.ui.CarScreen
import com.example.hyundai_cartry.ui.LogPanel
import com.example.hyundai_cartry.ui.PillButton
import com.example.hyundai_cartry.ui.SectionLabel
import com.example.hyundai_cartry.ui.StatusCard
import com.example.hyundai_cartry.ui.ValueCard
import com.example.hyundai_cartry.ui.theme.Hyundai_cartryTheme

// 앞유리 성에 제거(디프로스터) 상태 + 공조 풍량을 확인하는 화면
// 차량이 "지원한다고 알려준 영역"만 조회/감시 (capability 기반)
class DefrostActivity : ComponentActivity() {

    companion object {
        private const val TAG = "DefrostTest"
    }

    private lateinit var vehicle: Vehicle
    private lateinit var hvac: HVAC

    // ───── 차량이 알려준 지원 영역 ─────
    private val defrostAreas = mutableStateListOf<WindowArea>()          // 앞유리가 포함된 영역
    private val fanAreas = mutableStateListOf<SeatArea>()                // 풍량 지원 좌석 영역
    private val fanBounds = mutableMapOf<SeatArea, AreaBounds<Int>>()    // 풍량 최소/최대

    // ───── 화면 상태값 ─────
    private var defrostCapText by mutableStateOf("확인 중...")
    private val defrostTexts = mutableStateMapOf<String, String>()
    private var fanCapText by mutableStateOf("확인 중...")
    private val fanTexts = mutableStateMapOf<String, String>()
    private var logText by mutableStateOf("")

    // 해제용으로 등록한 리스너 기억
    private val defrostListeners = mutableListOf<Pair<WindowArea, HvacDefrosterStateListener>>()
    private val fanListeners = mutableListOf<Pair<SeatArea, HvacFanSpeedListener>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Hyundai_cartryTheme {
                CarScreen(
                    category = "앞유리 김서림",
                    headline = "앞유리 김서림을 없애볼까요?",
                    description = "Console의 Climate에서 제상과 풍량을 바꾸면 바로 반영돼요.",
                    onBack = { finish() },
                    side = { LogPanel(logText) },
                ) {
                    // ── 성에 제거 ──
                    if (defrostAreas.isEmpty()) {
                        val v = if (defrostCapText.startsWith("확인 중")) "확인 중" else "미지원"
                        StatusCard("❄️", "앞유리 김서림 제거", v, caption = defrostCapText)
                    }
                    defrostAreas.forEach { area ->
                        StatusCard(
                            "❄️", "앞유리 김서림 제거",
                            defrostTexts[windowName(area)] ?: "확인 중",
                            caption = defrostCapText,
                        )
                    }

                    // ── 풍량 ──
                    SectionLabel("공조 풍량")
                    if (fanAreas.isEmpty()) {
                        val v = if (fanCapText.startsWith("확인 중")) "확인 중" else "미지원"
                        StatusCard("🌀", "풍량", v, caption = fanCapText)
                    }
                    fanAreas.forEach { area ->
                        // fanTexts 값은 "3단|범위 0~8" 형태
                        val parts = (fanTexts[seatName(area)] ?: "확인 중").split("|")
                        ValueCard("🌀", "풍량 · ${seatKo(area)}", parts[0], parts.getOrNull(1))
                    }

                    ButtonRow {
                        PillButton("현재 값 다시 조회", onClick = { refreshAll() }, icon = "↻")
                    }
                }
            }
        }

        // ── SDK 생성 + 초기화 → HVAC(공조) API 가져오기 ──
        vehicle = Vehicle(applicationContext)
        vehicle.initialize()
        hvac = vehicle.getHVAC()

        // ── 지원 영역 확인 후, 그 영역만 조회 + 감시 ──
        loadDefrostCapability()
        loadFanCapability()
    }

    // ─────────────────────────────────────────────
    // 성에 제거 (디프로스터)
    // ─────────────────────────────────────────────
    private fun loadDefrostCapability() {
        // Deprecated 함수와 구분되도록 람다 파라미터 타입을 명시
        hvac.checkHvacDefrosterCapabilities(
            onComplete = { config: CapabilityConfig, areas: List<WindowArea> ->
                // 지원 영역 중 앞유리(FRONT_WINDSHIELD)가 포함된 영역만 사용
                val front = areas.filter { area -> area.values.any { it == WindowValue.FRONT_WINDSHIELD } }
                Log.d(TAG, "디프로스터 capability access=${config.accessType}, " +
                        "전체=${areas.map { windowName(it) }}, 사용=${front.map { windowName(it) }}")
                runOnUiThread {
                    defrostCapText = if (front.isEmpty())
                        "앞유리 디프로스터를 지원하지 않음 (지원: ${areas.joinToString { windowName(it) }})"
                    else "접근: ${config.accessType}"
                    defrostAreas.clear()
                    defrostAreas.addAll(front)
                    front.forEach { area ->
                        readDefrost(area)       // getHvacDefrosterState
                        registerDefrost(area)   // registerHvacDefrosterState
                    }
                }
            },
            onFailed = { e ->
                Log.e(TAG, "디프로스터 capability 조회 실패", e)
                runOnUiThread { defrostCapText = "지원 영역 조회 실패: ${e.message}" }
            }
        )
    }

    private fun readDefrost(area: WindowArea) {
        val name = windowName(area)
        hvac.getHvacDefrosterState(
            area,
            onComplete = { on ->
                Log.d(TAG, "[$name] 디프로스터 조회 = $on")
                runOnUiThread {
                    defrostTexts[name] = defrostLabel(on)
                    addLog("디프로스터 조회 → $on")
                }
            },
            onFailed = { e ->
                Log.e(TAG, "[$name] 디프로스터 조회 실패", e)
                runOnUiThread { defrostTexts[name] = "조회 실패" }
            }
        )
    }

    private fun registerDefrost(area: WindowArea) {
        val name = windowName(area)
        val listener = object : HvacDefrosterStateListener {
            override fun onHvacDefrosterStateUpdated(state: Boolean?) {
                Log.d(TAG, "[$name] 디프로스터 변경 → $state")
                runOnUiThread {
                    defrostTexts[name] = defrostLabel(state)
                    addLog("디프로스터 변경 → $state")
                }
            }
            override fun onFailed(e: Exception) {
                Log.e(TAG, "[$name] 디프로스터 리스너 오류", e)
                runOnUiThread { addLog("디프로스터 리스너 오류: ${e.message}") }
            }
        }
        hvac.registerHvacDefrosterState(area, listener)
        defrostListeners += area to listener
    }

    // ─────────────────────────────────────────────
    // 풍량
    // ─────────────────────────────────────────────
    private fun loadFanCapability() {
        hvac.checkHvacFanSpeedCapabilities(
            onComplete = { config: CapabilityConfig, map: Map<SeatArea, AreaBounds<Int>> ->
                Log.d(TAG, "풍량 capability access=${config.accessType}, " +
                        map.entries.joinToString { "${seatName(it.key)}=${it.value.minValue}~${it.value.maxValue}" })
                runOnUiThread {
                    fanCapText = map.entries.joinToString("\n") {
                        "${seatName(it.key)} 범위: ${it.value.minValue} ~ ${it.value.maxValue}"
                    }.ifEmpty { "풍량을 지원하지 않음" }
                    fanBounds.clear()
                    fanBounds.putAll(map)
                    fanAreas.clear()
                    fanAreas.addAll(map.keys)
                    map.keys.forEach { area ->
                        readFan(area)       // getHvacFanSpeed (초기값)
                        registerFan(area)   // registerHvacFanSpeed
                    }
                }
            },
            onFailed = { e ->
                Log.e(TAG, "풍량 capability 조회 실패", e)
                runOnUiThread { fanCapText = "지원 영역 조회 실패: ${e.message}" }
            }
        )
    }

    // 리스너는 "바뀔 때" 알려주므로, 화면을 열자마자 현재 값을 보여주기 위해 1회 조회
    private fun readFan(area: SeatArea) {
        val name = seatName(area)
        hvac.getHvacFanSpeed(
            area,
            onComplete = { speed -> runOnUiThread { fanTexts[name] = fanLabel(area, speed) } },
            onFailed = { e ->
                Log.e(TAG, "[$name] 풍량 조회 실패", e)
                runOnUiThread { fanTexts[name] = "조회 실패|${e.message}" }
            }
        )
    }

    private fun registerFan(area: SeatArea) {
        val name = seatName(area)
        val listener = object : HvacFanSpeedListener {
            override fun onHvacFanSpeedUpdated(speed: Int?) {
                Log.d(TAG, "[$name] 풍량 변경 → $speed")
                runOnUiThread {
                    fanTexts[name] = fanLabel(area, speed)
                    addLog("$name 풍량 변경 → $speed")
                }
            }
            override fun onFailed(e: Exception) {
                Log.e(TAG, "[$name] 풍량 리스너 오류", e)
                runOnUiThread { addLog("$name 풍량 리스너 오류: ${e.message}") }
            }
        }
        hvac.registerHvacFanSpeed(area, listener)
        fanListeners += area to listener
    }

    // ─────────────────────────────────────────────
    // 표시용 도우미
    // ─────────────────────────────────────────────
    private fun defrostLabel(on: Boolean?) = when (on) {
        true -> "켜짐"
        false -> "꺼짐"
        null -> "알 수 없음"
    }

    // 풍량: "3단|범위 0~8" 형태 → 화면에서 '|' 기준으로 큰 값/작은 설명으로 나눠 표시
    private fun fanLabel(area: SeatArea, speed: Int?): String {
        if (speed == null) return "알 수 없음"
        val b = fanBounds[area] ?: return "${speed}단"
        return "${speed}단|범위 ${b.minValue}~${b.maxValue}단"
    }

    // 좌석 영역 → 화면에 보여줄 한국어 이름
    private fun seatKo(area: SeatArea): String {
        if (area.values.size >= 3) return "전체 좌석"
        return area.values.joinToString(" · ") {
            when (it.name) {
                "ROW_1_LEFT" -> "운전석"
                "ROW_1_RIGHT" -> "조수석"
                "ROW_2_LEFT" -> "뒷좌석 왼쪽"
                "ROW_2_RIGHT" -> "뒷좌석 오른쪽"
                else -> it.name
            }
        }
    }

    private fun windowName(area: WindowArea) = area.values.joinToString("+") { it.name }
    private fun seatName(area: SeatArea) = area.values.joinToString("+") { it.name }

    private fun refreshAll() {
        defrostAreas.forEach { readDefrost(it) }
        fanAreas.forEach { readFan(it) }
    }

    private fun addLog(line: String) {
        logText = (listOf(line) + logText.lines().filter { it.isNotBlank() }).take(10).joinToString("\n")
    }

    override fun onDestroy() {
        // ── 리스너 전부 해제 → SDK 해제 ──
        defrostListeners.forEach { (area, l) -> hvac.unregisterHvacDefrosterState(area, l) }
        fanListeners.forEach { (area, l) -> hvac.unregisterHvacFanSpeed(area, l) }
        vehicle.release()
        super.onDestroy()
    }
}