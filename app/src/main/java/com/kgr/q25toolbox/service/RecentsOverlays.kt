package com.kgr.q25toolbox.service

/** The Recents overlay windows (vertical list / quilt, and grid); at most one is up at a time. */
object RecentsOverlays {
    fun isShowing(): Boolean =
        SlimRecentsOverlayController.isShowing() || GridRecentsOverlayController.isShowing()

    /** Closes whichever is showing. [animate] = false removes it at once (screen off, teardown). */
    fun hide(animate: Boolean = true) {
        if (SlimRecentsOverlayController.isShowing()) SlimRecentsOverlayController.hide(animate = animate, expandTaskId = null)
        if (GridRecentsOverlayController.isShowing()) GridRecentsOverlayController.hide(animate = animate)
    }
}
