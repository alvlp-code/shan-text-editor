// com.shin.ui.main.SwipeToEditDeleteCallback.java
package com.shin.ui.main;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.shin.data.model.TodoItem;

public class SwipeToEditDeleteCallback extends ItemTouchHelper.SimpleCallback {
    private final TodoAdapter adapter;
    private final Paint editPaint = new Paint();
    private final Paint deletePaint = new Paint();
    private final float iconPadding = 30f;

    public SwipeToEditDeleteCallback(TodoAdapter adapter) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.adapter = adapter;
        editPaint.setColor(Color.parseColor("#4CAF50"));   // Green for Edit
        deletePaint.setColor(Color.parseColor("#F44336")); // Red for Delete
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getAdapterPosition();
        if (position < 0 || position >= adapter.getCurrentList().size()) return;

        TodoItem item = adapter.getCurrentList().get(position);

        if (direction == ItemTouchHelper.RIGHT) {
            // ➡️ SWIPE RIGHT = EDIT
            if (adapter.getOnActionListener() != null) {
                adapter.getOnActionListener().onEdit(item);
            }
        } else if (direction == ItemTouchHelper.LEFT) {
            // ⬅️ SWIPE LEFT = DELETE
            if (adapter.getOnActionListener() != null) {
                adapter.getOnActionListener().onDelete(item);
            }
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {
        View itemView = viewHolder.itemView;
        float height = (float) itemView.getBottom() - (float) itemView.getTop();

        if (dX > 0) { // Swiping right → Edit
            RectF background = new RectF(
                    (float) itemView.getLeft(),
                    (float) itemView.getTop(),
                    dX,
                    (float) itemView.getBottom()
            );
            c.drawRect(background, editPaint);

            String text = "Edit";
            float textSize = 40f;
            editPaint.setTextSize(textSize);
            editPaint.setColor(Color.WHITE);
            c.drawText(
                    text,
                    itemView.getLeft() + iconPadding,
                    itemView.getTop() + (height / 2) + (textSize / 3),
                    editPaint  // ✅ FIXED: was "edit本报"
            );

        } else if (dX < 0) { // Swiping left → Delete
            RectF background = new RectF(
                    (float) itemView.getRight() + dX,
                    (float) itemView.getTop(),
                    (float) itemView.getRight(),
                    (float) itemView.getBottom()
            );
            c.drawRect(background, deletePaint);

            String text = "Delete";
            float textSize = 40f;
            deletePaint.setTextSize(textSize);
            deletePaint.setColor(Color.WHITE);
            float textWidth = deletePaint.measureText(text);
            c.drawText(
                    text,
                    itemView.getRight() - textWidth - iconPadding,
                    itemView.getTop() + (height / 2) + (textSize / 3),
                    deletePaint  // ✅ CORRECT
            );
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }
}