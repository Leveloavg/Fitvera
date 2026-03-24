package com.example.fitvera

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import de.hdodenhof.circleimageview.CircleImageView

class RequestAdapter(
    private val requests: MutableList<NotificacionSeguimiento>,
    private val onAction: (NotificacionSeguimiento, RequestAdapter.RequestAction) -> Unit
) : RecyclerView.Adapter<RequestAdapter.RequestViewHolder>() {

    enum class RequestAction {
        ACCEPT,
        CANCEL_OR_REJECT
    }

    class RequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // IDs actualizados para coincidir con el nuevo item_request.xml profesional
        val profileImage: CircleImageView = itemView.findViewById(R.id.img_user_notification)
        val nameTextView: TextView = itemView.findViewById(R.id.tv_user_name_notification)
        val statusTextView: TextView = itemView.findViewById(R.id.tv_notification_type)
        val acceptButton: Button = itemView.findViewById(R.id.btn_accept)
        val rejectButton: Button = itemView.findViewById(R.id.btn_reject)
        val unreadIndicator: View = itemView.findViewById(R.id.requestUnreadIndicator)
        val actionContainer: LinearLayout = itemView.findViewById(R.id.layout_buttons)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        // Inflamos el nuevo layout que evita los descuadres
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_request, parent, false)
        return RequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val request = requests[position]
        val context = holder.itemView.context

        holder.nameTextView.text = request.nombre

        if (!request.fotoUrl.isNullOrEmpty()) {
            Glide.with(context)
                .load(request.fotoUrl)
                .apply(RequestOptions.placeholderOf(R.drawable.ic_default_profile))
                .into(holder.profileImage)
        } else {
            holder.profileImage.setImageResource(R.drawable.ic_default_profile)
        }

        when (request.tipo) {
            TipoSolicitud.RECIBIDA -> {
                holder.statusTextView.text = "Quiere seguirte"
                holder.actionContainer.visibility = View.VISIBLE
                holder.acceptButton.setOnClickListener { onAction(request, RequestAction.ACCEPT) }
                holder.rejectButton.setOnClickListener { onAction(request, RequestAction.CANCEL_OR_REJECT) }
            }
            TipoSolicitud.ENVIADA -> {
                holder.statusTextView.text = "Solicitud enviada"
                holder.actionContainer.visibility = View.GONE
            }
        }

        if (!request.esLeida) {
            holder.unreadIndicator.visibility = View.VISIBLE
            // El color azul ahora se verá como un punto pequeño y estético
        } else {
            holder.unreadIndicator.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            if (!request.esLeida) {
                request.esLeida = true
                notifyItemChanged(position)
            }
        }
    }

    override fun getItemCount() = requests.size
}