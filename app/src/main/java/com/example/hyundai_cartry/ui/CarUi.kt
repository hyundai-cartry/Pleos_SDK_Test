package com.example.hyundai_cartry.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────
// CARTRY 공통 UI 키트
//   모든 테스트 화면이 같은 모양(어두운 배경, 큰 글씨, 둥근 카드, 알약 버튼)을 쓰도록 모아 둔 파일.
//   색이나 글씨 크기를 바꾸고 싶으면 이 파일만 고치면 모든 화면에 반영됩니다.
// ─────────────────────────────────────────────────────────────

/** 화면 전체에서 쓰는 색 */
object CarColors {
    val Background = Color(0xFF15171B)   // 화면 배경
    val Surface = Color(0xFF24272D)      // 기본 카드
    val SurfaceHigh = Color(0xFF30343B)  // 보조 버튼, 아이콘 배경
    val Selected = Color(0xFF12294A)     // "켜짐" 상태 카드 (짙은 남색)
    val Accent = Color(0xFF1E8BFF)       // 강조 파랑 (주 버튼, 활성 점)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB4BAC4)
    val TextMuted = Color(0xFF7D838D)
    val Error = Color(0xFFFF6B6B)
    val Warning = Color(0xFFFFB547)
}

/** 상태 점(•)의 색과 카드 강조 여부를 정하는 값 */
enum class Tone(val dot: Color, val text: Color) {
    ON(CarColors.Accent, CarColors.TextPrimary),         // 켜짐 / 잠김 / 작동 중
    OFF(CarColors.TextMuted, CarColors.TextSecondary),   // 꺼짐 / 해제
    WARN(CarColors.Warning, CarColors.Warning),          // 안내가 필요한 상태
    ERROR(CarColors.Error, CarColors.Error),             // 실패 / 미지원
    NEUTRAL(CarColors.TextMuted, CarColors.TextMuted),   // 아직 값 없음
}

/** 한국어 상태 문구로 Tone을 자동 판단 (화면마다 따로 정하지 않아도 되게) */
fun toneOf(value: String): Tone = when {
    value == "-" || value.startsWith("확인 중") || value == "알 수 없음" -> Tone.NEUTRAL
    listOf("실패", "오류", "미지원", "거부", "지원하지 않음").any { it in value } -> Tone.ERROR
    value in setOf("꺼짐", "해제", "안 밟음") -> Tone.OFF
    else -> Tone.ON
}

// ─────────────────────────────────────────────────────────────
// 화면 틀: 상단 바 + 제목 + (넓은 화면이면) 왼쪽 내용 / 오른쪽 보조 영역
// ─────────────────────────────────────────────────────────────
@Composable
fun CarScreen(
    category: String,                 // 상단 바 오른쪽의 작은 제목 (예: "와이퍼")
    headline: String,                 // 큰 제목 (예: "와이퍼가 움직이나요?")
    description: String,              // 제목 아래 설명
    onBack: (() -> Unit)?,            // null이면 뒤로 가기 버튼을 숨김 (메뉴 화면)
    side: (@Composable ColumnScope.() -> Unit)? = null,   // 오른쪽 영역 (로그 등)
    content: @Composable ColumnScope.() -> Unit,
) {
    // 화면 전체(글씨·여백·버튼)를 UI_SCALE 배율만큼 함께 키움
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(base.density * UI_SCALE, base.fontScale)
    ) {
        CarScreenBody(category, headline, description, onBack, side, content)
    }
}

/**
 * 전체 UI 배율. 차량 화면(저밀도 대형 디스플레이)에서 잘 보이도록 1.3배로 키움.
 * 너무 크거나 작으면 이 숫자 하나만 바꾸면 됩니다. (1.0 = 원래 크기)
 */
const val UI_SCALE = 1.3f

