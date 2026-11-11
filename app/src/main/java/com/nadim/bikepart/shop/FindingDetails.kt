package com.nadim.bikepart.shop

data class MotorcycleMake(val id: String, val name: String, val retired: Boolean = false)
data class MotorcycleModel(val id: String, val makeId: String, val name: String, val retired: Boolean = false)
data class StorageLocation(val id: String, val name: String, val retired: Boolean = false)

// An empty association set means unknown; assigned fitment always has at least one model.
internal fun lookupDisplay(name: String): String = LookupNames.display(name).also {
    require(it.isNotEmpty()) { "Enter a lookup name" }
}
