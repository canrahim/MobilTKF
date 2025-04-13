package com.asforce.asforcetkf2.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.asforce.asforcetkf2.R
import com.asforce.asforcetkf2.databinding.ItemTabBinding
import com.asforce.asforcetkf2.model.Tab
import java.util.Collections

/**
 * RecyclerView adapter for displaying browser tabs in the tab strip
 */
class TabsAdapter(
    private val onTabSelected: (Tab) -> Unit,
    private val onTabClosed: (Tab) -> Unit,
    private val onStartDrag: (TabsAdapter.TabViewHolder) -> Unit
) : RecyclerView.Adapter<TabsAdapter.TabViewHolder>() {
    
    private var tabs: MutableList<Tab> = mutableListOf()
    
    fun interface OnTabDragListener {
        fun onTabMoved(fromPosition: Int, toPosition: Int)
    }
    
    var onTabDragListener: OnTabDragListener? = null
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val binding = ItemTabBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TabViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        holder.bind(tab)
    }
    
    override fun getItemCount(): Int = tabs.size
    
    @SuppressLint("NotifyDataSetChanged")
    fun updateTabs(newTabs: List<Tab>) {
        tabs.clear()
        tabs.addAll(newTabs)
        notifyDataSetChanged()
    }
    
    fun moveTab(fromPosition: Int, toPosition: Int) {
        // Verify positions are valid
        if (fromPosition < 0 || fromPosition >= tabs.size ||
            toPosition < 0 || toPosition >= tabs.size) {
            return
        }
        
        // Swap tabs
        Collections.swap(tabs, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        
        // Notify listener
        onTabDragListener?.onTabMoved(fromPosition, toPosition)
    }
    
    fun getTabs(): List<Tab> = tabs.toList()
    
    inner class TabViewHolder(
        private val binding: ItemTabBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        @SuppressLint("ClickableViewAccessibility")
        fun bind(tab: Tab) {
            with(binding) {
                // Set tab title
                tabTitle.text = tab.displayTitle
                
                // Set favicon if available
                if (tab.favicon != null) {
                    tabFavicon.setImageBitmap(tab.favicon)
                    tabFavicon.visibility = View.VISIBLE
                } else {
                    tabFavicon.setImageResource(R.drawable.ic_globe)
                    tabFavicon.visibility = View.VISIBLE
                }
                
                // Highlight active tab
                if (tab.isActive) {
                    tabContainer.setBackgroundResource(R.drawable.bg_tab_active)
                    tabTitle.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.tab_title_active)
                    )
                } else {
                    tabContainer.setBackgroundResource(R.drawable.bg_tab_inactive)
                    tabTitle.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.tab_title_inactive)
                    )
                }
                
                // Show hibernation indicator if needed
                if (tab.isHibernated) {
                    hibernationIndicator.visibility = View.VISIBLE
                } else {
                    hibernationIndicator.visibility = View.GONE
                }
                
                // Loading indicator
                if (tab.isLoading) {
                    loadingIndicator.visibility = View.VISIBLE
                } else {
                    loadingIndicator.visibility = View.GONE
                }
                
                // Set up click listener to select tab
                root.setOnClickListener {
                    onTabSelected(tab)
                }
                
                // Set up close button
                closeButton.setOnClickListener {
                    onTabClosed(tab)
                }
                
                // Enable tab dragging by setting touch listener on the whole tab
                tabContainer.setOnLongClickListener {
                    onStartDrag(this@TabViewHolder)
                    true
                }
            }
        }
    }
}