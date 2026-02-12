package com.shin.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.shin.data.dao.TodoDao;
import com.shin.data.model.TodoItem;

@Database(entities = {TodoItem.class}, version = 1, exportSchema = false)
public abstract class TodoDatabase extends RoomDatabase {
    private static volatile TodoDatabase INSTANCE;

    public abstract TodoDao todoDao();

    public static TodoDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (TodoDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            TodoDatabase.class,
                            "todo_db"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}