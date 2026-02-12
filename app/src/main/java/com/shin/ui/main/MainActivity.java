// MainActivity.java
package com.shin.ui.main;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.shin.R;
import com.shin.data.model.TodoItem;

public class MainActivity extends AppCompatActivity {
    private MainViewModel viewModel;
    private TodoAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set edge-to-edge
        View rootView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            v.setPadding(
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).left,
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).top,
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).right,
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            );
            return insets;
        });

        // Find views
        EditText editTextTitle = findViewById(R.id.edit_text_new);
        EditText editTextDesc = findViewById(R.id.edit_text_desc);
        Button addButton = findViewById(R.id.button_add);
        RecyclerView recyclerView = findViewById(R.id.recycler_view);

        // Setup RecyclerView
        adapter = new TodoAdapter();
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(
                new SwipeToEditDeleteCallback(adapter)
        );
        itemTouchHelper.attachToRecyclerView(recyclerView);

        // ViewModel
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        viewModel.getTodos().observe(this, todos -> adapter.submitList(todos));
        adapter.setOnActionListener(new TodoAdapter.OnTodoActionListener() {
            @Override
            public void onDelete(TodoItem item) {
                viewModel.deleteTodo(item);
            }

            @Override
            public void onEdit(TodoItem item) {
                showEditDialog(item);
            }
        });

        // Add button
        addButton.setOnClickListener(v -> {
            String title = editTextTitle.getText().toString().trim();
            String desc = editTextDesc.getText().toString().trim();
            if (!title.isEmpty()) {
                viewModel.addTodo(title, desc);
                editTextTitle.setText("");
                editTextDesc.setText("");
            }
        });
    }

    private void showEditDialog(TodoItem item) {
        // Create input fields
        EditText titleInput = new EditText(this);
        EditText descInput = new EditText(this);
        titleInput.setText(item.title);
        descInput.setText(item.description != null ? item.description : "");

        // Layout
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(titleInput);
        layout.addView(descInput);

        // Dialog
        new AlertDialog.Builder(this)
                .setTitle("Edit Task")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String title = titleInput.getText().toString().trim();
                    String desc = descInput.getText().toString().trim();
                    if (!title.isEmpty()) {
                        viewModel.updateTodo(item.id, title, desc);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}