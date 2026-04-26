package io.goodmidnight.transfer.core.connection.hotspot

sealed interface HotSpotEvent {

    /**
     * Requests the creation of a temporary Local Only Hotspot.
     */
    data object StartHotspot : HotSpotEvent

    /**
     *  Requests the shutdown of the active Local Only Hotspot.
     */
    data object StopHotspot : HotSpotEvent
}