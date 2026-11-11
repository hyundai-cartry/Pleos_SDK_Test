package com.example.hyundai_cartry

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import androidx.compose.runtime.Composable
import com.example.hyundai_cartry.ui.ButtonRow
import com.example.hyundai_cartry.ui.CarScreen
import com.example.hyundai_cartry.ui.PillButton
import com.example.hyundai_cartry.ui.SectionLabel
import com.example.hyundai_cartry.ui.StatusCard
import com.example.hyundai_cartry.ui.ValueCard
import com.example.hyundai_cartry.ui.theme.Hyundai_cartryTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocationActivity : ComponentActivity() {

    companion object {
        private const val TAG = "FusedLocationTest"
        // SDK가 요구하는 권한 이름 (AndroidManifest.xml에 선언한 것과 같은 문자열)
        private const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
    }

    private lateinit var fusedLocation: FusedLocation

    // initialize()를 호출했는지 기억해 두는 변수
    // → 호출했을 때만 onDestroy에서 release()를 호출하기 위해 사용
    private var initialized = false

    // 위치 업데이트 리스너를 등록했는지 기억해 두는 변수
    // → 등록했을 때만 unregister를 호출하기 위해 사용
    private var callbackRegistered = false

    // ───── 화면에 보여줄 상태값 (값이 바뀌면 Compose가 화면을 자동으로 다시 그림) ─────
    private var statusText by mutableStateOf("확인 중")
    private var lastKnownText by mutableStateOf("-")
    private var liveText by mutableStateOf("-")
    private var showBlockedButton by mutableStateOf(false)

    // ─────────────────────────────────────────────
    // 위치 업데이트를 계속 받는 리스너
    // ─────────────────────────────────────────────
    // 등록할 때와 해제할 때 "같은 객체"를 넘겨야 하므로 변수로 만들어 둡니다.
    private val locationListener = object : FusedLocationListener {

        // 위치가 바뀔 때마다 호출됩니다.
        override fun onChanged(location: LocationData?) {
            // 문서: location은 null일 수 있으므로 null이면 무시합니다.
            location ?: return
            Log.d(TAG, "onChanged: $location")
            // 콜백은 메인 스레드가 아닌 곳에서 오므로 메인 스레드에서 화면 상태를 바꿉니다.
            runOnUiThread { liveText = format(location) }
        }

        // 오류가 발생하면 호출됩니다. (서비스 연결 실패, 위치 접근 차단 등)
        override fun onError(exception: Exception) {
            Log.e(TAG, "onError", exception)
            runOnUiThread {
                statusText = "오류: ${exception.message}"
                // 개인정보 설정으로 위치 접근이 막힌 경우를 안내할 수 있도록 버튼을 보여줍니다.
                showBlockedButton = true
            }
        }
    }

    // ─────────────────────────────────────────────
    // 권한 요청 런처
    // ─────────────────────────────────────────────
    // launch()로 권한 팝업을 띄우고, 사용자가 선택하면 { } 안의 코드가 실행됩니다.
    // (반드시 onCreate보다 먼저, 즉 클래스 멤버 변수로 만들어야 합니다.)
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startLocation()
            } else {
                statusText = "권한 거부됨"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 화면 구성 (상태값이 바뀌면 자동으로 다시 그려짐)
        setContent {
            Hyundai_cartryTheme {
                CarScreen(
                    category = "현재 위치",
                    headline = "차량 위치를 받아올까요?",
                    description = "FusedLocation SDK로 마지막 위치와 실시간 위치를 확인해요.",
                    onBack = { finish() },
                    side = {
                        SectionLabel("실시간 위치")
                        LocationCard("📡", "위치 업데이트", liveText)
                    },
                ) {
                    StatusCard("🛰️", "SDK 상태", statusText)
                    LocationCard("📍", "마지막으로 확인된 위치", lastKnownText)

                    // 위치 접근이 차단됐을 때 시스템 안내 팝업을 띄우는 버튼
                    if (showBlockedButton) {
                        ButtonRow {
                            PillButton(
                                "위치 접근 차단 안내 보기",
                                onClick = { fusedLocation.showLocationAccessBlockedPopup() },
                                icon = "ⓘ"
                            )
                        }
                    }
                }
            }
        }

        // ── 1단계: 객체 생성 ──
        fusedLocation = FusedLocation(applicationContext)

        // ── 2단계: 지원 환경 확인 ──
        // 미지원이면 여기서 끝냅니다. (initialize를 하지 않음)
        if (!fusedLocation.isSupported()) {
            statusText = "미지원 환경"
            return
        }

        // ── 3단계: SDK 초기화 (서비스 연결 시작) ──
        fusedLocation.initialize()
        initialized = true

        // ── 4단계: 권한 확인 → 있으면 바로 시작, 없으면 요청 ──
        if (ContextCompat.checkSelfPermission(this, PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            startLocation()
        } else {
            statusText = "확인 중 · 권한 요청"
            permissionLauncher.launch(PERMISSION)
        }
    }

    // 권한이 확보된 뒤에만 호출되는 함수 (권한 없이 호출하면 SecurityException 발생)
    private fun startLocation() {
        statusText = "수신 중"

        // ── 5-1단계: 마지막 위치 1회 조회 ──
        // 새로 위치를 계산하지 않고, 이미 가지고 있는 마지막 위치를 한 번만 돌려줍니다.
        fusedLocation.getLastKnownLocation(object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                runOnUiThread {
                    lastKnownText = location?.let { format(it) } ?: "아직 없음|SDK가 계산해 둔 위치가 없어요 (null)"
                }
            }

            override fun onError(exception: Exception) {
                Log.e(TAG, "getLastKnownLocation error", exception)
                runOnUiThread { lastKnownText = "조회 실패|${exception.message}" }
            }
        })

        // ── 5-2단계: 위치 업데이트 수신 시작 ──
        fusedLocation.registerFusedLocationCallback(locationListener)
        callbackRegistered = true
    }

    // Activity가 완전히 종료될 때 호출됩니다.
    override fun onDestroy() {
        // ── 6단계: 리스너 해제 (등록했던 "같은" 리스너 객체를 넘김) ──
        if (callbackRegistered) {
            fusedLocation.unregisterFusedLocationCallback(locationListener)
            callbackRegistered = false
        }
        // ── 7단계: SDK 해제 ──
        // initialize()를 호출했다면 반드시 release()를 호출해야 합니다.
        // (unregister와 release는 서로 대체할 수 없음 — 둘 다 필요)
        if (initialized) {
            fusedLocation.release()
            initialized = false
        }
        super.onDestroy()
    }

    // LocationData를 화면에 보여줄 문자열로 바꾸는 함수
    //   "좌표|부가 정보" 형태 → LocationCard가 '|' 앞은 크게, 뒤는 작게 표시
    private fun format(loc: LocationData): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(loc.timestampMillis))
        return "%.5f, %.5f|고도 %.1f m · 방향 %.1f° · 신뢰도 %.2f · %s 기준".format(
            loc.latitude, loc.longitude, loc.altitude, loc.heading, loc.confidence, time
        )
    }

    // 위치 값 카드: "좌표|부가 정보" 문자열을 나눠서 표시
    @Composable
    private fun LocationCard(icon: String, title: String, text: String) {
        val parts = text.split("|")
        ValueCard(
            icon = icon,
            title = title,
            value = if (text == "-") "수신 대기 중" else parts[0],
            caption = parts.getOrNull(1) ?: if (text == "-") "위치가 들어오면 여기에 표시돼요" else null,
        )
    }
}