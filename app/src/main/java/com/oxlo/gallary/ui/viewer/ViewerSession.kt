package com.oxlo.gallary.ui.viewer

import com.oxlo.gallary.data.db.MediaItemEntity

/**
 * Holds the list the viewer is paging through. Both the library and search set
 * this right before navigating, so the viewer always shows exactly the set of
 * items the user was looking at.
 */
class ViewerSession {

    var items: List<MediaItemEntity> = emptyList()
        private set

    var startIndex: Int = 0
        private set

    fun open(items: List<MediaItemEntity>, index: Int) {
        this.items = items
        this.startIndex = index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }

    fun clear() {
        items = emptyList()
        startIndex = 0
    }
}
