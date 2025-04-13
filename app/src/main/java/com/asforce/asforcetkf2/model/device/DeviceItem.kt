package com.asforce.asforcetkf2.model.device

/**
 * Cihaz listesinde gösterilen her bir cihaz öğesi
 */
data class DeviceItem(
    val id: String,
    val name: String,
    var isSelected: Boolean = false,
    var isFavorite: Boolean = false
)