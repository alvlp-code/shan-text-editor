package com.shin.ui.main;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import com.shin.R;
import com.shin.data.model.TodoItem;
import com.shin.util.TimeUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TodoAdapter extends RecyclerView.Adapter<TodoAdapter.ViewHolder> {
    private List<TodoItem> todos = new ArrayList<>();
    private OnTodoActionListener listener;
    private final Set<Long> expandedItems = new HashSet<>();

    public TodoAdapter() {
        setHasStableIds(true);
    }

    public interface OnTodoActionListener {
        void onDelete(TodoItem item);
        void onEdit(TodoItem item);
    }

    public void setOnActionListener(OnTodoActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<TodoItem> newTodos) {
        this.todos = newTodos != null ? newTodos : new ArrayList<>();
        notifyDataSetChanged();
    }

    public List<TodoItem> getCurrentList() {
        return todos;
    }

    public OnTodoActionListener getOnActionListener() {
        return listener;
    }

    public void toggleExpanded(long itemId) {
        int position = -1;
        for (int i = 0; i < todos.size(); i++) {
            if (todos.get(i).id == itemId) {
                position = i;
                break;
            }
        }
        if (position != -1) {
            if (expandedItems.contains(itemId)) {
                expandedItems.remove(itemId);
            } else {
                expandedItems.add(itemId);
            }
            notifyItemChanged(position); // efficient!
        }
    }

    public boolean isExpanded(long itemId) {
        return expandedItems.contains(itemId);
    }

    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Ensure themed context
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        // Inflate with themed context
        View view = inflater.inflate(R.layout.item_todo, parent, false);
        return new ViewHolder(view, this);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(todos.get(position)); // ✅ Only this line
    }

    @Override
    public int getItemCount() {
        return todos.size();
    }

    @Override
    public long getItemId(int position) {
        return todos.get(position).id;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        TextView title;
        TextView description;
        TextView timestamp;
        CheckBox checkbox;

        // Store adapter reference for callbacks
        private final TodoAdapter adapter;

        ViewHolder(View itemView, TodoAdapter adapter) {
            super(itemView);
            this.adapter = adapter;
            cardView = itemView.findViewById(R.id.container);
            title = itemView.findViewById(R.id.text_title);
            description = itemView.findViewById(R.id.text_description);
            timestamp = itemView.findViewById(R.id.text_timestamp);
            checkbox = itemView.findViewById(R.id.checkbox);

            // Expand on description click
            description.setOnClickListener(v -> {
                int pos = getAdapterPosition(); // ✅ Use this instead
                if (pos != RecyclerView.NO_POSITION && pos < adapter.todos.size()) {
                    TodoItem item = adapter.todos.get(pos);
                    adapter.toggleExpanded(item.id);
                }
            });

            // Copy on long press
            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition(); // ✅ Use this
                if (pos != RecyclerView.NO_POSITION && pos < adapter.todos.size()) {
                    TodoItem item = adapter.todos.get(pos);
                    String fullText = item.title +
                            (item.description != null && !item.description.trim().isEmpty()
                                    ? "\n\n" + item.description
                                    : "");

                    ClipboardManager clipboard = (ClipboardManager)
                            v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Todo", fullText);
                    clipboard.setPrimaryClip(clip);

                    Toast.makeText(v.getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
        }

        void bind(TodoItem item) {
            title.setText(item.title);
            checkbox.setChecked(item.completed);
            timestamp.setText(TimeUtils.getRelativeTime(item.createdAt));

            // Description visibility & expansion
            if (item.description == null || item.description.trim().isEmpty()) {
                description.setVisibility(View.GONE);
            } else {
                description.setVisibility(View.VISIBLE);
                description.setText(item.description);
                boolean isExpanded = adapter.expandedItems.contains(item.id);
                description.setMaxLines(isExpanded ? Integer.MAX_VALUE : 2);
                description.setEllipsize(isExpanded ? null : TextUtils.TruncateAt.END);
            }

            Context context = itemView.getContext();

            long ageDays = (System.currentTimeMillis() - item.createdAt) / (24 * 60 * 60 * 1000);

            int bgColor;
            if (ageDays >= 7) {
                bgColor = getThemeColor(context, R.attr.colorAgingRed);
            } else if (ageDays >= 2) {
                bgColor = getThemeColor(context, R.attr.colorAgingOrange);
            } else if (ageDays >= 1) {
                bgColor = getThemeColor(context, R.attr.colorAgingYellow);
            } else {
                // Use surface color from theme
                bgColor = getThemeColor(context, com.google.android.material.R.attr.colorSurface);
            }

            cardView.setCardBackgroundColor(bgColor); // ✅ Correct
        }
        private static int getThemeColor(Context context, int attr) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}