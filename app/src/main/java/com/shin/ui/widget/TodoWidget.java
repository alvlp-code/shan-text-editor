// ui/widget/TodoWidget.java
package com.shin.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import com.shin.R;
import com.shin.ui.main.MainActivity;

public class TodoWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_todo_layout);

        // Set up list
        Intent serviceIntent = new Intent(context, TodoWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setRemoteAdapter(R.id.widget_list, serviceIntent);

        // Open app on title click
        Intent launchIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE
        );

        // Add task button
        Intent addIntent = new Intent(context, MainActivity.class);
        addIntent.setAction("ACTION_ADD_TASK");
        PendingIntent addPendingIntent = PendingIntent.getActivity(
                context, 1, addIntent, PendingIntent.FLAG_IMMUTABLE
        );

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}