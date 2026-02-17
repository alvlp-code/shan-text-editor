package com.shan;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.tabs.TabLayout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

// TextMate Imports
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;

// Markwon Import
import io.noties.markwon.Markwon;

public class MainActivity extends AppCompatActivity {

    private CodeEditor codeEditor;
    private TextView markdownPreview;
    private ScrollView previewContainer;
    private TabLayout tabLayout;
    private Button btnSave;
    private ImageButton btnMenu;
    private Markwon markwon;
    private Uri currentFileUri;

    private final ActivityResultLauncher<String> saveFileLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/*"),
            uri -> {
                if (uri != null) {
                    currentFileUri = uri;
                    saveContentToFile(uri);
                }
            }
    );

    private final ActivityResultLauncher<String[]> openFileLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    currentFileUri = uri;
                    loadContentFromFile(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Views
        codeEditor = findViewById(R.id.codeEditor);
        markdownPreview = findViewById(R.id.markdownPreview);
        previewContainer = findViewById(R.id.previewContainer);
        tabLayout = findViewById(R.id.tabLayout);
        btnSave = findViewById(R.id.btnSave);
        btnMenu = findViewById(R.id.btnMenu);

        // Initialize Markwon
        markwon = Markwon.create(this);

        // Setup TextMate & Editor
        setupTextMate();
        setupEditor();

        // Setup Tabs
        setupTabs();

        // Save Button
        btnSave.setOnClickListener(v -> {
            if (currentFileUri != null) {
                saveContentToFile(currentFileUri);
            } else {
                saveFileLauncher.launch("file.md");
            }
        });

        // 3-Dot Menu Button
        btnMenu.setOnClickListener(v -> showPopupMenu(v));
    }

    private void showPopupMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenuInflater().inflate(R.menu.menu_toolbar, popup.getMenu());

        // Hide the "Save" item from popup (we have a dedicated button)
        popup.getMenu().findItem(R.id.menu_save).setVisible(false);

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_open_file) {
                openFileLauncher.launch(new String[]{"*/*"});
                return true;
            } else if (id == R.id.menu_save_file) {
                if (currentFileUri != null) {
                    saveContentToFile(currentFileUri);
                } else {
                    saveFileLauncher.launch("file.md");
                }
                return true;
            } else if (id == R.id.menu_file_info) {
                showFileInfo();
                return true;
            } else if (id == R.id.menu_close) {
                showCloseDialog();
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void showFileInfo() {
        String content = codeEditor.getText().toString();
        int lines = content.split("\n").length;
        int chars = content.length();
        String fileName = currentFileUri != null ?
                currentFileUri.getLastPathSegment() : "Unsaved";

        new AlertDialog.Builder(this)
                .setTitle("File Info")
                .setMessage("Name: " + fileName +
                        "\n\nLines: " + lines +
                        "\nCharacters: " + chars)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showCloseDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Close File")
                .setMessage("Do you want to save before closing?")
                .setPositiveButton("Save", (dialog, which) -> {
                    if (currentFileUri != null) {
                        saveContentToFile(currentFileUri);
                    } else {
                        saveFileLauncher.launch("file.md");
                    }
                    finish();
                })
                .setNegativeButton("Don't Save", (dialog, which) -> {
                    finish();
                })
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("Code"));
        tabLayout.addTab(tabLayout.newTab().setText("Preview"));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    codeEditor.setVisibility(View.VISIBLE);
                    previewContainer.setVisibility(View.GONE);
                } else {
                    codeEditor.setVisibility(View.GONE);
                    previewContainer.setVisibility(View.VISIBLE);
                    String markdown = codeEditor.getText().toString();
                    markwon.setMarkdown(markdownPreview, markdown);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupTextMate() {
        try {
            FileProviderRegistry.getInstance().addFileProvider(
                    new AssetsFileResolver(getAssets())
            );
            ThemeRegistry.getInstance().setTheme("default");
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json");
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "TextMate Setup Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupEditor() {
        try {
            TextMateColorScheme colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance());
            codeEditor.setColorScheme(colorScheme);
            TextMateLanguage language = TextMateLanguage.create("text.html.markdown", true);
            codeEditor.setEditorLanguage(language);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Language Setup Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        codeEditor.setLineNumberEnabled(true);
        codeEditor.setWordwrap(true);
        codeEditor.setPinLineNumber(true);
        codeEditor.setCursorBlinkPeriod(500);

        codeEditor.setText("# Markdown Editor\n\nThis is **bold** and this is *italic*.\n\n## Features\n- Syntax Highlighting\n- Tab Preview\n- File Menu\n\n```java\npublic class Test {\n    public static void main(String[] args) {\n        System.out.println(\"Hello\");\n    }\n}\n```\n");
    }

    private void saveContentToFile(Uri uri) {
        try {
            ContentResolver resolver = getContentResolver();
            OutputStreamWriter writer = new OutputStreamWriter(
                    resolver.openOutputStream(uri), StandardCharsets.UTF_8);
            String content = codeEditor.getText().toString();
            writer.write(content);
            writer.flush();
            writer.close();
            Toast.makeText(this, "File Saved", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Save Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadContentFromFile(Uri uri) {
        try {
            ContentResolver resolver = getContentResolver();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resolver.openInputStream(uri), StandardCharsets.UTF_8));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            codeEditor.setText(content.toString());

            // Update preview if in preview mode
            if (tabLayout.getSelectedTabPosition() == 1) {
                markwon.setMarkdown(markdownPreview, content.toString());
            }

            Toast.makeText(this, "File Loaded", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Open Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (codeEditor != null) {
            codeEditor.release();
        }
    }
}