
package com.dishii.mm;
import org.libsdl.app.SDLActivity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.Build;
import android.widget.Toast;

import android.util.Log;

import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ImageView;

//This class is the main SDLActivity and just sets up a bunch of default files and the input overlay
public class MainActivity extends SDLActivity{

    SharedPreferences preferences;
    private static final CountDownLatch setupLatch = new CountDownLatch(1);
    private static String currentDataRootPath = "/storage/emulated/0/2S2H";
    private static final String PREF_DATA_ROOT_PATH = "dataRootPath";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        preferences = getSharedPreferences("com.dishii.mm.prefs",Context.MODE_PRIVATE);

        updateCurrentDataRootPath();

        if (hasStoragePermission()) {
            beginSetupOrChooseDataRoot();
        } else {
            requestStoragePermission();
        }

        super.onCreate(savedInstanceState);

        setupControllerOverlay();
        applyImmersiveFullscreen();
        attachController();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveFullscreen();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveFullscreen();
        }
    }

    private void applyImmersiveFullscreen() {
        Window window = getWindow();
        if (window == null) {
            return;
        }

        View decorView = window.getDecorView();
        int flags = View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decorView.setSystemUiVisibility(flags);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    public static void waitForSetupFromNative() {
        try {
            setupLatch.await();  // Block until setup is complete
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static String getDataRootPathFromNative() {
        return currentDataRootPath;
    }

    private void doVersionCheck(){
        int currentVersion = BuildConfig.VERSION_CODE;
        int storedVersion = preferences.getInt("appVersion", 1);

        if (currentVersion > storedVersion) {
            deleteOutdatedAssets();
            preferences.edit().putInt("appVersion", currentVersion).apply();
        }
    }

    private void deleteOutdatedAssets() {
        File targetRootFolder = getTargetRootFolder();

        File sohFile = new File(targetRootFolder, "2ship.o2r");
        File assetsFolder = new File(targetRootFolder, "assets");

        deleteIfExists(sohFile);
        deleteRecursiveIfExists(assetsFolder);
    }

    private void deleteIfExists(File file) {
        if (file.exists()) {
            if (file.delete()) {
                Log.i("deleteAssets", "Deleted file: " + file.getAbsolutePath());
            } else {
                Log.w("deleteAssets", "Failed to delete file: " + file.getAbsolutePath());
            }
        } else {
            Log.i("deleteAssets", "File not found (skipped): " + file.getAbsolutePath());
        }
    }

    private void deleteRecursiveIfExists(File dir) {
        if (dir.exists()) {
            deleteRecursive(dir);
            Log.i("deleteAssets", "Deleted directory: " + dir.getAbsolutePath());
        } else {
            Log.i("deleteAssets", "Directory not found (skipped): " + dir.getAbsolutePath());
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    private File getTargetRootFolder() {
        String configuredPath = preferences.getString(PREF_DATA_ROOT_PATH, null);
        if (configuredPath == null || configuredPath.isEmpty()) {
            return getDefaultDataRootFolder();
        }

        return new File(configuredPath);
    }

    private File getDefaultDataRootFolder() {
        return new File(Environment.getExternalStorageDirectory(), "2S2H");
    }

    private void updateCurrentDataRootPath() {
        currentDataRootPath = getTargetRootFolder().getAbsolutePath();
    }

    private void beginSetupOrChooseDataRoot() {
        if (!preferences.contains(PREF_DATA_ROOT_PATH)) {
            preferences.edit().putString(PREF_DATA_ROOT_PATH, getDefaultDataRootFolder().getAbsolutePath()).apply();
            updateCurrentDataRootPath();
        }

        beginSetupIfStorageReady();
    }

    private static class DataRootOption {
        final String label;
        final File folder;

        DataRootOption(String label, File folder) {
            this.label = label;
            this.folder = folder;
        }
    }

    private List<DataRootOption> getDataRootOptions() {
        Map<String, DataRootOption> options = new LinkedHashMap<>();
        File defaultFolder = getDefaultDataRootFolder();
        options.put(defaultFolder.getAbsolutePath(),
                new DataRootOption("Internal storage: " + defaultFolder.getAbsolutePath(), defaultFolder));

        File[] externalDirs = getExternalFilesDirs(null);
        if (externalDirs != null) {
            for (File externalDir : externalDirs) {
                if (externalDir == null || !Environment.isExternalStorageRemovable(externalDir)) {
                    continue;
                }

                File volumeRoot = getVolumeRootFromExternalFilesDir(externalDir);
                if (volumeRoot == null) {
                    continue;
                }

                File sdFolder = new File(volumeRoot, "2S2H");
                options.put(sdFolder.getAbsolutePath(),
                        new DataRootOption("SD card: " + sdFolder.getAbsolutePath(), sdFolder));
            }
        }

        return new ArrayList<>(options.values());
    }

    private File getVolumeRootFromExternalFilesDir(File externalDir) {
        String path = externalDir.getAbsolutePath();
        String suffix = "/Android/data/" + getPackageName() + "/files";
        int suffixIndex = path.indexOf(suffix);
        if (suffixIndex > 0) {
            return new File(path.substring(0, suffixIndex));
        }

        File parent = externalDir;
        for (int i = 0; i < 4 && parent != null; i++) {
            parent = parent.getParentFile();
        }
        return parent;
    }

    private void showDataRootChooser(boolean restartRequired) {
        List<DataRootOption> options = getDataRootOptions();
        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) {
            labels[i] = options.get(i).label;
        }

        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("Choose Data Folder")
                .setMessage("Choose where 2S2H stores saves, mods, settings, and support files.")
                .setItems(labels, (dialog, which) -> {
                    DataRootOption selected = options.get(which);
                    Executors.newSingleThreadExecutor().execute(() -> applyDataRootSelection(selected.folder, restartRequired));
                })
                .setCancelable(false)
                .show());
    }

    private void applyDataRootSelection(File targetRootFolder, boolean restartRequired) {
        File previousRoot = getTargetRootFolder();
        preferences.edit().putString(PREF_DATA_ROOT_PATH, targetRootFolder.getAbsolutePath()).apply();
        updateCurrentDataRootPath();

        if (!ensureTargetRootFolderReady(targetRootFolder)) {
            preferences.edit().putString(PREF_DATA_ROOT_PATH, previousRoot.getAbsolutePath()).apply();
            updateCurrentDataRootPath();
            showSetupFailure("The selected data folder is not writable.");
            return;
        }

        migrateExistingRootIfNeeded(previousRoot, targetRootFolder);
        migrateExistingRootIfNeeded(getDefaultDataRootFolder(), targetRootFolder);

        if (restartRequired) {
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("Restart Required")
                    .setMessage("2S2H will use the new data folder after restarting. Existing data was copied when needed; the old folder was left in place.")
                    .setCancelable(false)
                    .setPositiveButton("Close App", (dialog, which) -> finish())
                    .show());
            return;
        }

        beginSetupIfStorageReady();
    }

    private void migrateExistingRootIfNeeded(File sourceRoot, File targetRoot) {
        if (sourceRoot == null || targetRoot == null ||
                sourceRoot.getAbsolutePath().equals(targetRoot.getAbsolutePath()) ||
                !sourceRoot.isDirectory() || !isDirectoryEmpty(targetRoot)) {
            return;
        }

        try {
            AssetCopyUtil.copyDirectoryContents(sourceRoot, targetRoot);
            Log.i("setupFiles", "Copied existing data from: " + sourceRoot.getAbsolutePath());
        } catch (IOException e) {
            Log.e("setupFiles", "Failed to copy existing data from: " + sourceRoot.getAbsolutePath(), e);
            runOnUiThread(() -> Toast.makeText(this, "Could not copy existing data", Toast.LENGTH_LONG).show());
        }
    }

    private boolean isDirectoryEmpty(File directory) {
        File[] files = directory.listFiles();
        return files == null || files.length == 0;
    }

    private void beginSetupIfStorageReady() {
        updateCurrentDataRootPath();
        File targetRootFolder = getTargetRootFolder();
        if (!ensureTargetRootFolderReady(targetRootFolder)) {
            showStorageAccessFailure();
            return;
        }

        doVersionCheck();
        checkAndSetupFiles();
    }

    // Check if storage permission is granted
    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private boolean ensureTargetRootFolderReady(File targetRootFolder) {
        if (!isExternalStorageWritable() || !hasStoragePermission()) {
            return false;
        }

        if (!targetRootFolder.exists() && !targetRootFolder.mkdirs()) {
            Log.e("setupFiles", "Failed to create root folder: " + targetRootFolder.getAbsolutePath());
            return false;
        }

        if (!targetRootFolder.isDirectory() || !targetRootFolder.canWrite()) {
            Log.e("setupFiles", "Root folder is not writable: " + targetRootFolder.getAbsolutePath());
            return false;
        }

        File probeFile = new File(targetRootFolder, ".write_test");
        try (OutputStream out = new FileOutputStream(probeFile)) {
            out.write(0);
        } catch (IOException e) {
            Log.e("setupFiles", "Write probe failed for: " + targetRootFolder.getAbsolutePath(), e);
            return false;
        }

        if (probeFile.exists() && !probeFile.delete()) {
            Log.w("setupFiles", "Failed to delete write probe: " + probeFile.getAbsolutePath());
        }

        return true;
    }

    private static final int STORAGE_PERMISSION_REQUEST_CODE = 2296;
    private static final int FILE_PICKER_REQUEST_CODE = 0;

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ → MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, STORAGE_PERMISSION_REQUEST_CODE);
            } else {
                beginSetupOrChooseDataRoot();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6–10 → request READ/WRITE at runtime
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    STORAGE_PERMISSION_REQUEST_CODE);
        } else {
            // Below Android 6 → permissions granted at install time
            beginSetupOrChooseDataRoot();
        }
    }

    public void checkAndSetupFiles() {
        File targetRootFolder = getTargetRootFolder();
        File assetsFolder = new File(targetRootFolder, "assets");
        File sohOtrFile = new File(targetRootFolder, "2ship.o2r");

        boolean isMissingAssets = !assetsFolder.exists() || assetsFolder.listFiles() == null || assetsFolder.listFiles().length == 0;
        boolean isMissingSohOtr = !sohOtrFile.exists();

        if (!targetRootFolder.exists() || isMissingAssets || isMissingSohOtr) {
            new AlertDialog.Builder(this)
                    .setTitle("Setup Required")
                    .setMessage("Some required files are missing. The app will create them (~30s). Press OK to begin.")
                    .setCancelable(false)
                    .setPositiveButton("OK", (dialog, which) -> {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            runOnUiThread(() -> Toast.makeText(this, "Setting up files...", Toast.LENGTH_SHORT).show());
                            setupFilesInBackground(targetRootFolder);
                        });
                    })
                    .show();
        } else {
            // No setup needed, still need to count down
            setupLatch.countDown();
        }
    }


    private void setupFilesInBackground(File targetRootFolder) {
        boolean setupFailed = false;

        if (!ensureTargetRootFolderReady(targetRootFolder)) {
            showStorageAccessFailure();
            return;
        }

        File sourceOldRoot = getExternalFilesDir(null);
        File sourceSavesDir = sourceOldRoot == null ? null : new File(sourceOldRoot, "saves"); // how to tell if there's anything to migrate

        // === Migration from old Android/data/.../files/ directory ===
        if (sourceOldRoot != null && sourceSavesDir != null && sourceSavesDir.isDirectory()) {
            Log.i("setupFiles", "Migrating old data from: " + sourceOldRoot.getAbsolutePath());

            File[] sourceFiles = sourceOldRoot.listFiles();
            if (sourceFiles != null) {
                for (File file : sourceFiles) {
                    String name = file.getName();
                    if (name.equals("assets") || name.equals("2ship.o2r") || name.equals("mm.o2r")) {
                        continue; // Skip these
                    }

                    File dest = new File(targetRootFolder, name);
                    try {
                        if (file.isDirectory()) {
                            AssetCopyUtil.copyDirectory(file, dest);
                        } else {
                            AssetCopyUtil.copyFile(file, dest);
                        }
                        Log.i("setupFiles", "Migrated: " + name);
                    } catch (IOException e) {
                        Log.e("setupFiles", "Failed to migrate: " + name, e);
                    }
                }
            }

            runOnUiThread(() -> Toast.makeText(this, "Save data migrated", Toast.LENGTH_SHORT).show());
        }

        // Ensure root folder exists
        if (!targetRootFolder.exists()) {
            if (!targetRootFolder.mkdirs()) {
                Log.e("setupFiles", "Failed to create root folder");
                showSetupFailure("Failed to create the 2S2H folder.");
                return;
            }
        }

        // Always ensure mods folder exists
        File targetModsDir = new File(targetRootFolder, "mods");
        if (!targetModsDir.exists() && !targetModsDir.mkdirs()) {
            showSetupFailure("Failed to create the mods folder.");
            return;
        }

        // Copy assets/ from internal
        File targetAssetsDir = new File(targetRootFolder, "assets");
        try {
            if (!targetAssetsDir.exists() && !targetAssetsDir.mkdirs()) {
                throw new IOException("Failed to create assets folder: " + targetAssetsDir.getAbsolutePath());
            }
            AssetCopyUtil.copyAssetsToExternal(this, "assets", targetAssetsDir.getAbsolutePath());
            runOnUiThread(() -> Toast.makeText(this, "Assets copied", Toast.LENGTH_SHORT).show());
        } catch (IOException e) {
            e.printStackTrace();
            setupFailed = true;
            runOnUiThread(() -> Toast.makeText(this, "Error copying assets", Toast.LENGTH_LONG).show());
        }

        // Copy 2ship.o2r from internal assets
        File targetOtrFile = new File(targetRootFolder, "2ship.o2r");
        try (InputStream in = getAssets().open("2ship.o2r");
             OutputStream out = new FileOutputStream(targetOtrFile)) {

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            runOnUiThread(() -> Toast.makeText(this, "2ship.o2r copied", Toast.LENGTH_SHORT).show());

        } catch (IOException e) {
            e.printStackTrace();
            setupFailed = true;
            runOnUiThread(() -> Toast.makeText(this, "Error copying 2ship.o2r", Toast.LENGTH_LONG).show());
        }

        if (setupFailed) {
            showSetupFailure("Required support files could not be copied. Please install a complete APK build.");
            return;
        }

        setupLatch.countDown();
    }

    private void showSetupFailure(String message) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("Setup Failed")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Close", (dialog, which) -> finish())
                .show());
    }




    private native void nativeHandleSelectedFile(String filePath);

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // Handle file selection
            Uri selectedFileUri = data.getData();
            String fileName = "MM.z64";

            File destinationDirectory = getTargetRootFolder();
            File destinationFile = new File(destinationDirectory, fileName);

            if (selectedFileUri != null && ensureTargetRootFolderReady(destinationDirectory)) {
                try {
                    InputStream in = getContentResolver().openInputStream(selectedFileUri);
                    OutputStream out = new FileOutputStream(destinationFile);

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }

                    in.close();
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    showSetupFailure("The selected file could not be copied into the 2S2H folder.");
                    return;
                }
            } else {
                showStorageAccessFailure();
                return;
            }

            // Now pass the path of the file in the new folder
            nativeHandleSelectedFile(destinationFile.getPath());

        } else if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            // Handle MANAGE_EXTERNAL_STORAGE result
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    beginSetupOrChooseDataRoot();
                } else {
                    Toast.makeText(this, "Storage permission is required to access files.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (hasStoragePermission()) {
                beginSetupOrChooseDataRoot();
            } else {
                showStorageAccessFailure();
            }
        }
    }

    private void showStorageAccessFailure() {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("Storage Permission Required")
                .setMessage("The app needs file access to create and update the 2S2H folder. Please grant storage access and try again.")
                .setCancelable(false)
                .setPositiveButton("Open Settings", (dialog, which) -> requestStoragePermission())
                .setNegativeButton("Close", (dialog, which) -> finish())
                .show());
    }


    public void openFilePicker() {
        // Create an Intent to open the file picker dialog
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");

        // Start the file picker dialog
        startActivityForResult(intent, 0);
    }

    public void changeDataFolderFromNative() {
        if (getDataRootOptions().size() < 2) {
            runOnUiThread(() -> Toast.makeText(this, "No alternate data folder is available.", Toast.LENGTH_LONG).show());
            return;
        }

        showDataRootChooser(true);
    }

    // Check if external storage is available and writable
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }


    public native void attachController();
    public native void detachController();
    // Native method for setting button state
    public native void setButton(int button, boolean value);
    public native void setCameraState(int axis, float value);

    // Native method for setting joystick axis value
    public native void setAxis(int axis, short value);

    private Button button1, button2, button3, button4;
    private Button buttonA, buttonB, buttonX, buttonY;
    private Button buttonDpadUp, buttonDpadDown, buttonDpadLeft, buttonDpadRight;
    private Button buttonLB, buttonRB, buttonZ, buttonStart, buttonBack;
    private Button buttonToggle;
    private FrameLayout leftJoystick;
    private ImageView leftJoystickKnob;
    private View overlayView;

    // Function to set up the controller overlay (inflate layout and initialize buttons)
    private void setupControllerOverlay() {
        // Inflate the touchcontrol_overlay layout
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        overlayView = inflater.inflate(R.layout.touchcontrol_overlay, null);

        // Set layout params for overlayView to control positioning and sizing
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        overlayView.setLayoutParams(layoutParams);
        // Add overlay view to the main layout (you may need to add it to a container like FrameLayout)
        ViewGroup view = (ViewGroup) getContentView();
        view.addView(overlayView);

        final ViewGroup buttonGroup = overlayView.findViewById(R.id.button_group);

        buttonA = overlayView.findViewById(R.id.buttonA);
        buttonB = overlayView.findViewById(R.id.buttonB);
        buttonX = overlayView.findViewById(R.id.buttonX);
        buttonY = overlayView.findViewById(R.id.buttonY);

        buttonDpadUp = overlayView.findViewById(R.id.buttonDpadUp);
        buttonDpadDown = overlayView.findViewById(R.id.buttonDpadDown);
        buttonDpadLeft = overlayView.findViewById(R.id.buttonDpadLeft);
        buttonDpadRight = overlayView.findViewById(R.id.buttonDpadRight);

        buttonLB = overlayView.findViewById(R.id.buttonLB);
        buttonRB = overlayView.findViewById(R.id.buttonRB);
        buttonZ = overlayView.findViewById(R.id.buttonZ);

        buttonStart = overlayView.findViewById(R.id.buttonStart);
        buttonBack = overlayView.findViewById(R.id.buttonBack);

        buttonToggle = overlayView.findViewById(R.id.buttonToggle);

        // Initialize joysticks and joystick knobs from the inflated layout
        leftJoystick = overlayView.findViewById(R.id.left_joystick);
        leftJoystickKnob = overlayView.findViewById(R.id.left_joystick_knob);

        FrameLayout rightScreenArea = overlayView.findViewById(R.id.right_screen_area);

        // Set OnTouchListeners for the Xbox controller buttons
        addTouchListener(buttonA, ControllerButtons.BUTTON_A); // SDL Button 0 (A)
        addTouchListener(buttonB, ControllerButtons.BUTTON_B); // SDL Button 1 (B)
        addTouchListener(buttonX, ControllerButtons.BUTTON_X); // SDL Button 2 (X)
        addTouchListener(buttonY, ControllerButtons.BUTTON_Y); // SDL Button 3 (Y)

        setupCButtons(buttonDpadUp, ControllerButtons.AXIS_RY, 1); // SDL Button 10 (D-Pad Up)
        setupCButtons(buttonDpadDown, ControllerButtons.AXIS_RY , -1); // SDL Button 11 (D-Pad Down)
        setupCButtons(buttonDpadLeft, ControllerButtons.AXIS_RX, 1); // SDL Button 12 (D-Pad Left)
        setupCButtons(buttonDpadRight, ControllerButtons.AXIS_RX, -1); // SDL Button 13 (D-Pad Right)

        addTouchListener(buttonLB, ControllerButtons.BUTTON_LB); // SDL Button 4 (LB)
        addTouchListener(buttonRB, ControllerButtons.BUTTON_RB); // SDL Button 5 (RB)
        addTouchListener(buttonZ, ControllerButtons.AXIS_RT); // SDL Button 5 (Z)

        addTouchListener(buttonStart, ControllerButtons.BUTTON_START); // SDL Button 7 (Start)
        addTouchListener(buttonBack, ControllerButtons.BUTTON_BACK); // SDL Button 6 (Back)


        // Setup joystick movement
        setupJoystick(leftJoystick, leftJoystickKnob, true); // Left joystick

        setupLookAround(rightScreenArea);

        setupToggleButton(buttonToggle,buttonGroup);

    }

    private void setupToggleButton(Button button, ViewGroup uiGroup){
        boolean isHidden = preferences.getBoolean("controlsVisible", false); // Default to 'false' (visible)
        uiGroup.setVisibility(isHidden ? View.INVISIBLE : View.VISIBLE); // Set the initial visibility based on the saved state
        /*if(isHidden){
            detachController();
        }*/
        button.setOnClickListener(new View.OnClickListener() {
            boolean isHidden = false;
            @Override
            public void onClick(View v) {
                if (isHidden) {
                    uiGroup.setVisibility(View.VISIBLE); // Show UI elements
                    //attachController();
                } else {
                    uiGroup.setVisibility(View.INVISIBLE); // Hide UI elements
                    //detachController();
                }
                preferences.edit().putBoolean("controlsVisible", !isHidden).apply();
                isHidden = !isHidden; // Toggle state
            }
        });
    }

    // Function to set a touch listener for each button
    private void addTouchListener(Button button, int buttonNum) {
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        setButton(buttonNum, true);
                        button.setPressed(true);
                        return true;
                    case MotionEvent.ACTION_UP:
                        setButton(buttonNum, false);
                        button.setPressed(false);
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        setButton(buttonNum, false);
                        return true;
                }
                return false;
            }
        });
    }

    private void setupCButtons(Button button, int buttonNum, int direction) {
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        setAxis(buttonNum, direction<0 ? Short.MAX_VALUE : Short.MIN_VALUE);
                        button.setPressed(true);
                        return true;
                    case MotionEvent.ACTION_UP:
                        setAxis(buttonNum, (short) 0);
                        button.setPressed(false);
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        setAxis(buttonNum, (short) 0);
                        return true;
                }
                return false;
            }
        });
    }

    boolean TouchAreaEnabled = true;

    void DisableTouchArea(){
        TouchAreaEnabled = false;
    }
    void EnableTouchArea(){
        TouchAreaEnabled = true;
    }

    private void setupLookAround(FrameLayout rightScreenArea) {
        rightScreenArea.setOnTouchListener(new View.OnTouchListener() {
            private float lastX = 0;
            private float lastY = 0;
            private boolean isTouching = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Start tracking the finger's position
                        lastX = event.getX();
                        lastY = event.getY();
                        isTouching = true;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (isTouching) {
                            // Calculate the change in position (delta)
                            float deltaX = event.getX() - lastX;
                            float deltaY = event.getY() - lastY;

                            // Update the last position
                            lastX = event.getX();
                            lastY = event.getY();

                            // Increase sensitivity by using a larger multiplier
                            // Adjust these multipliers to suit your needs
                            float sensitivityMultiplier = 15; // Higher value for more sensitivity
                            float rx = (deltaX * sensitivityMultiplier);
                            float ry = (deltaY * sensitivityMultiplier);

                            // Send the mapped values to the joystick axes
                            setCameraState(0, rx); // Right stick X axis
                            setCameraState(1, ry); // Right stick Y axis
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // Stop tracking the finger's position and reset joystick input
                        isTouching = false;
                        setCameraState(0, 0.0f); // Reset right stick X axis
                        setCameraState(1, 0.0f); // Reset right stick Y axis
                        break;
                }
                return TouchAreaEnabled; // Event full handled
            }
        });
    }





    // Function to set joystick movement with reset to center when not touched
    private void setupJoystick(FrameLayout joystickLayout, ImageView joystickKnob, boolean isLeft) {
        joystickLayout.post(() -> {
            // Calculate the joystick center once, before any events
            final float joystickCenterX = joystickLayout.getWidth() / 2f;
            final float joystickCenterY = joystickLayout.getHeight() / 2f;

            joystickLayout.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                        case MotionEvent.ACTION_MOVE:
                            // Calculate the joystick movement and move the knob
                            float deltaX = event.getX() - joystickCenterX;
                            float deltaY = event.getY() - joystickCenterY;

                            // Clamp the joystick movement to prevent it from going outside the area
                            float maxRadius = joystickLayout.getWidth() / 2f - joystickKnob.getWidth() / 2f;
                            float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                            if (distance > maxRadius) {
                                float scale = maxRadius / distance;
                                deltaX *= scale;
                                deltaY *= scale;
                            }

                            joystickKnob.setX(joystickCenterX + deltaX - joystickKnob.getWidth() / 2f);
                            joystickKnob.setY(joystickCenterY + deltaY - joystickKnob.getHeight() / 2f);

                            // Send joystick values to native C code
                            short x = (short) (deltaX / maxRadius * Short.MAX_VALUE);
                            short y = (short) (deltaY / maxRadius * Short.MAX_VALUE);

                            // Send X-axis and Y-axis values
                            setAxis(isLeft ? ControllerButtons.AXIS_LX : ControllerButtons.AXIS_RX, x); // X-axis
                            setAxis(isLeft ? ControllerButtons.AXIS_LY : ControllerButtons.AXIS_RY, y); // Y-axis
                            break;

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            // Reset joystick knob to the center position (ensure it's placed correctly)
                            joystickKnob.setX(joystickCenterX - joystickKnob.getWidth() / 2f);
                            joystickKnob.setY(joystickCenterY - joystickKnob.getHeight() / 2f);

                            // Reset joystick values to 0 when released or canceled
                            setAxis(isLeft ? ControllerButtons.AXIS_LX : ControllerButtons.AXIS_RX, (short) 0); // X-axis
                            setAxis(isLeft ? ControllerButtons.AXIS_LY : ControllerButtons.AXIS_RY, (short) 0); // Y-axis
                            break;
                    }
                    return true;
                }
            });
        });



}



}
