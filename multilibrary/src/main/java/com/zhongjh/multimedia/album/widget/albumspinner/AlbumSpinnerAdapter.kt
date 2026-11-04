package com.zhongjh.multimedia.album.widget.albumspinner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.imageview.ShapeableImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.zhongjh.multimedia.R
import com.zhongjh.multimedia.album.entity.Album
import com.zhongjh.multimedia.album.entity.AlbumSpinnerStyle
import com.zhongjh.multimedia.settings.GlobalSpec.imageEngine

/**
 * 专辑下拉选项框的适配器
 *
 * @author zhongjh
 */
class AlbumSpinnerAdapter(private val albumSpinnerStyle: AlbumSpinnerStyle) : RecyclerView.Adapter<AlbumSpinnerAdapter.ViewHolder>() {
    var albums: List<Album> = ArrayList()
        private set

    fun bindAlbums(albums: List<Album>) {
        // 计算新老数据集差异，将差异更新到Adapter
        val diffResult = DiffUtil.calculateDiff(AlbumCallback(this.albums, albums))
        this.albums = albums
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_album_zjh, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val album = albums[position]
        holder.tvSign.visibility = if (album.checkedCount > 0) View.VISIBLE else View.INVISIBLE
        holder.itemView.isSelected = album.isChecked
        imageEngine.loadThumbnail(
            holder.itemView.context.applicationContext, holder.itemView.context.resources.getDimensionPixelSize(R.dimen.z_media_grid_size),
            albumSpinnerStyle.placeholder, holder.imgFirst, album.firstImagePath ?: ""
        )
        holder.tvName.text = album.name
        holder.tvCount.text = album.count.toString()
        holder.itemView.setOnClickListener {
            onAlbumItemClickListener?.let {
                val size = albums.size
                for (i in 0 until size) {
                    val item = albums[i]
                    item.isChecked = false
                }
                album.isChecked = true
                notifyItemRangeChanged(0, albums.size)
                it.onItemClick(position, album)
            }
        }
    }

    override fun getItemCount(): Int {
        return albums.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgFirst: ShapeableImageView = itemView.findViewById(R.id.imgFirst)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvSign: TextView = itemView.findViewById(R.id.tvSign)
        val tvCount: TextView = itemView.findViewById(R.id.tvCount)
    }

    private var onAlbumItemClickListener: OnAlbumItemClickListener? = null

    fun setOnAlbumItemClickListener(listener: OnAlbumItemClickListener?) {
        this.onAlbumItemClickListener = listener
    }
}
