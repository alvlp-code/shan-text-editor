package com.shin.ui.widget;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import java.util.List;
import com.shin.data.TodoDatabase;
import com.shin.data.model.TodoItem;

import com.shin.R;

public class TodoWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new TodoRemoteViewsFactory(this.getApplicationContext());
    }

    private static class TodoRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
        private final TodoDatabase database;
        private List<TodoItem> items;

        TodoRemoteViewsFactory(android.content.Context context) {
            database = TodoDatabase.getInstance(context);
        }

        @Override
        public void onCreate() {}

        @Override
        public void onDataSetChanged() {
            // Load todos on background thread (simplified)
            items = database.todoDao().getActiveTodosSync();
        }

        @Override
        public void onDestroy() {

        }

        @Override
        public RemoteViews getViewAt(int position) {
            @SuppressLint("RemoteViewLayout") RemoteViews views = new RemoteViews(
                "com.shin", // Replace with your package name
                R.layout.widget_item
            );
            TodoItem item = items.get(position);
            views.setTextViewText(R.id.widget_text, item.title);
            views.setBoolean(R.id.widget_checkbox, "setChecked", item.completed);
            return views;
        }

        @Override
        public int getCount() {
            return items != null ? items.size() : 0;
        }

        @Override
        public RemoteViews getLoadingView() { return null; }
        @Override
        public int getViewTypeCount() { return 1; }
        @Override
        public long getItemId(int position) { return position; }
        @Override
        public boolean hasStableIds() { return true; }
    }
}