package com.example.sdlpopoverlay;

import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NativeSDLPoPActivity extends SDLActivity {
    private static final String ASSET_ROOT = "sdlpop";
    private static final String ASSET_MARKER = "native-assets-v3";

    private String gameRoot;
    private DepthOverlayView depthOverlayView;
    private NativeControlsView controlsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        File root = new File(getFilesDir(), ASSET_ROOT);
        gameRoot = root.getAbsolutePath();
        installAssets(root);
        super.onCreate(savedInstanceState);
        enterImmersiveMode();
        installDepthOverlay();
        installControlsOverlay();
    }

    @Override
    protected void onPause() {
        releaseTouchControls();
        if (depthOverlayView != null) {
            depthOverlayView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (depthOverlayView != null) {
            depthOverlayView.onResume();
        }
        enterImmersiveMode();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
        } else {
            releaseTouchControls();
        }
    }

    @Override
    protected void onStop() {
        releaseTouchControls();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        releaseTouchControls();
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (controlsView != null) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_CANCEL
                    || action == MotionEvent.ACTION_OUTSIDE
                    || action == MotionEvent.ACTION_UP) {
                controlsView.releaseAllKeys();
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isNoisyNavigationEvent(event)) {
            releaseTouchControls();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (isNoisyNavigationSource(event.getSource())) {
            releaseTouchControls();
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    protected String[] getLibraries() {
        return new String[]{
                "SDL2",
                "SDL2_image",
                "main"
        };
    }

    @Override
    protected String[] getArguments() {
        return new String[]{gameRoot};
    }

    private void releaseTouchControls() {
        if (controlsView != null) {
            controlsView.releaseAllKeys();
        } else {
            SDLActivity.onNativeKeyboardFocusLost();
        }
    }

    private static boolean isNoisyNavigationEvent(KeyEvent event) {
        if (!isTouchControlKey(event.getKeyCode())) {
            return false;
        }
        return isNoisyNavigationSource(event.getSource());
    }

    private static boolean isTouchControlKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                return true;
            default:
                return false;
        }
    }

    private static boolean isNoisyNavigationSource(int source) {
        return (source & InputDevice.SOURCE_CLASS_JOYSTICK) != 0
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void installDepthOverlay() {
        if (mLayout == null) {
            return;
        }

        depthOverlayView = new DepthOverlayView(this);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        mLayout.addView(depthOverlayView, params);
    }

    private void installControlsOverlay() {
        if (mLayout == null) {
            return;
        }

        controlsView = new NativeControlsView(this, depthOverlayView);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        mLayout.addView(controlsView, params);
    }

    private void installAssets(File root) {
        File marker = new File(root, ".asset-version");
        if (marker.isFile() && ASSET_MARKER.equals(readMarker(marker))) {
            return;
        }

        deleteRecursively(root);
        if (!root.mkdirs() && !root.isDirectory()) {
            throw new IllegalStateException("Unable to create game data directory: " + root);
        }

        try {
            copyAssetTree(getAssets(), ASSET_ROOT, root);
            writeMarker(marker);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to install SDLPoP assets", e);
        }
    }

    private void copyAssetTree(AssetManager assets, String assetPath, File outputPath) throws IOException {
        String[] children = assets.list(assetPath);
        if (children == null || children.length == 0) {
            copyAssetFile(assets, assetPath, outputPath);
            return;
        }

        if (!outputPath.mkdirs() && !outputPath.isDirectory()) {
            throw new IOException("Unable to create directory: " + outputPath);
        }

        for (String child : children) {
            copyAssetTree(assets, assetPath + "/" + child, new File(outputPath, child));
        }
    }

    private void copyAssetFile(AssetManager assets, String assetPath, File outputPath) throws IOException {
        File parent = outputPath.getParentFile();
        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Unable to create directory: " + parent);
        }

        try (InputStream input = assets.open(assetPath);
             OutputStream output = new FileOutputStream(outputPath)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private void writeMarker(File marker) throws IOException {
        try (OutputStream output = new FileOutputStream(marker)) {
            output.write(ASSET_MARKER.getBytes("UTF-8"));
        }
    }

    private String readMarker(File marker) {
        try (InputStream input = getContentResolver().openInputStream(android.net.Uri.fromFile(marker))) {
            if (input == null) {
                return "";
            }
            byte[] buffer = new byte[64];
            int read = input.read(buffer);
            return read > 0 ? new String(buffer, 0, read, "UTF-8") : "";
        } catch (IOException ignored) {
            return "";
        }
    }

    private void deleteRecursively(File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete() && file.exists()) {
            throw new IllegalStateException("Unable to delete stale asset: " + file);
        }
    }

    private static final class NativeControlsView extends View {
        private static final int NO_KEY = 0;
        private static final int CONTROL_OVERLAY_TOGGLE = -100;
        private static final int ICON_LEFT = 1;
        private static final int ICON_RIGHT = 2;
        private static final int ICON_UP = 3;
        private static final int ICON_DOWN = 4;
        private static final int ICON_ACTION = 5;
        private static final int ICON_ENTER = 6;
        private static final int ICON_PAUSE = 7;
        private static final int ICON_OVERLAY = 8;
        private static final int[] SYNTHETIC_KEY_CODES = {
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_SHIFT_LEFT,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_ESCAPE
        };

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<ControlButton> buttons = new ArrayList<>();
        private final Set<Integer> activeKeys = new HashSet<>();
        private final SparseBooleanArray overlayPointers = new SparseBooleanArray();
        private final Path iconPath = new Path();
        private final RectF scratch = new RectF();
        private final DepthOverlayView depthOverlayView;

        private float density;

        NativeControlsView(NativeSDLPoPActivity context, DepthOverlayView depthOverlayView) {
            super(context);
            this.depthOverlayView = depthOverlayView;
            density = getResources().getDisplayMetrics().density;

            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(0x5C101820);

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1.6f));
            strokePaint.setColor(0xB8FFFFFF);

            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeCap(Paint.Cap.ROUND);
            iconPaint.setStrokeJoin(Paint.Join.ROUND);
            iconPaint.setStrokeWidth(dp(3.0f));
            iconPaint.setColor(0xEFFFFFFF);

            setFocusable(false);
            setFocusableInTouchMode(false);
            setWillNotDraw(false);
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            layoutButtons(width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            for (ControlButton button : buttons) {
                drawButton(canvas, button);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            int pointerIndex = event.getActionIndex();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    releaseAllKeys();
                    handlePointerDown(event, pointerIndex);
                    reconcileKeys(event, -1);
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    handlePointerDown(event, pointerIndex);
                    reconcileKeys(event, -1);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    reconcileKeys(event, -1);
                    return true;
                case MotionEvent.ACTION_UP:
                    releaseAllKeys();
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    reconcileKeys(event, pointerIndex);
                    overlayPointers.delete(event.getPointerId(pointerIndex));
                    invalidate();
                    return true;
                case MotionEvent.ACTION_OUTSIDE:
                case MotionEvent.ACTION_CANCEL:
                    releaseAllKeys();
                    return true;
                default:
                    return true;
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            releaseAllKeys();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
            if (!gainFocus) {
                releaseAllKeys();
            }
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            if (visibility != VISIBLE) {
                releaseAllKeys();
            }
            super.onWindowVisibilityChanged(visibility);
        }

        void releaseAllKeys() {
            for (int keyCode : SYNTHETIC_KEY_CODES) {
                SDLActivity.onNativeKeyUp(keyCode);
            }
            SDLActivity.onNativeKeyboardFocusLost();
            activeKeys.clear();
            overlayPointers.clear();
            invalidate();
        }

        private void layoutButtons(int width, int height) {
            buttons.clear();
            if (width <= 0 || height <= 0) {
                return;
            }

            float minSide = Math.min(width, height);
            float radius = clamp(minSide * 0.078f, dp(36.0f), dp(54.0f));
            float smallRadius = radius * 0.66f;
            float margin = clamp(minSide * 0.035f, dp(14.0f), dp(28.0f));
            float bottomLimit = height - margin;

            float dpadCenterX = margin + radius * 2.45f;
            float dpadOffset = radius * 1.78f;
            float dpadCenterY = bottomLimit - radius - dpadOffset;

            addButton(dpadCenterX - dpadOffset, dpadCenterY, radius,
                    KeyEvent.KEYCODE_DPAD_LEFT, ICON_LEFT);
            addButton(dpadCenterX + dpadOffset, dpadCenterY, radius,
                    KeyEvent.KEYCODE_DPAD_RIGHT, ICON_RIGHT);
            addButton(dpadCenterX, dpadCenterY - dpadOffset, radius,
                    KeyEvent.KEYCODE_DPAD_UP, ICON_UP);
            addButton(dpadCenterX, dpadCenterY + dpadOffset, radius,
                    KeyEvent.KEYCODE_DPAD_DOWN, ICON_DOWN);

            float actionX = width - margin - radius * 1.25f;
            float actionRadius = radius * 1.08f;
            float actionY = bottomLimit - actionRadius;
            addButton(actionX, actionY, actionRadius,
                    KeyEvent.KEYCODE_SHIFT_LEFT, ICON_ACTION);
            addButton(actionX - radius * 2.05f, actionY - radius * 1.46f, radius * 0.92f,
                    KeyEvent.KEYCODE_DPAD_UP, ICON_UP);
            addButton(actionX - radius * 2.25f, actionY + radius * 0.12f, smallRadius,
                    KeyEvent.KEYCODE_ENTER, ICON_ENTER);

            addButton(width - margin - smallRadius, margin + smallRadius, smallRadius,
                    KeyEvent.KEYCODE_ESCAPE, ICON_PAUSE);
            addButton(margin + smallRadius, margin + smallRadius, smallRadius,
                    CONTROL_OVERLAY_TOGGLE, ICON_OVERLAY);

            invalidate();
        }

        private void addButton(float centerX, float centerY, float radius, int keyCode, int icon) {
            buttons.add(new ControlButton(centerX, centerY, radius, keyCode, icon));
        }

        private void handlePointerDown(MotionEvent event, int pointerIndex) {
            int pointerId = event.getPointerId(pointerIndex);
            int controlCode = hitControl(event.getX(pointerIndex), event.getY(pointerIndex));

            if (controlCode == CONTROL_OVERLAY_TOGGLE) {
                overlayPointers.put(pointerId, true);
                if (depthOverlayView != null) {
                    depthOverlayView.cycleMode();
                }
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                return;
            }

            overlayPointers.delete(pointerId);
            if (controlCode > 0) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
        }

        private void reconcileKeys(MotionEvent event, int excludedPointerIndex) {
            Set<Integer> desiredKeys = new HashSet<>();
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (i == excludedPointerIndex) {
                    continue;
                }
                int pointerId = event.getPointerId(i);
                if (overlayPointers.get(pointerId, false)) {
                    continue;
                }
                int controlCode = hitControl(event.getX(i), event.getY(i));
                if (controlCode > 0) {
                    desiredKeys.add(controlCode);
                }
            }

            Set<Integer> keysToRelease = new HashSet<>(activeKeys);
            keysToRelease.removeAll(desiredKeys);
            for (Integer keyCode : keysToRelease) {
                SDLActivity.onNativeKeyUp(keyCode);
                activeKeys.remove(keyCode);
            }

            for (Integer keyCode : desiredKeys) {
                if (!activeKeys.contains(keyCode)) {
                    SDLActivity.onNativeKeyDown(keyCode);
                    activeKeys.add(keyCode);
                }
            }
            invalidate();
        }

        private int hitControl(float x, float y) {
            for (int i = buttons.size() - 1; i >= 0; i--) {
                ControlButton button = buttons.get(i);
                float dx = x - button.centerX;
                float dy = y - button.centerY;
                float hitRadius = button.radius * 1.04f;
                if (dx * dx + dy * dy <= hitRadius * hitRadius) {
                    return button.keyCode;
                }
            }
            return NO_KEY;
        }

        private void drawButton(Canvas canvas, ControlButton button) {
            boolean held = button.keyCode == CONTROL_OVERLAY_TOGGLE
                    ? depthOverlayView != null && depthOverlayView.getMode() != DepthOverlayView.MODE_OFF
                    : activeKeys.contains(button.keyCode);
            fillPaint.setColor(held ? 0x842F6FED : 0x5C101820);
            strokePaint.setColor(held ? 0xFFFFFFFF : 0xB8FFFFFF);

            canvas.drawCircle(button.centerX, button.centerY, button.radius, fillPaint);
            canvas.drawCircle(button.centerX, button.centerY, button.radius, strokePaint);

            switch (button.icon) {
                case ICON_LEFT:
                    drawArrow(canvas, button, 180.0f);
                    break;
                case ICON_RIGHT:
                    drawArrow(canvas, button, 0.0f);
                    break;
                case ICON_UP:
                    drawArrow(canvas, button, -90.0f);
                    break;
                case ICON_DOWN:
                    drawArrow(canvas, button, 90.0f);
                    break;
                case ICON_ACTION:
                    drawActionIcon(canvas, button);
                    break;
                case ICON_ENTER:
                    drawEnterIcon(canvas, button);
                    break;
                case ICON_PAUSE:
                    drawPauseIcon(canvas, button);
                    break;
                case ICON_OVERLAY:
                    drawOverlayIcon(canvas, button);
                    break;
                default:
                    break;
            }
        }

        private void drawArrow(Canvas canvas, ControlButton button, float degrees) {
            canvas.save();
            canvas.rotate(degrees, button.centerX, button.centerY);
            float size = button.radius * 0.38f;
            iconPath.reset();
            iconPath.moveTo(button.centerX - size * 0.72f, button.centerY - size);
            iconPath.lineTo(button.centerX + size * 0.72f, button.centerY);
            iconPath.lineTo(button.centerX - size * 0.72f, button.centerY + size);
            canvas.drawPath(iconPath, iconPaint);
            canvas.drawLine(button.centerX - size * 0.45f, button.centerY,
                    button.centerX + size * 0.95f, button.centerY, iconPaint);
            canvas.restore();
        }

        private void drawActionIcon(Canvas canvas, ControlButton button) {
            float size = button.radius * 0.44f;
            iconPath.reset();
            iconPath.moveTo(button.centerX, button.centerY - size);
            iconPath.lineTo(button.centerX + size, button.centerY);
            iconPath.lineTo(button.centerX, button.centerY + size);
            iconPath.lineTo(button.centerX - size, button.centerY);
            iconPath.close();
            canvas.drawPath(iconPath, iconPaint);
            canvas.drawCircle(button.centerX, button.centerY, size * 0.18f, iconPaint);
        }

        private void drawEnterIcon(Canvas canvas, ControlButton button) {
            float size = button.radius * 0.42f;
            iconPath.reset();
            iconPath.moveTo(button.centerX - size, button.centerY);
            iconPath.lineTo(button.centerX - size * 0.2f, button.centerY + size * 0.75f);
            iconPath.lineTo(button.centerX + size, button.centerY - size * 0.75f);
            canvas.drawPath(iconPath, iconPaint);
        }

        private void drawPauseIcon(Canvas canvas, ControlButton button) {
            float barHeight = button.radius * 0.62f;
            float barWidth = button.radius * 0.14f;
            float gap = button.radius * 0.17f;
            iconPaint.setStyle(Paint.Style.FILL);
            scratch.set(button.centerX - gap - barWidth, button.centerY - barHeight * 0.5f,
                    button.centerX - gap, button.centerY + barHeight * 0.5f);
            canvas.drawRoundRect(scratch, barWidth, barWidth, iconPaint);
            scratch.set(button.centerX + gap, button.centerY - barHeight * 0.5f,
                    button.centerX + gap + barWidth, button.centerY + barHeight * 0.5f);
            canvas.drawRoundRect(scratch, barWidth, barWidth, iconPaint);
            iconPaint.setStyle(Paint.Style.STROKE);
        }

        private void drawOverlayIcon(Canvas canvas, ControlButton button) {
            float size = button.radius * 0.42f;
            float skew = size * 0.38f;
            iconPath.reset();
            iconPath.moveTo(button.centerX, button.centerY - size);
            iconPath.lineTo(button.centerX + size, button.centerY - skew);
            iconPath.lineTo(button.centerX, button.centerY + size * 0.15f);
            iconPath.lineTo(button.centerX - size, button.centerY - skew);
            iconPath.close();
            canvas.drawPath(iconPath, iconPaint);
            canvas.drawLine(button.centerX - size, button.centerY - skew,
                    button.centerX - size, button.centerY + size * 0.58f, iconPaint);
            canvas.drawLine(button.centerX + size, button.centerY - skew,
                    button.centerX + size, button.centerY + size * 0.58f, iconPaint);
            canvas.drawLine(button.centerX, button.centerY + size * 0.15f,
                    button.centerX, button.centerY + size, iconPaint);
            canvas.drawLine(button.centerX - size, button.centerY + size * 0.58f,
                    button.centerX, button.centerY + size, iconPaint);
            canvas.drawLine(button.centerX + size, button.centerY + size * 0.58f,
                    button.centerX, button.centerY + size, iconPaint);
        }

        private float dp(float value) {
            return value * density;
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    private static final class ControlButton {
        final float centerX;
        final float centerY;
        final float radius;
        final int keyCode;
        final int icon;

        ControlButton(float centerX, float centerY, float radius, int keyCode, int icon) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
            this.keyCode = keyCode;
            this.icon = icon;
        }
    }
}
