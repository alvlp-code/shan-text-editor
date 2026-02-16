package com.shan;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

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
    private Button btnEdit, btnPreview, btnSave, btnOpen;
    private Markwon markwon;
    private boolean isPreviewMode = false;
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

        // Initialize ALL Views (including Edit & Preview buttons)
        codeEditor = findViewById(R.id.codeEditor);
        markdownPreview = findViewById(R.id.markdownPreview);
        previewContainer = findViewById(R.id.previewContainer);
        btnEdit = findViewById(R.id.btnEdit);
        btnPreview = findViewById(R.id.btnPreview);
        btnSave = findViewById(R.id.btnSave);
        btnOpen = findViewById(R.id.btnOpen);

        // Initialize Markwon
        markwon = Markwon.create(this);

        setupTextMate();
        setupEditor();

        // Edit Button Click Listener
        btnEdit.setOnClickListener(v -> {
            isPreviewMode = false;
            codeEditor.setVisibility(View.VISIBLE);
            previewContainer.setVisibility(View.GONE);
            btnEdit.setBackgroundColor(0xFF0e639c);
            btnPreview.setBackgroundColor(0xFF3c3c3c);
        });

        // Preview Button Click Listener
        btnPreview.setOnClickListener(v -> {
            isPreviewMode = true;
            codeEditor.setVisibility(View.GONE);
            previewContainer.setVisibility(View.VISIBLE);
            btnEdit.setBackgroundColor(0xFF3c3c3c);
            btnPreview.setBackgroundColor(0xFF0e639c);

            // Render Markdown
            String markdown = codeEditor.getText().toString();
            markwon.setMarkdown(markdownPreview, markdown);
        });

        // Save Button Click Listener
        btnSave.setOnClickListener(v -> {
            if (currentFileUri != null) {
                saveContentToFile(currentFileUri);
            } else {
                saveFileLauncher.launch("file.md");
            }
        });

        // Open Button Click Listener
        btnOpen.setOnClickListener(v -> openFileLauncher.launch(new String[]{"*/*"}));
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

        codeEditor.setText("# Markdown Editor\n\nThis is **bold** and this is *italic*.\n\n```java\npublic class Test {}\n```\n");
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

            if (isPreviewMode) {
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