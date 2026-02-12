package com.shin.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "todos")
public class TodoItem {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String title;
    public String description; // ← new
    public long createdAt;     // ← new (milliseconds since epoch)
    public boolean completed;

    public TodoItem(String title, String description) {
        this.title = title;
        this.description = description;
        this.createdAt = System.currentTimeMillis(); // auto-set on creation
        this.completed = false;
    }
}