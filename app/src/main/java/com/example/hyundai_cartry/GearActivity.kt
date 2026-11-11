package com.example.hyundai_cartry

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Gear
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.hyundai_cartry.ui.ButtonRow
import com.example.hyundai_cartry.ui.CarColors
import com.example.hyundai_cartry.ui.CarScreen
import com.example.hyundai_cartry.ui.LogPanel
import com.example.hyundai_cartry.ui.PillButton
import com.example.hyundai_cartry.ui.SectionLabel
import com.example.hyundai_cartry.ui.StatusCard
import com.example.hyundai_cartry.ui.theme.Hyundai_cartryTheme

// 기어 상태 확인 화면
//   [A] Google 표준 Car API : GEAR_SELECTION(선택 기어)  ← 권장 방식
//       ※ CURRENT_GEAR(실제 기어)는 에뮬레이터에서 갱신되지 않아 표시하지 않음
//   [B] Pleos SDK Gear API  : getCurrentGearSelectionState / registerGearSelection (Deprecated, 비교용)
@Suppress("DEPRECATION")   // Pleos Gear API가 Deprecated라서 경고를 숨김
class GearActivity : ComponentActivity() {

    companion object {
        private const val TAG = "GearTest"

        // 표준 기어 값(Int) → 사람이 읽을 이름
        // (안드로이드 차량 표준: N=1, R=2, P=4, D=8, 1단=16, 2단=32 ...)
        fun gearName(value: Int?): String = when (value) {
            null, 0 -> "알 수 없음"
            1 -> "N · 중립"
            2 -> "R · 후진"
            4 -> "P · 주차"
            8 -> "D · 주행"
            else -> {
                // 16=1단, 32=2단, 64=3단 ... (2의 거듭제곱)
                val step = Integer.numberOfTrailingZeros(value) - 3
                if (value > 8 && Integer.bitCount(value) == 1) "${step}단" else "알 수 없음 ($value)"
            }
        }

        // Pleos SDK 기어 이름 → 위와 같은 표시 형식
        fun pleosGearKo(name: String): String = when (name) {
            "-" -> "확인 중"
            "GEAR_PARK" -> "P · 주차"
            "GEAR_REVERSE" -> "R · 후진"
            "GEAR_NEUTRAL" -> "N · 중립"
            "GEAR_DRIVE" -> "D · 주행"
            "null", "GEAR_UNKNOWN" -> "알 수 없음"
            else -> name.removePrefix("GEAR_").let { if (it.all(Char::isDigit)) "${it}단" else it }
        }
    }

    // P R N D 를 크게 나란히 보여주고 현재 기어만 파랗게 강조
    @Composable
    private fun GearSelector(current: String) {
        val active = current.firstOrNull()?.toString()
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf("P", "R", "N", "D").forEach { letter ->
                val on = letter == active
                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 120.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (on) CarColors.Accent else CarColors.Surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        letter,
                        color = if (on) Color.White else CarColors.TextMuted,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // ───── [A] Google 표준 Car API ─────
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null

    // ───── [B] Pleos SDK ─────
    private lateinit var vehicle: Vehicle
    private lateinit var gear: Gear
    private var pleosListenerRegistered = false

    // ───── 화면 상태값 ─────
    private var googleSelectionText by mutableStateOf("확인 중")
    private var pleosSelectionText by mutableStateOf("-")
    private var logText by mutableStateOf("")

    // ─────────────────────────────────────────────
    // [A] 표준 Car API 콜백: GEAR_SELECTION 변경 시 호출
    // ─────────────────────────────────────────────
    private val carCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (value.propertyId != VehiclePropertyIds.GEAR_SELECTION) return
            val gearValue = value.value as? Int
            runOnUiThread {
                googleSelectionText = gearName(gearValue)
                addLog("[Google] 선택 기어 → ${gearName(gearValue)}")
            }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.e(TAG, "[Google] 오류 propertyId=$propertyId areaId=$areaId")
            runOnUiThread { addLog("[Google] 오류 (propertyId=$propertyId)") }
        }
    }