@Composable
private fun CarScreenBody(
    category: String,
    headline: String,
    description: String,
    onBack: (() -> Unit)?,
    side: (@Composable ColumnScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarColors.Background)
            .padding(horizontal = 40.dp, vertical = 24.dp)
    ) {
        TopBar(category, onBack)
        Spacer(Modifier.height(28.dp))

        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 가로가 넓으면 2단, 좁으면 1단으로 자동 배치
            val twoColumns = side != null && maxWidth > 900.dp
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Header(category, headline, description)
                        content()
                        Spacer(Modifier.height(24.dp))
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        side?.invoke(this)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Header(category, headline, description)
                    content()
                    side?.invoke(this)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun TopBar(category: String, onBack: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(CarColors.Surface)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                // 글자(‹)는 폰트 줄 높이 때문에 아래로 치우쳐 보여서, 선 두 개로 직접 그림 → 정확히 가운데
                Canvas(Modifier.size(20.dp)) {
                    val stroke = 2.6.dp.toPx()
                    val left = size.width * 0.32f
                    val right = size.width * 0.66f
                    drawLine(CarColors.TextPrimary, Offset(right, size.height * 0.15f), Offset(left, size.height * 0.5f), stroke, StrokeCap.Round)
                    drawLine(CarColors.TextPrimary, Offset(left, size.height * 0.5f), Offset(right, size.height * 0.85f), stroke, StrokeCap.Round)
                }
            }
            Spacer(Modifier.width(16.dp))
        }
        // 앱 배지
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(CarColors.Accent),
            contentAlignment = Alignment.Center
        ) {
            Text("C", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            "CARTRY", color = CarColors.TextPrimary, fontSize = 24.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp
        )
        Spacer(Modifier.width(18.dp))
        Box(Modifier.width(1.dp).height(24.dp).background(CarColors.SurfaceHigh))
        Spacer(Modifier.width(18.dp))
        Text(category, color = CarColors.TextSecondary, fontSize = 20.sp)
    }
}

@Composable
private fun Header(category: String, headline: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pleos SDK", color = CarColors.Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text("기능 확인 · $category", color = CarColors.TextSecondary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(headline, color = CarColors.TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 50.sp)
        Text(description, color = CarColors.TextSecondary, fontSize = 20.sp, lineHeight = 30.sp)
        Spacer(Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────
// 카드
// ─────────────────────────────────────────────────────────────

/**
 * 상태 카드: 아이콘 · 제목 · (설명) · 오른쪽에 "• 값"
 * 켜짐(Tone.ON)이면 남색 배경 + 왼쪽 파란 띠로 강조 (참고 이미지의 "앞유리 김서림 제거" 카드 모양)
 */
@Composable
fun StatusCard(
    icon: String,
    title: String,
    value: String,
    caption: String? = null,
    tone: Tone = toneOf(value),
) {
    val highlighted = tone == Tone.ON
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .height(IntrinsicSize.Min)   // 왼쪽 띠가 카드 높이만큼 늘어나도록
            .clip(shape)
            .background(if (highlighted) CarColors.Selected else CarColors.Surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 왼쪽 강조 띠
        Box(
            Modifier.width(6.dp).fillMaxHeight()
                .background(if (highlighted) CarColors.Accent else Color.Transparent)
        )
        Row(
            modifier = Modifier.weight(1f).padding(horizontal = 26.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 32.sp)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = CarColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                if (!caption.isNullOrBlank()) {
                    Text(caption, color = CarColors.TextMuted, fontSize = 17.sp, lineHeight = 24.sp)
                }
            }
            Spacer(Modifier.width(16.dp))
            StatusValue(value, tone)
        }
    }
}

/**
 * 값 두 개를 한 카드에 나란히 보여주는 상태 카드
 *   예) 앞유리 | 스위치 • 연속 1단 | 동작 • 작동 중
 *   둘 중 하나라도 켜짐(Tone.ON)이면 카드 전체를 강조
 */
@Composable
fun DualStatusCard(
    icon: String,
    title: String,
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String,
    caption: String? = null,
) {
    val highlighted = toneOf(firstValue) == Tone.ON || toneOf(secondValue) == Tone.ON
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(22.dp))
            .background(if (highlighted) CarColors.Selected else CarColors.Surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(6.dp).fillMaxHeight()
                .background(if (highlighted) CarColors.Accent else Color.Transparent)
        )
        Row(
            modifier = Modifier.weight(1f).padding(horizontal = 26.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 32.sp)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = CarColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                if (!caption.isNullOrBlank()) {
                    Text(caption, color = CarColors.TextMuted, fontSize = 16.sp, lineHeight = 22.sp)
                }
            }
            LabeledValue(firstLabel, firstValue)
            Spacer(Modifier.width(28.dp))
            LabeledValue(secondLabel, secondValue)
        }
    }
}

