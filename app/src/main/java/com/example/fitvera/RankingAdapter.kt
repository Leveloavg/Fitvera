package com.example.fitvera

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView

class RankingAdapter(private var rankingList: List<RankingEntry>) :
    RecyclerView.Adapter<RankingAdapter.RankingViewHolder>() {

    private var currentMetric: String = "kilometers_week"

    inner class RankingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRank: TextView = itemView.findViewById(R.id.tv_position)
        val ivProfileImage: CircleImageView = itemView.findViewById(R.id.iv_profile_image)
        val tvName: TextView = itemView.findViewById(R.id.tv_user_name)
        val tvMetricValue: TextView = itemView.findViewById(R.id.tv_metric_value)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RankingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ranking_user, parent, false)
        return RankingViewHolder(view)
    }

    override fun onBindViewHolder(holder: RankingViewHolder, position: Int) {
        val entry = rankingList[position]
        holder.tvRank.text = (position + 1).toString()

        if (!entry.photoUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(entry.photoUrl)
                .placeholder(R.drawable.profile_placeholder)
                .error(R.drawable.profile_placeholder)
                .into(holder.ivProfileImage)
        } else {
            holder.ivProfileImage.setImageResource(R.drawable.profile_placeholder)
        }

        holder.tvName.text = entry.name
        holder.tvMetricValue.text = getFormattedMetricValue(entry)
    }

    override fun getItemCount(): Int = rankingList.size

    fun updateList(newList: List<RankingEntry>) {
        rankingList = newList
        notifyDataSetChanged()
    }

    fun setMetric(metric: String) {
        this.currentMetric = metric
        notifyDataSetChanged()
    }

    private fun getFormattedMetricValue(entry: RankingEntry): String {
        return when {
            // 🚩 NUEVO CASO: TERRITORIOS
            currentMetric == "territories_total" -> {
                "${entry.territories_total} Zonas"
            }

            currentMetric.startsWith("kilometers") -> {
                val kmValue = when (currentMetric) {
                    "kilometers_week" -> entry.kilometers_week
                    "kilometers_month" -> entry.kilometers_month
                    "kilometers_year" -> entry.kilometers_year
                    else -> entry.kilometers_total
                }
                "%.1f km".format(kmValue)
            }

            currentMetric.startsWith("activities") -> {
                val activities = when (currentMetric) {
                    "activities_week" -> entry.activities_week
                    "activities_month" -> entry.activities_month
                    "activities_year" -> entry.activities_year
                    else -> entry.activities_total
                }
                "$activities Act."
            }

            currentMetric.startsWith("pace") -> {
                val paceSeconds = when (currentMetric) {
                    "pace_week" -> entry.pace_week
                    "pace_month" -> entry.pace_month
                    "pace_year" -> entry.pace_year
                    else -> entry.pace_total
                }
                formatPace(paceSeconds)
            }
            else -> "N/A"
        }
    }

    private fun formatPace(paceSeconds: Long): String {
        if (paceSeconds == 0L) return "0:00"
        val mins = paceSeconds / 60
        val secs = paceSeconds % 60
        return "%d:%02d".format(mins, secs)
    }
}