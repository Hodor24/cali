package dev.tabml.box

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.tabml.box.databinding.ItemChatAssistantBinding
import dev.tabml.box.databinding.ItemChatUserBinding

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ChatMessage>()

    fun submitAll(messages: List<ChatMessage>) {
        items.clear()
        items.addAll(messages)
        notifyDataSetChanged()
    }

    fun append(message: ChatMessage) {
        items.add(message)
        notifyItemInserted(items.lastIndex)
    }

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
        when (holder) {
            is UserVH -> holder.bind(msg.text)
            is AssistantVH -> holder.bind(msg.text)
        }
    }

    class UserVH(private val binding: ItemChatUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(text: String) {
            binding.chatText.text = text
        }
    }

    class AssistantVH(
        private val binding: ItemChatAssistantBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(text: String) {
            binding.chatText.text = text
        }
    }

    companion object {
        private const val VT_USER = 0
        private const val VT_ASSISTANT = 1
    }
}
