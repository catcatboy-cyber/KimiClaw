package com.kimiclaw.pet;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 消息列表弹窗的 RecyclerView Adapter
 * 支持多App、多联系人消息展示
 */
public class MessagePopupAdapter extends RecyclerView.Adapter<MessagePopupAdapter.ViewHolder> {

    private final List<MessageItem> messageList;
    private final OnMessageActionListener actionListener;
    private final boolean hideContent;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public interface OnMessageActionListener {
        /** 点击整条消息，打开对应联系人 */
        void onItemClick(MessageItem item, int position);
        /** 点击忽略按钮，删除单条消息 */
        void onDismissClick(MessageItem item, int position);
    }

    public MessagePopupAdapter(List<MessageItem> messageList, OnMessageActionListener actionListener) {
        this(messageList, actionListener, false);
    }

    public MessagePopupAdapter(List<MessageItem> messageList, OnMessageActionListener actionListener, boolean hideContent) {
        this.messageList = messageList;
        this.actionListener = actionListener;
        this.hideContent = hideContent;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_popup, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MessageItem item = messageList.get(position);
        holder.tvAppName.setText(item.getAppName());
        holder.tvSender.setText("👤 " + (item.sender != null ? item.sender : ""));

        if (hideContent) {
            holder.tvContent.setText("🔒 消息内容已隐藏");
            holder.tvContent.setTextColor(0xFFB0B0B0);
        } else {
            String preview = item.content != null && item.content.length() > 50
                    ? item.content.substring(0, 50) + "..."
                    : item.content;
            holder.tvContent.setText(preview);
            holder.tvContent.setTextColor(0xFFE0E0E0);
        }
        holder.tvTime.setText(timeFormat.format(new Date(item.timestamp)));

        // 点击整条打开对应联系人
        holder.itemView.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onItemClick(item, holder.getAdapterPosition());
            }
        });

        // 点击忽略按钮删除单条
        holder.btnDismissItem.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onDismissClick(item, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAppName;
        TextView tvSender;
        TextView tvContent;
        TextView tvTime;
        TextView btnDismissItem;

        ViewHolder(View itemView) {
            super(itemView);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            tvSender = itemView.findViewById(R.id.tvSender);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvTime = itemView.findViewById(R.id.tvTime);
            btnDismissItem = itemView.findViewById(R.id.btnDismissItem);
        }
    }
}
