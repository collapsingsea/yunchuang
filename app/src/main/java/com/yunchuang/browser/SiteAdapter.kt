package com.yunchuang.browser

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SiteAdapter(
    private val onClick: (Site) -> Unit,
    private val onLongClick: (Site) -> Unit,
    private val onIconDrag: (VH) -> Unit
) : RecyclerView.Adapter<SiteAdapter.VH>() {

    private val items = mutableListOf<Site>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Site>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun snapshot(): List<Site> = items.toList()

    fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_site, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tile: TextView = view.findViewById(R.id.tile)
        private val txtName: TextView = view.findViewById(R.id.txtName)
        private val txtHost: TextView = view.findViewById(R.id.txtHost)
        private val icMode: ImageView = view.findViewById(R.id.icMode)
        private val txtMode: TextView = view.findViewById(R.id.txtMode)

        fun bind(site: Site) {
            itemView.tag = site.id
            Ui.bindTile(tile, site, 15f)
            txtName.text = site.name.ifBlank { site.host() }
            txtHost.text = site.host()
            txtMode.text = site.modeLabel(itemView.context)
            icMode.setImageResource(
                if (site.uaMode == Site.UA_MOBILE) R.drawable.ic_smartphone else R.drawable.ic_desktop
            )
            itemView.setOnClickListener { onClick(site) }
            itemView.setOnLongClickListener {
                onLongClick(site)
                true
            }
            tile.contentDescription = itemView.context.getString(R.string.drag_reorder)
            tile.setOnClickListener { onClick(site) }
            tile.setOnLongClickListener {
                onIconDrag(this)
                true
            }
        }
    }
}
