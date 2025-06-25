// --- src/main/java/com/catto/cookietimer/TimerDiffCallback.kt ---
package com.catto.cookietimer

import androidx.recyclerview.widget.DiffUtil

// TimerDiffCallback: Used by DiffUtil to efficiently update RecyclerView items.
// This is crucial for performance and smooth animations when the underlying data changes.
class TimerDiffCallback(
    private val oldList: List<Timer>,
    private val newList: List<Timer>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    // Called by DiffUtil to decide whether two objects represent the same Item.
    // For our timers, the 'id' is a unique identifier.
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].id == newList[newItemPosition].id
    }

    // Called by DiffUtil when areItemsTheSame(int, int) returns true for two items.
    // This checks if the content (data) of the items has changed.
    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // Compare all relevant fields.
        // Data class's default equals() is sufficient if all fields are part of equals/hashCode
        // and @Ignore fields are not considered by Room anyway.
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}
