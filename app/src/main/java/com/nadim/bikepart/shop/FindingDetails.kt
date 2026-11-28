package com.nadim.bikepart.shop

import com.nadim.bikepart.R

data class MotorcycleMake(val id: String, val name: String, val retired: Boolean = false)
data class MotorcycleModel(val id: String, val makeId: String, val name: String, val retired: Boolean = false)
data class StorageLocation(val id: String, val name: String, val retired: Boolean = false)

// An empty association set means unknown; assigned fitment always has at least one model.
internal fun lookupDisplay(context: android.content.Context, name: String): String = LookupNames.display(name).also {
    require(it.isNotEmpty()) { context.getString(R.string.enter_a_lookup_name) }
}
