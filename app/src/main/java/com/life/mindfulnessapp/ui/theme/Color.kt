package com.life.mindfulnessapp.ui.theme

import androidx.compose.ui.graphics.Color

// ══════════════════════════════════════════════════════════════════════════════
//  双主题色彩体系 —— 有意识时限 · UI 设计 v2
//
//  设计原则：
//  ① 护眼优先：夜间绿降饱和至 #26BB68，避免 OLED 长期视觉疲劳
//  ② 层次鲜明：背景 → 卡片 → 浮层 三层明度差 ≥ 8%，保证"悬浮感"
//  ③ 语义清晰：绿=正向/活跃、琥珀=警告、红=危险、蓝灰=中性信息
//  ④ 品牌统一：accent 绿贯穿两套主题，日间降亮度 / 夜间降饱和度
//
//  ─ 夜间（Night）：深蓝灰底 #0F1117 + 护眼翠绿 #26BB68
//  ─ 日间（Day） ：浅暖灰底 #F4F6F9 + 清新绿   #1B9E55
// ══════════════════════════════════════════════════════════════════════════════

// ── 品牌绿（护眼版，饱和度从 Emerald 降至 Medium Sea Green）────────────────
val LogoGreen       = Color(0xFF26BB68)   // 主品牌绿（-8% 饱和度，更护眼）
val LogoGreenBright = Color(0xFF43CC7E)   // 亮版：用于深色背景上的高亮文字
val LogoGreenDeep   = Color(0xFF1A9E55)   // 深版：按压态、日间 Header 渐变终止色

// 兼容别名
val LogoGreenLight = LogoGreenBright
val LogoGreenMid   = LogoGreen

// ── 功能色（语义明确，两套主题共用）────────────────────────────────────────
val MindfulGreen40   = Color(0xFF26BB68)   // 与 LogoGreen 统一
val MindfulGreenDark = Color(0xFF1A9E55)   // 按压态
val WarningColor     = Color(0xFFE8941A)   // 琥珀橙：更沉稳，不刺眼
val DangerColor      = Color(0xFFCF4040)   // 砖红：比 E74C3C 更内敛
val SuccessColor     = MindfulGreen40

// ── 热力图专用色（正念语义）────────────────────────────────────────────────
// 未超限：宁静蓝灰（中性，仅表达"有使用"）
val HeatmapNeutral  = Color(0xFF5B8FAD)   // 蓝灰，稍降饱和，与 tertiary 区分
// 超限：暖橙陶土（警觉感）
val HeatmapWarn     = Color(0xFFCE8555)   // 暖橙陶土色，与 WarningColor 同系

// ════════════════════════════════════════════════════════════════════════════
//  夜间主题（Night Theme）v2
//  设计目标：
//   ① 背景三层明度差明显（0F1117 → 1A1E2C → 222840），卡片有真实"浮起感"
//   ② 护眼绿替代高饱和翠绿，长时间使用不疲劳
//   ③ 消除蓝紫第三色造成的冷热撞色，改用冷蓝灰（信息语义）
// ════════════════════════════════════════════════════════════════════════════

// 背景（三层拉开约 8–10 亮度单位）
val NightBg          = Color(0xFF0F1117)   // L1 全局背景：接近纯黑，AMOLED 省电
val NightBgVariant   = Color(0xFF171B25)   // L1.5 略深内容区背景（如侧栏）

// 卡片（比背景亮 ~10–12 单位，明显可见浮起）
val NightCardBg       = Color(0xFF1A1E2C)   // L2 主卡片：深蓝灰（+11 vs NightBg）
val NightCardElevated = Color(0xFF222840)   // L3 略高卡片：弹层/Sheet 容器（+8 vs CardBg）
val NightCardGreen    = Color(0xFF172235)   // L2 高亮卡片（极淡蓝绿调，无压抑感）
val NightCardMonitor  = Color(0xFF192038)   // L2 已监控卡片（冷蓝调，清晰区分状态）

// 顶部 Header 区域
val NightHeaderBg     = Color(0xFF182035)   // 深蓝 Header（统一蓝灰调）
val NightHeaderBgDark = Color(0xFF101828)   // Header 内渐变深色端

// 文字（拉开三档，增强可读性）
val NightTextPrimary   = Color(0xFFEDF0F7)   // 主文字：蓝白（比纯白柔和，减少眩光）
val NightTextSecondary = Color(0xFF8A96B0)   // 次文字：蓝灰，alpha 0.55 替代方案
val NightTextHint      = Color(0xFF48526A)   // 提示文字：更深蓝灰（alpha 0.3 替代）
val NightTextGreen     = LogoGreen           // 绿色标签文字

