package com.shan;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.shan.texteditor.TextEditorActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnNotes = findViewById(R.id.btnNotes);
        Button btnEditor = findViewById(R.id.btnEditor);

        btnNotes.setOnClickListener(v -> {
            startActivity(new Intent(this, NotesActivity.class));
        });

        btnEditor.setOnClickListener(v -> {
            startActivity(TextEditorActivity.newIntent(this));
        });
    }
}