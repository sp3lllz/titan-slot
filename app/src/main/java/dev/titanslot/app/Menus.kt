package dev.titanslot.app

import android.os.SystemClock

data class Toast(val text: String, val at: Long = SystemClock.uptimeMillis())

/** The quick menu on the shelf (MENU), after slot's quick_menu.rs, plus Titan extras. */
enum class QuickRow(val label: String) {
    FAST_FORWARD("Fast Forward"),
    FAST_FORWARD_SOUND("Fast Forward Sound"),
    REWIND("Rewind"),
    SCALING("Scaling"),
    FILTER("Screen Filter"),
    COLOUR_CORRECTION("Colour Correction"),
    GB_PALETTE("Game Boy Palette"),
    CONTROLS("Controls"),
    SCRAPE_MISSING("Scrape Cart Art"),
    RESCAN("Rescan Games"),
    ABOUT("About");

    /** Rows that open something rather than cycle a value. */
    val opens: Boolean get() = this == CONTROLS || this == SCRAPE_MISSING || this == RESCAN || this == ABOUT
}

/** The in-game menu on a tap of MENU. */
enum class PauseRow(val label: String) {
    RESUME("Resume"),
    SAVE_STATE("Save State"),
    LOAD_STATE("Load Last State"),
    STATES("Save States"),
    RESET("Reset Game"),
    EJECT("Eject"),
}

/** The cart sheet (START on a cart): slot's Cart Studio, on the phone. */
enum class CartRow(val label: String) {
    NAME("Name"),
    SCRAPE("Scrape Art"),
    PICK("Use Image From Phone"),
    FIT("Art Fit"),
    REMOVE_ART("Remove Art"),
    COLOUR("Colour"),
    FINISH("Finish"),
    SHAPE("Outline"),
    CHIP("Chip"),
    RESET("Reset Cart");

    /** Rows whose value Left / Right cycles. */
    val cycles: Boolean get() = this in setOf(SCRAPE, FIT, COLOUR, FINISH, SHAPE, CHIP)
}
