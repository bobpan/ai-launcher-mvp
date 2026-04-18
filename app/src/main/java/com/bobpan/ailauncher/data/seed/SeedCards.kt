package com.bobpan.ailauncher.data.seed

import com.bobpan.ailauncher.data.db.entity.CachedCardEntity
import com.bobpan.ailauncher.data.model.Card
import com.bobpan.ailauncher.data.model.CardType
import com.bobpan.ailauncher.data.model.Intent
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 16 seed cards, 4 per intent, verbatim from PRD-v2 §9.
 * `id`, `intent`, `type`, and `tags` are load-bearing; seedOrder fixes the cold-start sort.
 * Declaration order per intent: _01 CONTINUE, _02/_03 DISCOVER, _04 NEW (FR-11 cold-start).
 */
val SEED_CARDS: List<Card> = listOf(
    // COMMUTE
    Card(
        id = "commute_01", intent = Intent.COMMUTE, type = CardType.CONTINUE,
        icon = "🎧", title = "继续听《三体》",
        description = "上次停在第 14 章，还剩 23 分钟",
        actionLabel = "继续播放", whyLabel = "示例：你在听的有声书",
        tags = listOf("audiobook", "scifi", "commute", "morning"),
        seedOrder = 0
    ),
    Card(
        id = "commute_02", intent = Intent.COMMUTE, type = CardType.DISCOVER,
        icon = "🚇", title = "常用通勤路线",
        description = "示例路线预计 18 分钟",
        actionLabel = "查看路线", whyLabel = "示例：你的常用路线",
        tags = listOf("transit", "subway", "commute", "morning"),
        seedOrder = 1
    ),
    Card(
        id = "commute_03", intent = Intent.COMMUTE, type = CardType.DISCOVER,
        icon = "📰", title = "新闻早报 · 3 分钟",
        description = "科技、财经、本地头条速览",
        actionLabel = "开始播报", whyLabel = "示例：早间高频选择",
        tags = listOf("news", "briefing", "commute", "morning"),
        seedOrder = 2
    ),
    Card(
        id = "commute_04", intent = Intent.COMMUTE, type = CardType.NEW,
        icon = "🎙️", title = "试听《硅谷早知道》",
        description = "科技播客，看看你是否感兴趣",
        actionLabel = "试听", whyLabel = "ε-greedy 探索 · 匹配科技偏好",
        tags = listOf("podcast", "tech", "commute", "explore"),
        seedOrder = 3
    ),

    // WORK
    Card(
        id = "work_01", intent = Intent.WORK, type = CardType.CONTINUE,
        icon = "🎨", title = "继续 Figma 稿件",
        description = "示例文件 \"Launcher v2 - Home\"",
        actionLabel = "打开 Figma", whyLabel = "示例：最近编辑的设计稿",
        tags = listOf("design", "figma", "work", "focus"),
        seedOrder = 4
    ),
    Card(
        id = "work_02", intent = Intent.WORK, type = CardType.DISCOVER,
        icon = "🎯", title = "开启专注模式 45 min",
        description = "屏蔽通知、白噪音、番茄钟",
        actionLabel = "开始专注", whyLabel = "示例：工作日常用",
        tags = listOf("focus", "pomodoro", "work", "afternoon"),
        seedOrder = 5
    ),
    Card(
        id = "work_03", intent = Intent.WORK, type = CardType.DISCOVER,
        icon = "📅", title = "今日日程概览",
        description = "示例：3 个会议、2 个待办",
        actionLabel = "查看日程", whyLabel = "工作模式常看",
        tags = listOf("calendar", "meeting", "work"),
        seedOrder = 6
    ),
    Card(
        id = "work_04", intent = Intent.WORK, type = CardType.NEW,
        icon = "📋", title = "试试 Linear 建单",
        description = "轻量级项目管理，给你看看",
        actionLabel = "了解一下", whyLabel = "ε-greedy 探索 · 项目管理类",
        tags = listOf("productivity", "pm", "work", "explore"),
        seedOrder = 7
    ),

    // LUNCH
    Card(
        id = "lunch_01", intent = Intent.LUNCH, type = CardType.CONTINUE,
        icon = "☕", title = "瑞幸生椰拿铁",
        description = "15 元券可用",
        actionLabel = "一键下单", whyLabel = "示例：高频选择",
        tags = listOf("coffee", "luckin", "lunch", "habit"),
        seedOrder = 8
    ),
    Card(
        id = "lunch_02", intent = Intent.LUNCH, type = CardType.DISCOVER,
        icon = "🍲", title = "附近餐厅 · 20% off",
        description = "步行约 4 分钟，人均 58 元",
        actionLabel = "查看菜单", whyLabel = "午餐时间 · 附近优惠",
        tags = listOf("restaurant", "lunch", "nearby", "deal"),
        seedOrder = 9
    ),
    Card(
        id = "lunch_03", intent = Intent.LUNCH, type = CardType.DISCOVER,
        icon = "🛵", title = "叫个外卖",
        description = "示例：常点的 3 家都在营业",
        actionLabel = "打开美团", whyLabel = "午餐时间高频选择",
        tags = listOf("delivery", "meituan", "lunch"),
        seedOrder = 10
    ),
    Card(
        id = "lunch_04", intent = Intent.LUNCH, type = CardType.NEW,
        icon = "🥗", title = "今日轻食套餐",
        description = "鸡胸肉 + 藜麦碗",
        actionLabel = "看看", whyLabel = "ε-greedy 探索 · 健康选项",
        tags = listOf("salad", "healthy", "lunch", "explore"),
        seedOrder = 11
    ),

    // REST
    Card(
        id = "rest_01", intent = Intent.REST, type = CardType.CONTINUE,
        icon = "📺", title = "继续《漫长的季节》E08",
        description = "上次停在 18:23，剩 42 分钟",
        actionLabel = "继续看", whyLabel = "示例：最近在追的剧",
        tags = listOf("tv", "drama", "rest", "evening"),
        seedOrder = 12
    ),
    Card(
        id = "rest_02", intent = Intent.REST, type = CardType.DISCOVER,
        icon = "🎵", title = "放首《夜的第七章》",
        description = "周杰伦",
        actionLabel = "播放", whyLabel = "示例：休息时常听",
        tags = listOf("music", "jay", "rest"),
        seedOrder = 13
    ),
    Card(
        id = "rest_03", intent = Intent.REST, type = CardType.DISCOVER,
        icon = "🧘", title = "10 分钟身体扫描冥想",
        description = "睡前放松",
        actionLabel = "开始", whyLabel = "示例：冥想类高频选择",
        tags = listOf("meditation", "wellness", "rest", "evening"),
        seedOrder = 14
    ),
    Card(
        id = "rest_04", intent = Intent.REST, type = CardType.NEW,
        icon = "🎮", title = "试试《塞尔达》王国之泪",
        description = "开放世界解谜游戏",
        actionLabel = "了解", whyLabel = "ε-greedy 探索 · 解谜偏好",
        tags = listOf("game", "zelda", "rest", "explore"),
        seedOrder = 15
    )
)

/** Maps a domain Card → Room entity. */
fun Card.toEntity(json: Json): CachedCardEntity {
    val serializer = ListSerializer(String.serializer())
    return CachedCardEntity(
        id            = id,
        title         = title,
        description   = description,
        intent        = intent.name,
        type          = type.name,
        icon          = icon,
        actionLabel   = actionLabel,
        whyLabel      = whyLabel,
        tagsJson      = json.encodeToString(serializer, tags),
        seedOrder     = seedOrder
    )
}

/** Maps a Room entity → domain Card. */
fun CachedCardEntity.toDomain(json: Json): Card {
    val serializer = ListSerializer(String.serializer())
    return Card(
        id           = id,
        intent       = Intent.valueOf(intent),
        type         = CardType.valueOf(type),
        icon         = icon,
        title        = title,
        description  = description,
        actionLabel  = actionLabel,
        whyLabel     = whyLabel,
        tags         = json.decodeFromString(serializer, tagsJson),
        seedOrder    = seedOrder
    )
}
