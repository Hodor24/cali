package dev.tabml.box

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import dev.tabml.box.databinding.ItemChatAssistantBinding
import dev.tabml.box.databinding.ItemChatUserBinding

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ChatMessage>()

    var onUserRowLongPress: ((Int, ChatMessage) -> Unit)? = null
    var onAssistantRowLongPress: ((Int, ChatMessage) -> Unit)? = null

    fun submitAll(messages: List<ChatMessage>) {
        items.clear()
        items.addAll(messages)
        notifyDataSetChanged()
    }

    fun append(message: ChatMessage) {
        items.add(message)
        notifyItemInserted(items.lastIndex)
    }

    fun replaceLast(message: ChatMessage) {
        require(items.isNotEmpty()) { "no items" }
        val i = items.lastIndex
        items[i] = message
        notifyItemChanged(i)
    }

    fun removeLast() {
        if (items.isEmpty()) return
        val i = items.lastIndex
        items.removeAt(i)
        notifyItemRemoved(i)
    }

    fun removeAt(position: Int) {
        if (position !in items.indices) return
        items.removeAt(position)
        notifyItemRemoved(position)
        if (position < items.size) {
            notifyItemRangeChanged(position, items.size - position)
        }
    }

    fun replaceAt(position: Int, message: ChatMessage) {
        if (position !in items.indices) return
        items[position] = message
        notifyItemChanged(position)
    }

    fun snapshot(): List<ChatMessage> = items.toList()

    override fun getItemViewType(position: Int): Int =
        when (items[position].speaker) {
            ChatSpeaker.User -> VT_USER
            ChatSpeaker.Assistant -> VT_ASSISTANT
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VT_USER -> UserVH(ItemChatUserBinding.inflate(inflater, parent, false))
            else -> AssistantVH(ItemChatAssistantBinding.inflate(inflater, parent, false))
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = items[position]
        val ctx = holder.itemView.context
        when (holder) {
            is UserVH -> holder.bind(position, msg, onUserRowLongPress, ctx)
            is AssistantVH -> holder.bind(position, msg, onAssistantRowLongPress, ctx)
        }
    }

    class UserVH(private val binding: ItemChatUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(position: Int, msg: ChatMessage, listener: ((Int, ChatMessage) -> Unit)?, ctx: Context) {
            binding.chatText.text = msg.text
            val mult = if (Prefs.caliLargeChatText(ctx)) 1.2f else 1f
            binding.chatText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * mult)
            if (Prefs.caliHighContrastChat(ctx)) {
                binding.root.setCardBackgroundColor(ctx.getColor(R.color.cali_chat_user_hi_bg))
                binding.chatText.setTextColor(ctx.getColor(R.color.cali_chat_user_hi_fg))
            } else {
                val bg = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimaryContainer)
                val fg = MaterialColors.getColor(binding.chatText, com.google.android.material.R.attr.colorOnPrimaryContainer)
                binding.root.setCardBackgroundColor(bg)
                binding.chatText.setTextColor(fg)
            }
            binding.root.contentDescription = ctx.getString(R.string.cd_chat_user_row, summarizeForA11y(msg.text))
            binding.root.setOnLongClickListener(
                if (listener == null) null
                else View.OnLongClickListener { listener(position, msg); true },
            )
        }
    }

    class AssistantVH(private val binding: ItemChatAssistantBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(position: Int, msg: ChatMessage, listener: ((Int, ChatMessage) -> Unit)?, ctx: Context) {
            binding.chatText.text = msg.text
            val mult = if (Prefs.caliLargeChatText(ctx)) 1.2f else 1f
            binding.chatText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * mult)
            if (Prefs.caliHighContrastChat(ctx)) {
                binding.chatBubbleCard.setCardBackgroundColor(ctx.getColor(R.color.cali_chat_assistant_hi_bg))
                binding.chatText.setTextColor(ctx.getColor(R.color.cali_chat_assistant_hi_fg))
            } else {
                val bg = MaterialColors.getColor(binding.chatBubbleCard, com.google.android.material.R.attr.colorSurfaceVariant)
                val fg = MaterialColors.getColor(binding.chatText, com.google.android.material.R.attr.colorOnSurfaceVariant)
                binding.chatBubbleCard.setCardBackgroundColor(bg)
                binding.chatText.setTextColor(fg)
            }
            binding.root.contentDescription = ctx.getString(R.string.cd_chat_assistant_row, summarizeForA11y(msg.text))
            binding.root.setOnLongClickListener(
                if (listener == null) null
                else View.OnLongClickListener { listener(position, msg); true },
            )
        }
    }

    companion object {
        private const val VT_USER = 0
        private const val VT_ASSISTANT = 1

        fun summarizeForA11y(text: String): String =
            text.replace("\n", " ").trim().take(120)
    }
}
