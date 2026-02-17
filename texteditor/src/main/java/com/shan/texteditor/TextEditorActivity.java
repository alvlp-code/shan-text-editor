package com.shan.texteditor;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.noties.markwon.Markwon;

public class TextEditorActivity extends AppCompatActivity {

    private CodeEditor codeEditor;
    private TextView markdownPreview;
    private ScrollView previewContainer;
    private TabLayout tabLayout;
    private Button btnSave;
    private ImageButton btnMenu;
    private Markwon markwon;

    // Support both Uri and File path
    private Uri currentFileUri;
    private File currentFile;
    private String filePath;

    private final ActivityResultLauncher<String> saveFileLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/*"),
            uri -> {
                if (uri != null) {
                    currentFileUri = uri;
                    currentFile = null; // Clear file when using SAF
                    saveContentToUri(uri);
                }
            }
    );

    private final ActivityResultLauncher<String[]> openFileLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    currentFileUri = uri;
                    currentFile = null; // Clear file when using SAF
                    loadContentFromUri(uri);
                    setResult(RESULT_OK);
                }
            }
    );

    public static Intent newIntent(Context context) {
        return new Intent(context, TextEditorActivity.class);
    }

    public static Intent newIntent(Context context, String filePath) {
        Intent intent = new Intent(context, TextEditorActivity.class);
        intent.putExtra("file_path", filePath);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_editor);

        codeEditor = findViewById(R.id.codeEditor);
        markdownPreview = findViewById(R.id.markdownPreview);
        previewContainer = findViewById(R.id.previewContainer);
        tabLayout = findViewById(R.id.tabLayout);
        btnSave = findViewById(R.id.btnSave);
        btnMenu = findViewById(R.id.btnMenu);

        markwon = Markwon.create(this);

        setupTextMate();
        setupEditor();
        setupTabs();

        // Check if we have a file path from intent
        handleIntent();

        btnSave.setOnClickListener(v -> saveCurrentFile());

        btnMenu.setOnClickListener(v -> showPopupMenu(v));

        // Auto-open keyboard after a short delay
        autoOpenKeyboard();
    }

    private void autoOpenKeyboard() {
        // Request focus for the code editor
        codeEditor.requestFocus();

        // Small delay to ensure view is fully rendered
        codeEditor.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(codeEditor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }, 300);
    }

    private void handleIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();
        Uri data = intent.getData();

        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action)) {
            // Opened from external app
            if (data != null) {
                currentFileUri = data;
                loadContentFromUri(data);
            }
        } else if (Intent.ACTION_SEND.equals(action)) {
            // Received shared content
            Bundle extras = intent.getExtras();
            if (extras != null) {
                CharSequence sharedText = extras.getCharSequence(Intent.EXTRA_TEXT);
                if (sharedText != null) {
                    codeEditor.setText(sharedText.toString());
                }
            }
        } else if (intent.hasExtra("file_path")) {
            // Opened from NotesActivity
            filePath = intent.getStringExtra("file_path");
            if (filePath != null) {
                currentFile = new File(filePath);
                loadContentFromFile(currentFile);
            }
        }
    }

    private void saveCurrentFile() {
        if (currentFile != null) {
            // Save to direct file path
            saveContentToFile(currentFile);
        } else if (currentFileUri != null) {
            // Save to URI (SAF)
            saveContentToUri(currentFileUri);
        } else {
            // No file associated, create new
            saveFileLauncher.launch("file.md");
        }
    }

    private void showPopupMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenuInflater().inflate(R.menu.menu_toolbar, popup.getMenu());
        popup.getMenu().findItem(R.id.menu_save).setVisible(false);

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_open_file) {
                openFileLauncher.launch(new String[]{"*/*"});
                return true;
            } else if (id == R.id.menu_save_file) {
                saveCurrentFile();
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

        String fileName;
        if (currentFile != null) {
            fileName = currentFile.getName();
        } else if (currentFileUri != null) {
            fileName = currentFileUri.getLastPathSegment();
        } else {
            fileName = "Unsaved";
        }

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
                    saveCurrentFile();
                    finish();
                })
                .setNegativeButton("Don't Save", (dialog, which) -> finish())
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
                    // Auto-open keyboard when switching to Code tab
                    codeEditor.postDelayed(() -> {
                        android.view.inputmethod.InputMethodManager imm =
                                (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.showSoftInput(codeEditor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                    }, 200);
                } else {
                    codeEditor.setVisibility(View.GONE);
                    previewContainer.setVisibility(View.VISIBLE);
                    String markdown = codeEditor.getText().toString();
                    markwon.setMarkdown(markdownPreview, markdown);

                    // Hide keyboard when switching to Preview tab
                    android.view.inputmethod.InputMethodManager imm =
                            (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(codeEditor.getWindowToken(), 0);
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupTextMate() {
        try {
            FileProviderRegistry.getInstance().addFileProvider(new AssetsFileResolver(getAssets()));
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

        // Make editor focusable
        codeEditor.setFocusable(true);
        codeEditor.setFocusableInTouchMode(true);

        // Only set default text if no file was loaded
        if (currentFile == null && currentFileUri == null) {
            codeEditor.setText("# Markdown Editor\n\nStart writing...\n");
        }
    }

    private void saveContentToFile(File file) {
        try {
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            String content = codeEditor.getText().toString();
            writer.write(content);
            writer.flush();
            writer.close();
            fos.close();

            Toast.makeText(this, "File Saved: " + file.getName(), Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Save Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveContentToUri(Uri uri) {
        try {
            ContentResolver resolver = getContentResolver();
            OutputStreamWriter writer = new OutputStreamWriter(
                    resolver.openOutputStream(uri), StandardCharsets.UTF_8);
            String content = codeEditor.getText().toString();
            writer.write(content);
            writer.flush();
            writer.close();

            Toast.makeText(this, "File Saved", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Save Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadContentFromFile(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(fis, StandardCharsets.UTF_8));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            fis.close();

            codeEditor.setText(content.toString());

            // Update title or path display if needed
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(file.getName());
            }

            Toast.makeText(this, "File Loaded: " + file.getName(), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Open Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadContentFromUri(Uri uri) {
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