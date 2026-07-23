package com.handcontrol.phone.gesture

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

        val ACTIONABLE = listOf(SWIPE_UP, SWIPE_DOWN, LIKE, FAVORITE, PAUSE, COMMENT, PROFILE)
    }
}