    // ─────────────────────────────────────────────
    // [B] Pleos SDK 리스너
    // ─────────────────────────────────────────────
    private val pleosListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            runOnUiThread {
                pleosSelectionText = gear?.name ?: "null"
                addLog("[Pleos] 선택 기어 → ${gear?.name}")
            }
        }
        override fun onFailed(e: Exception) {
            Log.e(TAG, "[Pleos] 리스너 오류", e)
            runOnUiThread { addLog("[Pleos] 리스너 오류: ${e.message}") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Hyundai_cartryTheme {
                CarScreen(
                    category = "기어",
                    headline = "지금 어떤 기어인가요?",
                    description = "Console의 Motion에서 Gear Selection을 바꾸면 두 방식 모두 바로 반영돼요.",
                    onBack = { finish() },
                    side = { LogPanel(logText) },
                ) {
                    // 큰 P R N D 표시 (Google 표준 API 값 기준)
                    GearSelector(googleSelectionText)

                    SectionLabel("방식별 값 비교")
                    StatusCard("🅶", "Google 표준 Car API", googleSelectionText, caption = "GEAR_SELECTION · 권장 방식")
                    StatusCard("🅿️", "Pleos SDK Gear API", pleosGearKo(pleosSelectionText), caption = "Deprecated · 비교용")

                    ButtonRow {
                        PillButton("현재 값 다시 조회", onClick = { refreshAll() }, icon = "↻")
                    }
                }
            }
        }

        startGoogleCarApi()
        startPleosGearApi()
    }

    // ─────────────────────────────────────────────
    // [A] 표준 Car API 시작
    // ─────────────────────────────────────────────
    private fun startGoogleCarApi() {
        try {
            // 차량 서비스에 연결 → 속성(Property) 관리자 가져오기
            car = Car.createCar(this)
            propertyManager = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager

            if (propertyManager == null) {
                googleSelectionText = "Car API 사용 불가"
                return
            }

            readGoogle()

            // 값이 바뀔 때마다 알림 받기 (ON_CHANGE 속성이므로 SENSOR_RATE_ONCHANGE)
            val ok = propertyManager?.registerCallback(
                carCallback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE
            )
            Log.d(TAG, "[Google] registerCallback GEAR_SELECTION = $ok")
        } catch (e: Exception) {
            // 권한 누락(SecurityException) 등
            Log.e(TAG, "[Google] Car API 시작 실패", e)
            googleSelectionText = "실패: ${e.javaClass.simpleName}"
            addLog("[Google] 시작 실패: ${e.message}")
        }
    }

    private fun readGoogle() {
        val pm = propertyManager ?: return
        try {
            // 기어는 차량 전체(GLOBAL) 속성이므로 areaId = 0
            googleSelectionText = gearName(pm.getIntProperty(VehiclePropertyIds.GEAR_SELECTION, 0))
            addLog("[Google] 조회 → 선택 $googleSelectionText")
        } catch (e: Exception) {
            Log.e(TAG, "[Google] 조회 실패", e)
            addLog("[Google] 조회 실패: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────
    // [B] Pleos SDK Gear API 시작 (비교용)
    // ─────────────────────────────────────────────
    private fun startPleosGearApi() {
        vehicle = Vehicle(applicationContext)
        vehicle.initialize()
        gear = vehicle.getGear()

        readPleos()
        gear.registerGearSelection(pleosListener)
        pleosListenerRegistered = true
    }

    private fun readPleos() {
        gear.getCurrentGearSelectionState(
            onComplete = { g ->
                runOnUiThread {
                    pleosSelectionText = g?.name ?: "null"
                    addLog("[Pleos] 조회 → ${g?.name}")
                }
            },
            onFailed = { e ->
                Log.e(TAG, "[Pleos] 조회 실패", e)
                runOnUiThread {
                    pleosSelectionText = "조회 실패"
                    addLog("[Pleos] 조회 실패: ${e.message}")
                }
            }
        )
    }

    private fun refreshAll() {
        readGoogle()
        readPleos()
    }

    private fun addLog(line: String) {
        logText = (listOf(line) + logText.lines().filter { it.isNotBlank() }).take(10).joinToString("\n")
    }

    override fun onDestroy() {
        // [A] 콜백 해제 → 차량 서비스 연결 해제
        propertyManager?.unregisterCallback(carCallback)
        car?.disconnect()
        // [B] 리스너 해제 → SDK 해제
        if (pleosListenerRegistered) gear.unregisterGearSelection(pleosListener)
        vehicle.release()
        super.onDestroy()
    }
}