/** 작은 이름표 + "• 값" (DualStatusCard 안에서 사용) */
@Composable
private fun LabeledValue(label: String, value: String) {
    // 최소 폭을 고정해서 값이 "꺼짐" ↔ "연속 1단"처럼 길이가 바뀌어도 열이 흔들리지 않게 함
    Column(Modifier.widthIn(min = 150.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = CarColors.TextMuted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        StatusValue(value)
    }
}

/** "• 꺼짐" 형태의 상태 표시 */
@Composable
fun StatusValue(value: String, tone: Tone = toneOf(value)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(tone.dot))
        Spacer(Modifier.width(10.dp))
        Text(value, color = tone.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 값 카드: 숫자나 좌표처럼 크게 보여줄 값용
 *   value 는 크게, caption 은 아래 작게
 */
@Composable
fun ValueCard(
    icon: String,
    title: String,
    value: String,
    caption: String? = null,
    valueColor: Color = CarColors.TextPrimary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CarColors.Surface)
            .padding(horizontal = 26.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = CarColors.TextSecondary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = valueColor, fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp)
            if (!caption.isNullOrBlank()) {
                Text(caption, color = CarColors.TextMuted, fontSize = 17.sp, lineHeight = 24.sp)
            }
        }
    }
}

@Composable
fun IconBadge(icon: String, background: Color = CarColors.SurfaceHigh, size: Int = 64) {
    Box(
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape(18.dp)).background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(icon, fontSize = (size * 0.48f).sp)
    }
}

/** 카드 묶음 위의 작은 소제목 */
@Composable
fun SectionLabel(text: String) {
    Text(
        text, color = CarColors.TextSecondary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp)
    )
}

// ─────────────────────────────────────────────────────────────
// 버튼
// ─────────────────────────────────────────────────────────────

/** 알약 모양 버튼. primary=true 면 파란색, false 면 회색 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = true,
    icon: String? = null,
) {
    Row(
        modifier = Modifier
            .height(68.dp)
            .clip(RoundedCornerShape(50))
            .background(if (primary) CarColors.Accent else CarColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 34.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Text(icon, color = Color.White, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 버튼 여러 개를 가로로 나란히 */
@Composable
fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

// ─────────────────────────────────────────────────────────────
// 로그 패널
// ─────────────────────────────────────────────────────────────
@Composable
fun LogPanel(log: String, title: String = "실시간 로그") {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CarColors.Surface)
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🧾", fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Text(title, color = CarColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = log.ifBlank { "아직 기록이 없어요. Console에서 값을 바꿔 보세요." },
            color = if (log.isBlank()) CarColors.TextMuted else CarColors.TextSecondary,
            fontSize = 18.sp,
            lineHeight = 30.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 메뉴 타일 (메인 화면)
// ─────────────────────────────────────────────────────────────
@Composable
fun MenuTile(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(min = 210.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(CarColors.Surface)
            .clickable(onClick = onClick)
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IconBadge(icon, background = CarColors.Selected, size = 68)
        Spacer(Modifier.height(4.dp))
        Text(title, color = CarColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = CarColors.TextMuted, fontSize = 18.sp, lineHeight = 26.sp)
    }
}
