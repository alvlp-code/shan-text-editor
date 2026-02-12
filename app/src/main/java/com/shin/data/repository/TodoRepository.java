package com.shin.data.repository;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;

import com.shin.R;
import com.shin.data.TodoDatabase;
import com.shin.data.dao.TodoDao;
import com.shin.data.model.TodoItem;
import com.shin.ui.widget.TodoWidget;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TodoRepository {
    private final TodoDao todoDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public TodoRepository(TodoDatabase database) {
        this.todoDao = database.todoDao();
    }

    public void getAllTodos(@NonNull GetTodosCallback callback) {
        executor.execute(() -> {
            List<TodoItem> todos = todoDao.getAll();
            mainHandler.post(() -> callback.onSuccess(todos));
        });
    }

    public void insertTodo(TodoItem item) {
        executor.execute(() -> todoDao.insert(item));
    }

    public void deleteTodo(TodoItem item) {
        executor.execute(() -> todoDao.delete(item));
    }

    public void updateTodo(long id, String title, String description) {
        executor.execute(() -> {
            TodoItem item = todoDao.findById(id);
            if (item != null) {
                item.title = title;
                item.description = description;
                todoDao.update(item);
            }
        });
    }

    // In TodoRepository.java
    private void notifyWidget(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                new ComponentName(context, TodoWidget.class)
        );
        if (appWidgetIds.length > 0) {
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list);
        }
    }
    public interface GetTodosCallback {
        void onSuccess(List<TodoItem> todos);
    }
}