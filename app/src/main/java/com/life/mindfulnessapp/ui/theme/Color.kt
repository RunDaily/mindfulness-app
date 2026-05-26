package com.life.mindfulnessapp.ui.theme

import androidx.compose.ui.graphics.Color

// ══════════════════════════════════════════════════════════════════════════════
//  双主题色彩体系 —— 参考深色胶囊UI设计风格
//  ─ 夜间（Night）：深蓝黑底 + 翠绿高亮，沉浸、护眼
//  ─ 日间（Day） ：白/浅灰底 + 清新绿，明亮、轻盈
// ══════════════════════════════════════════════════════════════════════════════

// ── Logo 翠绿（两套主题共用，品牌一致性）───────────────────────────────────
val LogoGreen       = Color(0xFF2ECC71)   // 参考图翠绿（更饱和、更鲜亮）
val LogoGreenBright = Color(0xFF4DDA88)   // 亮版绿色
val LogoGreenDeep   = Color(0xFF27AE60)   // 深按压态绿

// 兼容别名
val LogoGreenLight = LogoGreenBright
val LogoGreenMid   = LogoGreen

// ── 功能色（两套主题共用）────────────────────────────────────────────────────
val MindfulGreen40  = Color(0xFF2ECC71)   // 与 Logo 绿统一
val MindfulGreenDark = Color(0xFF27AE60)  // 深按压态
val WarningColor    = Color(0xFFF39C12)   // 琥珀色警告
val DangerColor     = Color(0xFFE74C3C)   // 红色危险
val SuccessColor    = MindfulGreen40

// ── 热力图专用色（正念语义：中性信息展示，无"越多越好"暗示）──────────────
// 未超限：宁静蓝灰（中性，仅表达"有使用"）
val HeatmapNeutral  = Color(0xFF6B8CAE)   // 蓝灰，未超限使用时长
// 超限：暖橙/琥珀（警觉感，提示注意）
val HeatmapWarn     = Color(0xFFD4956A)   // 暖橙陶土色，超出每日限额

// ════════════════════════════════════════════════════════════════════════════
//  夜间主题（Night Theme）
//  基调：深蓝灰 —— 现代胶囊风格，沉静舒适，无暗绿压抑感
// ════════════════════════════════════════════════════════════════════════════

// 背景
val NightBg          = Color(0xFF111318)   // 全局背景：深蓝灰（柔和，不刺眼）
val NightBgVariant   = Color(0xFF191C25)   // 背景层次变体

// 卡片（胶囊/卡片感核心颜色）
val NightCardBg       = Color(0xFF1E2130)   // 主卡片：深蓝灰（参考图胶囊背景色）
val NightCardElevated = Color(0xFF252840)   // 略微突起卡片
val NightCardGreen    = Color(0xFF1A2038)   // 高亮卡片（纯蓝灰调，无绿色压抑感）
val NightCardMonitor  = Color(0xFF1C2340)   // 已监控卡片（蓝紫调，舒适区分）

// 顶部 Header 卡片（深蓝紫系，现代感）
val NightHeaderBg     = Color(0xFF1A2035)   // 深蓝 Header
val NightHeaderBgDark = Color(0xFF121828)   // Header 阴影版

// 文字
val NightTextPrimary   = Color(0xFFF0F4F8)   // 主文字：纯净近白（高对比度）
val NightTextSecondary = Color(0xFF8E99B0)   // 次文字：蓝灰色
val NightTextHint      = Color(0xFF4A5468)   // 提示文字：深蓝灰
val NightTextGreen     = LogoGreen           // 绿色标签文字

// 分割线 / 边框
val NightDivider      = Color(0xFF1E2538)   // 细分割线
val NightBorder       = Color(0xFF2A3347)   // 边框（更蓝）

// Dock/导航栏
val NightDockBg       = Color(0xFF111318)   // 与背景一致，无断层

// ════════════════════════════════════════════════════════════════════════════
//  日间主题（Day Theme）
//  基调：白/浅灰底 + 清新翠绿，清爽明亮
// ════════════════════════════════════════════════════════════════════════════

// 背景
val DayBg             = Color(0xFFF5F7FA)   // 全局背景：极淡蓝灰（更清爽）
val DayBgVariant      = Color(0xFFECF0F5)   // 背景层次变体

// 卡片
val DayCardBg         = Color(0xFFFFFFFF)   // 主内容卡片：纯白
val DayCardElevated   = Color(0xFFF0F4F8)   // 略微沉底卡片（区分层次）
val DayCardGreen      = Color(0xFFE8F5EE)   // 带绿调白色卡片（已监控、高亮卡片）
val DayCardMonitor    = Color(0xFFDFF2E8)   // 已监控卡片

// 顶部 Header 卡片（翠绿系）
val DayHeaderBg       = Color(0xFF27AE60)   // 翠绿 Header
val DayHeaderBgDark   = Color(0xFF219A52)   // Header 渐变深色

// 文字
val DayTextPrimary    = Color(0xFF1A1D2E)   // 主文字：近黑蓝
val DayTextSecondary  = Color(0xFF5C6B7E)   // 次文字：蓝灰色
val DayTextHint       = Color(0xFF9EA8B5)   // 提示文字：浅蓝灰
val DayTextGreen      = Color(0xFF1E8B4E)   // 绿色标签文字（日间稍深）

// 分割线 / 边框
val DayDivider        = Color(0xFFE2E8F0)   // 细分割线
val DayBorder         = Color(0xFFCDD5E0)   // 边框

// Dock/导航栏
val DayDockBg         = Color(0xFFFFFFFF)   // 白色底

// ════════════════════════════════════════════════════════════════════════════
//  旧版兼容别名（保持其他文件不报错，默认指向夜间主题值）
// ════════════════════════════════════════════════════════════════════════════
val AppBg           = NightBg
val AppBgVariant    = NightBgVariant
val CardBg          = NightCardBg
val CardBgElevated  = NightCardElevated
val CardBgGreen     = NightCardGreen   // 已改为蓝灰调，无暗绿
val CardBgMonitored = NightCardMonitor // 已改为蓝紫调，无暗绿
val GreenCardBg     = NightHeaderBg
val GreenCardBgDark = NightHeaderBgDark
val GreenCardAccent = Color(0xFF2ECC71)
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
val MindfulGreenGrey80 = Color(0xFF7AC9A8)
val MindfulTeal80   = Color(0xFF5EC8C0)
val MindfulGreen20  = Color(0xFF0A1E35)   // 已改为深蓝调
