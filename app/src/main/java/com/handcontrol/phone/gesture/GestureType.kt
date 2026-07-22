package com.handcontrol.phone.gesture

/**
 * 手势类型枚举 — 基于 MediaPipe 21 个手部关键点识别
 */
enum class GestureType(
    val displayName: String,       // 显示名称
    val description: String,       // 手势描述
    val defaultAction: DouyinAction // 默认对应操作
) {
    OPEN_PALM(
        displayName = "张开手掌",
        description = "五指张开伸直",
        defaultAction = DouyinAction.PAUSE
    ),
    FIST(
        displayName = "握拳",
        description = "五指握紧成拳",
        defaultAction = DouyinAction.FAVORITE
    ),
    THUMB_UP(
        displayName = "点赞手势",
        description = "握拳，大拇指竖起向上",
        defaultAction = DouyinAction.LIKE
    ),
    INDEX_POINT(
        displayName = "食指指向",
        description = "仅食指伸出，其余握拳",
        defaultAction = DouyinAction.COMMENT
    ),
    PEACE_SIGN(
        displayName = "V字手势",
        description = "食指和中指伸出成V形",
        defaultAction = DouyinAction.PROFILE
    ),
    SWIPE_UP(
        displayName = "手向上挥",
        description = "手掌向上快速移动",
        defaultAction = DouyinAction.SWIPE_UP
    ),
    SWIPE_DOWN(
        displayName = "手向下挥",
        description = "手掌向下快速移动",
        defaultAction = DouyinAction.SWIPE_DOWN
    ),
    UNKNOWN(
        displayName = "未识别",
        description = "无法识别的手势",
        defaultAction = DouyinAction.NONE
    );

    companion object {
        /** 静态手势列表（不包含滑动） */
        val STATIC_GESTURES = listOf(OPEN_PALM, FIST, THUMB_UP, INDEX_POINT, PEACE_SIGN)

        /** 动态手势列表（滑动动作） */
        val DYNAMIC_GESTURES = listOf(SWIPE_UP, SWIPE_DOWN)
    }
}

/**
 * 抖音支持的操作类型
 */
enum class DouyinAction(val displayName: String, val code: Int) {
    NONE("无操作", -1),
    SWIPE_UP("上划", 0),
    SWIPE_DOWN("下划", 1),
    LIKE("点赞", 2),
    FAVORITE("收藏", 3),
    PAUSE("暂停/播放", 4),
    COMMENT("评论区", 5),
    PROFILE("作者主页", 6);

    companion object {
        fun fromCode(code: Int): DouyinAction =
            entries.firstOrNull { it.code == code } ?: NONE

        /** 所有可执行的操作（排除 NONE） */
        val ACTIONABLE = listOf(SWIPE_UP, SWIPE_DOWN, LIKE, FAVORITE, PAUSE, COMMENT, PROFILE)
    }
}
