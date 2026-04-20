package com.callanalyzer.app.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.callanalyzer.app.R
import com.callanalyzer.app.data.entity.CallLogEntity
import com.callanalyzer.app.utils.DurationFormatter
import java.text.SimpleDateFormat
import java.util.*

class CallLogAdapter : ListAdapter<CallLogEntity, CallLogAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val dateFmt = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_name)
        val tvNumber: TextView = view.findViewById(R.id.tv_number)
        val tvType: TextView = view.findViewById(R.id.tv_type)
        val tvDate: TextView = view.findViewById(R.id.tv_date)
        val tvDuration: TextView = view.findViewById(R.id.tv_duration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val ctx = holder.itemView.context

        // 名称：优先显示联系人姓名
        if (!item.name.isNullOrBlank()) {
            holder.tvName.text = item.name
            holder.tvNumber.text = item.number
            holder.tvNumber.visibility = View.VISIBLE
        } else {
            holder.tvName.text = item.number
            holder.tvNumber.visibility = View.GONE
        }

        holder.tvDate.text = dateFmt.format(Date(item.date))
        holder.tvDuration.text = if (item.callType == CallLogEntity.TYPE_MISSED) {
            "未接"
        } else {
            DurationFormatter.format(item.duration)
        }

        // 通话类型标签颜色
        holder.tvType.text = CallLogEntity.typeLabel(item.callType)
        val colorRes = when (item.callType) {
            CallLogEntity.TYPE_OUTGOING -> R.color.type_outgoing
            CallLogEntity.TYPE_INCOMING -> R.color.type_incoming
            CallLogEntity.TYPE_MISSED -> R.color.type_missed
            else -> R.color.type_incoming
        }
        holder.tvType.setTextColor(ContextCompat.getColor(ctx, colorRes))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CallLogEntity>() {
            override fun areItemsTheSame(o: CallLogEntity, n: CallLogEntity) = o.id == n.id
            override fun areContentsTheSame(o: CallLogEntity, n: CallLogEntity) = o == n
        }
    }
}
