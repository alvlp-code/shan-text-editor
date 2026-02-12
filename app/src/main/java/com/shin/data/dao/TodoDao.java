package com.shin.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.shin.data.model.TodoItem;
import java.util.List;

@Dao
public interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY id DESC")
    List<TodoItem> getAll();

    @Insert
    void insert(TodoItem item);

    @Delete
    void delete(TodoItem item);

    @Query("SELECT * FROM todos WHERE id = :id")
    TodoItem findById(long id);

    @Update
    void update(TodoItem item);

    @Query("SELECT * FROM todos WHERE completed = 0 ORDER BY createdAt DESC")
    List<TodoItem> getActiveTodosSync(); // No LiveData, no Rx — just List
}