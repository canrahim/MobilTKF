package com.asforce.asforcetkf2.ui.termal.kotlin.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.asforce.asforcetkf2.R
import com.asforce.asforcetkf2.ui.termal.kotlin.model.Person
import java.util.ArrayList

class PersonAdapter(private val listener: OnItemClickListener) : RecyclerView.Adapter<PersonAdapter.PersonViewHolder>() {

    private val items = ArrayList<Person>()
    private val fullList = ArrayList<Person>()

    interface OnItemClickListener {
        fun onItemClick(person: Person, position: Int)
        fun onDeleteClick(position: Int)
        fun onItemLongClick(person: Person, position: Int)
    }

    inner class PersonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvValue1: TextView = itemView.findViewById(R.id.tvValue1)
        val tvValue2: TextView = itemView.findViewById(R.id.tvValue2)
        val tvValue3: TextView = itemView.findViewById(R.id.tvValue3)
        val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        val cardView: CardView = itemView.findViewById(R.id.cardView)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(items[position], position)
                }
            }

            itemView.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onItemLongClick(items[position], position)
                    return@setOnLongClickListener true
                }
                false
            }

            deleteButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onDeleteClick(position)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_person, parent, false)
        return PersonViewHolder(view)
    }

    override fun onBindViewHolder(holder: PersonViewHolder, position: Int) {
        val person = items[position]
        holder.tvValue1.text = person.value1
        holder.tvValue2.text = person.value2
        holder.tvValue3.text = person.value3

        // Apply appropriate background color based on selection state
        val context = holder.itemView.context
        when {
            person.isSelected -> {
                holder.cardView.setCardBackgroundColor(context.getColor(R.color.selected_item_bg))
                holder.tvValue1.setTextColor(context.getColor(R.color.text_primary))
                holder.tvValue2.setTextColor(context.getColor(R.color.text_secondary))
                holder.tvValue3.setTextColor(context.getColor(R.color.text_tertiary))
            }
            person.isHighlighted -> {
                holder.cardView.setCardBackgroundColor(context.getColor(R.color.highlighted_item_bg))
                holder.tvValue1.setTextColor(context.getColor(R.color.text_primary))
                holder.tvValue2.setTextColor(context.getColor(R.color.text_secondary))
                holder.tvValue3.setTextColor(context.getColor(R.color.text_tertiary))
            }
            else -> {
                holder.cardView.setCardBackgroundColor(context.getColor(R.color.card_background))
                holder.tvValue1.setTextColor(context.getColor(R.color.text_primary))
                holder.tvValue2.setTextColor(context.getColor(R.color.text_secondary))
                holder.tvValue3.setTextColor(context.getColor(R.color.text_tertiary))
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun getItem(position: Int): Person = items[position]

    fun clearItems() {
        items.clear()
        fullList.clear()
        notifyDataSetChanged()
    }

    fun addItems(newItems: List<Person>) {
        items.addAll(newItems)
        fullList.addAll(newItems)
        notifyDataSetChanged()
    }

    fun updateList(filteredList: List<Person>) {
        items.clear()
        items.addAll(filteredList)
        notifyDataSetChanged()
    }

    fun getFullList(): List<Person> = fullList

    fun getSelectedIds(): List<String> {
        val selectedIds = ArrayList<String>()
        for (person in items) {
            if (person.isSelected) {
                selectedIds.add(person.id)
            }
        }
        return selectedIds
    }

    fun areAllItemsSelected(): Boolean {
        if (items.isEmpty()) return false
        
        for (person in items) {
            if (!person.isSelected) return false
        }
        return true
    }

    fun setAllItemsSelected(selected: Boolean) {
        for (person in items) {
            person.isSelected = selected
        }
    }
} 