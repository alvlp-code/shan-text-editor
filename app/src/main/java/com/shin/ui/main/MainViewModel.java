package com.shin.ui.main;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.shin.data.TodoDatabase;
import com.shin.data.model.TodoItem;
import com.shin.data.repository.TodoRepository;
import java.util.List;

public class MainViewModel extends AndroidViewModel {
    private final TodoRepository repository;
    private final MutableLiveData<List<TodoItem>> todos = new MutableLiveData<>();

    public MainViewModel(@NonNull Application application) {
        super(application);
        TodoDatabase db = TodoDatabase.getInstance(application);
        repository = new TodoRepository(db);
        loadTodos();
    }

    public LiveData<List<TodoItem>> getTodos() {
        return todos;
    }

    public void loadTodos() {
        repository.getAllTodos(todos::postValue);
    }

    public void addTodo(String title, String description) {
        if (!title.trim().isEmpty()) {
            repository.insertTodo(new TodoItem(title, description));
            loadTodos();
        }
    }

    public void deleteTodo(TodoItem item) {
        repository.deleteTodo(item);
        loadTodos();
    }

    public void updateTodo(long id, String title, String description) {
        repository.updateTodo(id, title, description);
        loadTodos();
    }
}