// 分割线 / 边框（比卡片暗，但可见）
val NightDivider      = Color(0xFF1D2438)   // 细分割线（比 CardBg 稍暗）
val NightBorder       = Color(0xFF273045)   // 卡片边框（比分割线稍亮）

// Dock/导航栏
val NightDockBg       = Color(0xFF0F1117)   // 与背景一致，无断层

// ════════════════════════════════════════════════════════════════════════════
//  日间主题（Day Theme）v2
//  设计目标：
//   ① 背景改用暖灰而非冷灰，减少"医疗感"，更有温度
//   ② 卡片白色加 1dp 透明边框模拟阴影，与背景区分（对比度 1.15:1）
//   ③ Header 改用渐变（深→浅），比纯色块更轻盈
//   ④ primary 与 secondary 绿色拉开明度差，Chip 选中态清晰
//   ⑤ 已监控卡片背景改为极淡薄荷（#EAF6EF），比 C8F0DC 轻盈不沉重
// ════════════════════════════════════════════════════════════════════════════

// 背景（暖灰底，有温度感）
val DayBg             = Color(0xFFF4F6F9)   // 全局背景：偏冷暖中性（改自 F5F7FA）
val DayBgVariant      = Color(0xFFEBEFF5)   // 背景层次变体（略暗）

// 卡片
val DayCardBg         = Color(0xFFFFFFFF)   // 主卡片：纯白（通过 1dp outline 区分）
val DayCardElevated   = Color(0xFFF7F9FC)   // 沉底卡片：极淡蓝灰（二级内容区）
val DayCardGreen      = Color(0xFFEAF6EF)   // 薄荷绿卡片：比 E8F5EE 更淡更轻盈
val DayCardMonitor    = Color(0xFFE2F4EB)   // 已监控卡片：清晰但不沉重

// 顶部 Header（渐变两端色）
val DayHeaderBg       = Color(0xFF1B9E55)   // Header 渐变起始色（比旧值暗 10%，更厚重有力）
val DayHeaderBgDark   = Color(0xFF157842)   // Header 渐变深色端（更深，层次感）
val DayHeaderBgLight  = Color(0xFF26BB68)   // Header 渐变浅色端（与品牌绿对接）

// 文字
val DayTextPrimary    = Color(0xFF191D2B)   // 主文字：近黑蓝（比旧 #1A1D2E 略暖）
val DayTextSecondary  = Color(0xFF556070)   // 次文字：暖蓝灰（比旧 #5C6B7E 偏暖）
val DayTextHint       = Color(0xFF9BA6B5)   // 提示文字：浅灰蓝（对比度符合 AA-Large）
val DayTextGreen      = Color(0xFF196B40)   // 绿色标签（日间加深，对比度 ≥ 4.5:1）

// 分割线 / 边框（日间要更细腻）
val DayDivider        = Color(0xFFE0E6EF)   // 细分割线（比旧 E2E8F0 略暗，更明显）
val DayBorder         = Color(0xFFCBD4E2)   // 卡片边框（模拟阴影感，1dp）

// Dock/导航栏
val DayDockBg         = Color(0xFFFFFFFF)   // 白色底

// ════════════════════════════════════════════════════════════════════════════
//  旧版兼容别名（保持其他文件不报错，默认指向夜间主题值）
// ════════════════════════════════════════════════════════════════════════════
val AppBg           = NightBg
val AppBgVariant    = NightBgVariant
val CardBg          = NightCardBg
val CardBgElevated  = NightCardElevated
val CardBgGreen     = NightCardGreen
val CardBgMonitored = NightCardMonitor
val GreenCardBg     = NightHeaderBg
val GreenCardBgDark = NightHeaderBgDark
val GreenCardAccent = LogoGreen
val TextPrimary     = NightTextPrimary
val TextSecondary   = NightTextSecondary
val TextHint        = NightTextHint
val TextGreen       = NightTextGreen
val Divider         = NightDivider
val BorderSubtle    = NightBorder
val BackgroundLight = DayBg
val BackgroundDark  = NightBg
val SurfaceLight    = DayCardBg
val SurfaceDark     = NightCardBg
val SurfaceVariantLight = DayCardGreen
val SurfaceVariantDark  = NightCardGreen
val HeaderBg        = NightHeaderBg
val DockBg          = NightDockBg
val HeaderText      = LogoGreenBright
val MindfulGreen80  = LogoGreenBright
val MindfulGreenGrey80 = Color(0xFF70BFA0)   // 略调整，与新品牌绿协调
val MindfulTeal80   = Color(0xFF56BFBA)      // 保持蓝绿调
val MindfulGreen20  = Color(0xFF091D35)      // 深蓝调，夜间卡片内衬色
