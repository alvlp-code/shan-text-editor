package com.shan;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.shan.texteditor.TextEditorActivity;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotesActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private FileAdapter fileAdapter;
    private List<FileItem> fileItems;
    private TextView pathText;
    private BottomNavigationView bottomNav;
    private SwipeRefreshLayout swipeRefreshLayout;

    private File currentDir;
    private File shanRootDir;
    private File homeDir;

    // File observers for different directories
    private Map<String, FileObserver> fileObservers = new HashMap<>();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // For creating new files
    private final ActivityResultLauncher<Intent> editorLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    refreshFileList();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        recyclerView = findViewById(R.id.recyclerView);
        pathText = findViewById(R.id.pathText);
        bottomNav = findViewById(R.id.bottomNav);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        // Initialize directories
        setupDirectories();

        // Setup RecyclerView
        fileItems = new ArrayList<>();
        fileAdapter = new FileAdapter(fileItems);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(fileAdapter);

        // Setup Swipe to Refresh
        setupSwipeRefresh();

        // Setup Bottom Navigation
        setupBottomNav();

        // Load initial directory
        navigateToDirectory(homeDir);
    }

    private void setupDirectories() {
        // Documents/Shan as root
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        shanRootDir = new File(documentsDir, "Shan");
        homeDir = shanRootDir;

        // Create if not exists
        if (!shanRootDir.exists()) {
            shanRootDir.mkdirs();
        }

        currentDir = homeDir;
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            refreshFileList();
            swipeRefreshLayout.setRefreshing(false);
        });

        swipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light
        );
    }

    private void setupBottomNav() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_back) {
                goBack();
                return true;
            } else if (id == R.id.nav_forward) {
                // Forward is handled by clicking folders
                Toast.makeText(this, "Tap a folder to navigate forward", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.nav_add_note) {
                addNote();
                return true;
            } else if (id == R.id.nav_add_folder) {
                addFolder();
                return true;
            } else if (id == R.id.nav_home) {
                navigateToDirectory(homeDir);
                return true;
            }
            return false;
        });
    }

    private void navigateToDirectory(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            Toast.makeText(this, "Directory not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Stop observing previous directory
        stopObservingCurrentDirectory();

        currentDir = dir;
        updatePathText();
        refreshFileList();

        // Start observing new directory
        startObservingDirectory(dir);
    }

    private void stopObservingCurrentDirectory() {
        if (currentDir != null) {
            String path = currentDir.getAbsolutePath();
            FileObserver observer = fileObservers.remove(path);
            if (observer != null) {
                observer.stopWatching();
            }
        }
    }

    private void startObservingDirectory(File dir) {
        String path = dir.getAbsolutePath();

        // Don't create duplicate observers
        if (fileObservers.containsKey(path)) {
            return;
        }

        FileObserver observer = new FileObserver(path) {
            @Override
            public void onEvent(int event, @Nullable String fileName) {
                // Filter relevant events
                int relevantEvents = FileObserver.CREATE | FileObserver.DELETE |
                        FileObserver.MODIFY | FileObserver.MOVED_FROM |
                        FileObserver.MOVED_TO;

                if ((event & relevantEvents) != 0) {
                    // Post to main thread with debouncing
                    mainHandler.post(() -> {
                        // Debounce multiple rapid events
                        mainHandler.removeCallbacks(refreshRunnable);
                        mainHandler.postDelayed(refreshRunnable, 500);
                    });
                }
            }
        };

        observer.startWatching();
        fileObservers.put(path, observer);
    }

    private Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshFileList();
        }
    };

    private void updatePathText() {
        String path = currentDir.getAbsolutePath();
        String relativePath = path.replace(shanRootDir.getAbsolutePath(), "");
        if (relativePath.isEmpty()) {
            relativePath = "/";
        }
        pathText.setText("📁 Shan" + relativePath);
    }

    private void refreshFileList() {
        executorService.execute(() -> {
            final List<FileItem> newItems = new ArrayList<>();

            // Add parent directory if not at root
            if (!currentDir.equals(shanRootDir)) {
                newItems.add(new FileItem("..", currentDir.getParentFile(), true, 0));
            }

            // Get files and folders
            File[] files = currentDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    boolean isFolder = file.isDirectory();
                    long size = isFolder ? countFilesInFolder(file) : file.length();
                    long modified = file.lastModified();
                    newItems.add(new FileItem(file.getName(), file, isFolder, size, modified));
                }
            }

            // Sort: folders first, then files (alphabetically)
            Collections.sort(newItems, (a, b) -> {
                if (a.isFolder && !b.isFolder) return -1;
                if (!a.isFolder && b.isFolder) return 1;
                return a.name.compareToIgnoreCase(b.name);
            });

            // Update UI on main thread
            mainHandler.post(() -> {
                fileItems.clear();
                fileItems.addAll(newItems);
                fileAdapter.notifyDataSetChanged();
            });
        });
    }

    // ==================== BYTE-LEVEL FILE OPERATIONS ====================

    /**
     * Read file as byte array using FileInputStream
     */
    private byte[] readFileAsBytes(File file) throws IOException {
        FileInputStream fis = null;
        ByteArrayOutputStream baos = null;
        try {
            fis = new FileInputStream(file);
            baos = new ByteArrayOutputStream();

            byte[] buffer = new byte[8192]; // 8KB buffer
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }

            return baos.toByteArray();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (baos != null) {
                try {
                    baos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Read file as byte array using FileChannel (more efficient for large files)
     */
    private byte[] readFileWithChannel(File file) throws IOException {
        RandomAccessFile raf = null;
        FileChannel channel = null;
        try {
            raf = new RandomAccessFile(file, "r");
            channel = raf.getChannel();

            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            channel.read(buffer);

            return buffer.array();
        } finally {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Read file in chunks (for very large files)
     */
    private void readFileInChunks(File file, ChunkProcessor processor) throws IOException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            byte[] buffer = new byte[8192]; // 8KB chunks
            int bytesRead;
            long position = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                processor.processChunk(chunk, position, bytesRead);
                position += bytesRead;
            }
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Write byte array to file
     */
    private void writeBytesToFile(File file, byte[] data) throws IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(data);
            fos.flush();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Append byte array to file
     */
    private void appendBytesToFile(File file, byte[] data) throws IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file, true); // true for append mode
            fos.write(data);
            fos.flush();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Get file hash (MD5, SHA-1, SHA-256)
     */
    private String getFileHash(File file, String algorithm) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        FileInputStream fis = null;

        try {
            fis = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            byte[] hashBytes = digest.digest();

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();

        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Compare two files byte by byte
     */
    private boolean compareFiles(File file1, File file2) throws IOException {
        if (file1.length() != file2.length()) {
            return false;
        }

        FileInputStream fis1 = null;
        FileInputStream fis2 = null;

        try {
            fis1 = new FileInputStream(file1);
            fis2 = new FileInputStream(file2);

            int b1, b2;
            while ((b1 = fis1.read()) != -1 && (b2 = fis2.read()) != -1) {
                if (b1 != b2) {
                    return false;
                }
            }
            return true;

        } finally {
            if (fis1 != null) {
                try {
                    fis1.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fis2 != null) {
                try {
                    fis2.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Interface for chunk processing
     */
    interface ChunkProcessor {
        void processChunk(byte[] chunk, long position, int length);
    }

    // ==================== ENHANCED FILE OPTIONS ====================

    private void showFileOptions(File file) {
        String[] options = {"Open", "Rename", "Delete", "Info", "Byte Operations"};

        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Open
                            openFile(file);
                            break;
                        case 1: // Rename
                            renameFile(file);
                            break;
                        case 2: // Delete
                            deleteFile(file);
                            break;
                        case 3: // Info
                            showFileInfo(file);
                            break;
                        case 4: // Byte Operations
                            showByteOperations(file);
                            break;
                    }
                })
                .show();
    }

    private void showByteOperations(File file) {
        String[] options = {
                "View as Hex",
                "Calculate MD5",
                "Calculate SHA-1",
                "Calculate SHA-256",
                "First 100 bytes",
                "File Signature",
                "Compare with another file"
        };

        new AlertDialog.Builder(this)
                .setTitle("Byte Operations - " + file.getName())
                .setItems(options, (dialog, which) -> {
                    try {
                        switch (which) {
                            case 0: // View as Hex
                                viewFileAsHex(file);
                                break;
                            case 1: // MD5
                                calculateAndShowHash(file, "MD5");
                                break;
                            case 2: // SHA-1
                                calculateAndShowHash(file, "SHA-1");
                                break;
                            case 3: // SHA-256
                                calculateAndShowHash(file, "SHA-256");
                                break;
                            case 4: // First 100 bytes
                                showFirstBytes(file, 100);
                                break;
                            case 5: // File Signature
                                showFileSignature(file);
                                break;
                            case 6: // Compare
                                showFileComparisonDialog(file);
                                break;
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void viewFileAsHex(File file) throws IOException {
        byte[] bytes = readFileWithChannel(file);
        StringBuilder hexBuilder = new StringBuilder();

        // Show first 1024 bytes max
        int maxBytes = Math.min(bytes.length, 1024);

        for (int i = 0; i < maxBytes; i++) {
            if (i > 0 && i % 16 == 0) {
                hexBuilder.append("\n");
            }
            hexBuilder.append(String.format("%02X ", bytes[i] & 0xFF));
        }

        if (bytes.length > 1024) {
            hexBuilder.append("\n... and ").append(bytes.length - 1024).append(" more bytes");
        }

        new AlertDialog.Builder(this)
                .setTitle("Hex View - " + file.getName() + " (" + bytes.length + " bytes)")
                .setMessage(hexBuilder.toString())
                .setPositiveButton("OK", null)
                .setNegativeButton("Copy", (dialog, which) -> {
                    // Copy to clipboard
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip =
                            android.content.ClipData.newPlainText("hex", hexBuilder.toString());
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void calculateAndShowHash(File file, String algorithm) throws Exception {
        String hash = getFileHash(file, algorithm);
        new AlertDialog.Builder(this)
                .setTitle(algorithm + " Hash")
                .setMessage(hash)
                .setPositiveButton("OK", null)
                .setNegativeButton("Copy", (dialog, which) -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip =
                            android.content.ClipData.newPlainText("hash", hash);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showFirstBytes(File file, int count) throws IOException {
        byte[] bytes = readFileWithChannel(file);
        int showCount = Math.min(bytes.length, count);

        StringBuilder sb = new StringBuilder();
        sb.append("First ").append(showCount).append(" bytes:\n\n");

        // Show as ASCII and hex
        sb.append("ASCII: ");
        for (int i = 0; i < showCount; i++) {
            char c = (char) bytes[i];
            if (c >= 32 && c <= 126) { // Printable ASCII
                sb.append(c);
            } else {
                sb.append('.');
            }
        }

        sb.append("\n\nHEX: ");
        for (int i = 0; i < showCount; i++) {
            sb.append(String.format("%02X ", bytes[i]));
        }

        new AlertDialog.Builder(this)
                .setTitle("File Preview")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private void showFileSignature(File file) throws IOException {
        byte[] header = readFileWithChannel(file);
        if (header.length < 8) {
            Toast.makeText(this, "File too small", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check common file signatures
        StringBuilder signature = new StringBuilder();
        signature.append("File: ").append(file.getName()).append("\n\n");
        signature.append("First 8 bytes (hex): ");
        for (int i = 0; i < 8 && i < header.length; i++) {
            signature.append(String.format("%02X ", header[i]));
        }

        signature.append("\n\nPossible file type:\n");

        // Check for common signatures
        if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8) {
            signature.append("📷 JPEG image");
        } else if (header[0] == (byte) 0x89 && header[1] == (byte) 0x50 &&
                header[2] == (byte) 0x4E && header[3] == (byte) 0x47) {
            signature.append("🖼️ PNG image");
        } else if (header[0] == (byte) 0x25 && header[1] == (byte) 0x50 &&
                header[2] == (byte) 0x44 && header[3] == (byte) 0x46) {
            signature.append("📄 PDF document");
        } else if (header[0] == (byte) 0x50 && header[1] == (byte) 0x4B) {
            signature.append("📦 ZIP archive (or Office document)");
        } else if (header[0] == (byte) 0x7F && header[1] == (byte) 0x45 &&
                header[2] == (byte) 0x4C && header[3] == (byte) 0x46) {
            signature.append("⚙️ ELF executable");
        } else {
            signature.append("❓ Unknown file type");
        }

        new AlertDialog.Builder(this)
                .setTitle("File Signature")
                .setMessage(signature.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private void showFileComparisonDialog(File file1) {
        // Create a list of other files in current directory for comparison
        File[] files = currentDir.listFiles();
        if (files == null || files.length == 0) {
            Toast.makeText(this, "No files to compare with", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> fileNames = new ArrayList<>();
        List<File> fileList = new ArrayList<>();

        for (File f : files) {
            if (!f.isDirectory() && !f.equals(file1)) {
                fileNames.add(f.getName());
                fileList.add(f);
            }
        }

        if (fileNames.isEmpty()) {
            Toast.makeText(this, "No other files to compare with", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Compare with")
                .setItems(fileNames.toArray(new String[0]), (dialog, which) -> {
                    File file2 = fileList.get(which);
                    compareTwoFiles(file1, file2);
                })
                .show();
    }

    private void compareTwoFiles(File file1, File file2) {
        executorService.execute(() -> {
            try {
                boolean isEqual = compareFiles(file1, file2);
                mainHandler.post(() -> {
                    String message = isEqual ?
                            "✅ Files are IDENTICAL" :
                            "❌ Files are DIFFERENT";

                    new AlertDialog.Builder(NotesActivity.this)
                            .setTitle("Comparison Result")
                            .setMessage(message +
                                    "\n\nFile 1: " + file1.getName() + " (" + file1.length() + " bytes)" +
                                    "\nFile 2: " + file2.getName() + " (" + file2.length() + " bytes)")
                            .setPositiveButton("OK", null)
                            .show();
                });
            } catch (IOException e) {
                mainHandler.post(() ->
                        Toast.makeText(NotesActivity.this, "Comparison failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    // ==================== EXISTING METHODS ====================

    private long countFilesInFolder(File folder) {
        long count = 0;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    count += countFilesInFolder(file);
                } else {
                    count++;
                }
            }
        }
        return count;
    }

    private void goBack() {
        if (!currentDir.equals(shanRootDir)) {
            navigateToDirectory(currentDir.getParentFile());
        } else {
            Toast.makeText(this, "Already at root", Toast.LENGTH_SHORT).show();
        }
    }

    private void addNote() {
        showCreateDialog("New Note", "note", (name) -> {
            File newFile = new File(currentDir, name + ".md");
            try {
                if (newFile.createNewFile()) {
                    // Open editor
                    Intent intent = TextEditorActivity.newIntent(this, newFile.getAbsolutePath());
                    editorLauncher.launch(intent);
                } else {
                    Toast.makeText(this, "File already exists", Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                Toast.makeText(this, "Failed to create note: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addFolder() {
        showCreateDialog("New Folder", "folder", (name) -> {
            File newFolder = new File(currentDir, name);
            if (newFolder.mkdirs()) {
                // Observer will catch this change
                Toast.makeText(this, "Folder created", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Folder already exists", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showCreateDialog(String title, String type, OnNameConfirmedListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Create EditText
        EditText input = new EditText(this);
        input.setHint(type.equals("note") ? "" : "");
        input.setMaxLines(1);

        // Add to dialog
        builder.setTitle("Create")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        listener.onConfirmed(name);
                    } else {
                        Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                    }
                });

        // Create and show dialog
        AlertDialog dialog = builder.create();
        dialog.show();

        // Auto-show keyboard
        input.requestFocus();
        input.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }, 100); // Small delay to ensure dialog is shown
    }

    private void openFile(File file) {
        if (file.isDirectory()) {
            navigateToDirectory(file);
        } else {
            // Use the newIntent with file path
            Intent intent = TextEditorActivity.newIntent(this, file.getAbsolutePath());
            editorLauncher.launch(intent);
        }
    }

    private void renameFile(File file) {
        // Create EditText
        EditText input = new EditText(this);

        // Get the full filename
        String fileName = file.getName();

        input.setText(fileName);
        input.setMaxLines(1);

        // Select text WITHOUT extension for files
        if (!file.isDirectory() && fileName.contains(".")) {
            int lastDotIndex = fileName.lastIndexOf(".");
            if (lastDotIndex > 0) {
                // Select only the part before the last dot
                input.setSelection(0, lastDotIndex);
            } else {
                input.selectAll();
            }
        } else {
            input.selectAll();
        }

        // Create dialog
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Rename " + (file.isDirectory() ? "Folder" : "File"))
                .setView(input)
                .setPositiveButton("Rename", (dialogInterface, which) -> {
                    String newName = input.getText().toString().trim();

                    if (newName.isEmpty()) {
                        Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    File newFile = new File(file.getParent(), newName);

                    if (newFile.exists() && !file.equals(newFile)) {
                        Toast.makeText(this, "A file with this name already exists", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (file.renameTo(newFile)) {
                        Toast.makeText(this, "Renamed successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Rename failed", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            // Auto-show keyboard
            input.requestFocus();
            input.postDelayed(() -> {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }, 200);
        });

        dialog.show();
    }

    private void deleteFile(File file) {
        new AlertDialog.Builder(this)
                .setTitle("Delete")
                .setMessage("Are you sure you want to delete \"" + file.getName() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (deleteRecursive(file)) {
                        // Observer will catch this change
                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    private void showFileInfo(File file) {
        String type = file.isDirectory() ? "Folder" : "File";
        String size = file.isDirectory() ?
                countFilesInFolder(file) + " items" :
                formatFileSize(file.length());
        String modified = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(file.lastModified()));

        new AlertDialog.Builder(this)
                .setTitle("File Info")
                .setMessage("Name: " + file.getName() +
                        "\n\nType: " + type +
                        "\nSize: " + size +
                        "\nBytes: " + file.length() +
                        "\nModified: " + modified +
                        "\nPath: " + file.getAbsolutePath())
                .setPositiveButton("OK", null)
                .show();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    @Override
    public void onBackPressed() {
        if (!currentDir.equals(shanRootDir)) {
            goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up observers
        for (FileObserver observer : fileObservers.values()) {
            observer.stopWatching();
        }
        fileObservers.clear();
        executorService.shutdown();
        mainHandler.removeCallbacks(refreshRunnable);
    }

    private interface OnNameConfirmedListener {
        void onConfirmed(String name);
    }

    // File Item Model
    static class FileItem {
        String name;
        File file;
        boolean isFolder;
        long size;
        long modified;

        FileItem(String name, File file, boolean isFolder, long size, long modified) {
            this.name = name;
            this.file = file;
            this.isFolder = isFolder;
            this.size = size;
            this.modified = modified;
        }

        FileItem(String name, File file, boolean isFolder, long size) {
            this(name, file, isFolder, size, 0);
        }
    }

    // RecyclerView Adapter
    class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
        private List<FileItem> items;

        FileAdapter(List<FileItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_file, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FileItem item = items.get(position);
            holder.nameText.setText(item.name);

            if (item.name.equals("..")) {
                holder.iconView.setImageResource(R.drawable.ic_arrow_up_from_line);
                holder.infoText.setText("Parent directory");
            } else if (item.isFolder) {
                holder.iconView.setImageResource(R.drawable.ic_dir);
                holder.infoText.setText(item.size + " items");
            } else {
                holder.iconView.setImageResource(R.drawable.ic_file);
                holder.infoText.setText(formatFileSize(item.size));
            }

            holder.itemView.setOnClickListener(v -> {
                if (item.name.equals("..")) {
                    goBack();
                } else {
                    openFile(item.file);
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (!item.name.equals("..")) {
                    showFileOptions(item.file);
                    return true;
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView iconView;
            TextView nameText;
            TextView infoText;

            ViewHolder(View itemView) {
                super(itemView);
                iconView = itemView.findViewById(R.id.iconView);
                nameText = itemView.findViewById(R.id.nameText);
                infoText = itemView.findViewById(R.id.infoText);
            }
        }
    }
}