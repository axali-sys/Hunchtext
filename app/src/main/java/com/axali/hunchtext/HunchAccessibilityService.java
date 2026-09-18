package com.axali.hunchtext;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Display;
import android.view.Gravity;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.hardware.display.DisplayManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

public class HunchAccessibilityService extends AccessibilityService {
    private static final String PREFS = "hunchtext_prefs";
    private static final String KEY_TOOLBAR_ENABLED = "hunch_toolbar_enabled";

    private WindowManager wm;
    private View legacyOverlay;
    private AccessibilityNodeInfo focusedNode;
    private WindowManager.LayoutParams legacyParams;

    private SurfaceControlViewHost hunchHost;
    private SurfaceControl hunchSurface;
    private AccessibilityWindowInfo targetWindow;
    private String keyboardPackageName;
    private boolean menuMode;
    private long menuVisibleUntil;
    private final Handler handler = new Handler();

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private boolean toolbarEnabled() {
        return prefs().getBoolean(KEY_TOOLBAR_ENABLED, false);
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes =
                    AccessibilityEvent.TYPE_VIEW_FOCUSED
                            | AccessibilityEvent.TYPE_VIEW_CLICKED
                            | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                            | AccessibilityEvent.TYPE_WINDOWS_CHANGED
                            | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                            | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }

        scheduleReposition();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo source = event.getSource();

        if (source != null && source.isEditable()) {
            focusedNode = source;
        }

        AccessibilityNodeInfo input = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (input != null && input.isEditable()) {
            focusedNode = input;
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED
                && isMoreAction(source)) {
            menuVisibleUntil = System.currentTimeMillis() + 5000L;
            showMenuHunch();
        } else if (!toolbarEnabled()
                && System.currentTimeMillis() < menuVisibleUntil
                && findKeyboardMenuWindow() != null) {
            showMenuHunch();
        } else if (toolbarEnabled()) {
            showHunchButton();
        }

        scheduleReposition();
    }

    private void scheduleReposition() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::positionHunch, 80);
        handler.postDelayed(this::positionHunch, 300);
        handler.postDelayed(this::positionHunch, 700);
    }

    private Button createHunchButton(boolean fromMenu) {
        Button b = new Button(this);
        b.setText("✦");
        b.setTextSize(20);
        b.setTextColor(Color.WHITE);
        b.setContentDescription("HunchText");
        b.setPadding(0, 0, 0, 0);
        b.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(103, 80, 164));
        bg.setCornerRadius(26);
        b.setBackground(bg);

        if (fromMenu) {
            b.setOnClickListener(v -> enableToolbarHunch());
            b.setTooltipText("Add HunchText to the keyboard toolbar");
        } else {
            b.setOnClickListener(v -> showHunchChoices());
            b.setTooltipText("HunchText");
        }
        return b;
    }

    private void enableToolbarHunch() {
        prefs().edit().putBoolean(KEY_TOOLBAR_ENABLED, true).apply();
        menuMode = false;
        menuVisibleUntil = 0L;
        releaseWindowAttachedHunch();
        removeLegacyOverlay();
        showHunchButton();
        scheduleReposition();
        Toast.makeText(
                this,
                "HunchText added. It will now appear beside the Samsung Keyboard menu.",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void showHunchButton() {
        if (!toolbarEnabled() || menuMode) return;

        if (Build.VERSION.SDK_INT >= 34) {
            showWindowAttachedHunch(false);
        } else {
            showLegacyHunch(false);
        }
    }

    private void showMenuHunch() {
        if (toolbarEnabled() || System.currentTimeMillis() >= menuVisibleUntil) return;

        menuMode = true;
        if (Build.VERSION.SDK_INT >= 34) {
            showWindowAttachedHunch(true);
        } else {
            showLegacyHunch(true);
        }
    }

    private AccessibilityWindowInfo chooseTargetWindow(boolean fromMenu) {
        if (fromMenu) {
            AccessibilityWindowInfo menu = findKeyboardMenuWindow();
            if (menu != null) return menu;
        }
        return findKeyboardWindow();
    }

    private void showWindowAttachedHunch(boolean fromMenu) {
        AccessibilityWindowInfo window = chooseTargetWindow(fromMenu);
        if (window == null) return;

        DisplayManager dm = getSystemService(DisplayManager.class);
        Display display = dm.getDisplay(window.getDisplayId());
        if (display == null) return;

        String pkg = getWindowPackage(window);
        if (pkg != null && !pkg.isEmpty()) {
            keyboardPackageName = pkg;
        }

        if (hunchHost != null && hunchSurface != null && targetWindow != null
                && targetWindow.getId() == window.getId()
                && menuMode == fromMenu) {
            return;
        }

        releaseWindowAttachedHunch();

        try {
            hunchHost = new SurfaceControlViewHost(this, display, null);
            Button button = createHunchButton(fromMenu);
            hunchHost.setView(button, 54, 54);

            SurfaceControlViewHost.SurfacePackage pkgSurface = hunchHost.getSurfacePackage();
            if (pkgSurface == null || pkgSurface.getSurfaceControl() == null) {
                releaseWindowAttachedHunch();
                return;
            }

            hunchSurface = pkgSurface.getSurfaceControl();
            targetWindow = window;
            menuMode = fromMenu;
            attachAccessibilityOverlayToWindow(window.getId(), hunchSurface);
            positionWindowAttachedHunch(window);
        } catch (Throwable t) {
            releaseWindowAttachedHunch();
            if (fromMenu) {
                showLegacyHunch(true);
            } else {
                showLegacyHunch(false);
            }
        }
    }

    private void positionHunch() {
        if (!toolbarEnabled() && !menuMode) {
            removeLegacyOverlay();
            releaseWindowAttachedHunch();
            return;
        }

        if (menuMode && System.currentTimeMillis() >= menuVisibleUntil) {
            menuMode = false;
            removeLegacyOverlay();
            releaseWindowAttachedHunch();
            return;
        }

        boolean wantMenu = menuMode && !toolbarEnabled();
        AccessibilityWindowInfo desired = chooseTargetWindow(wantMenu);
        if (desired == null) {
            if (!wantMenu && toolbarEnabled()) {
                showHunchButton();
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= 34) {
            if (hunchHost == null || hunchSurface == null || targetWindow == null
                    || targetWindow.getId() != desired.getId()) {
                showWindowAttachedHunch(wantMenu);
                return;
            }
            positionWindowAttachedHunch(desired);
        } else {
            positionLegacyHunch(desired);
        }
    }

    private void positionWindowAttachedHunch(AccessibilityWindowInfo window) {
        if (hunchSurface == null) return;

        Rect bounds = new Rect();
        window.getBoundsInScreen(bounds);

        int x;
        int y;

        if (menuMode && !toolbarEnabled()) {
            // The menu-phase button is placed inside Samsung's detected popup/window,
            // so the user sees HunchText as a selectable menu item before enabling it.
            x = Math.max(8, bounds.width() - 62);
            y = Math.max(8, 8);
        } else {
            Rect anchor = findMoreButtonInWindow(window);
            if (anchor != null) {
                Rect anchorWindow = new Rect();
                AccessibilityNodeInfo anchorNode = findMoreNode(window.getRoot());
                if (anchorNode != null) {
                    anchorNode.getBoundsInWindow(anchorWindow);
                }

                if (anchorWindow.width() > 0 && anchorWindow.height() > 0) {
                    x = Math.max(4, anchorWindow.left - 58);
                    y = Math.max(2, anchorWindow.centerY() - 27);
                } else {
                    x = Math.max(4, anchor.left - bounds.left - 58);
                    y = Math.max(2, anchor.centerY() - bounds.top - 27);
                }
            } else {
                x = Math.max(4, bounds.width() - 116);
                y = 4;
            }
        }

        new SurfaceControl.Transaction()
                .setPosition(hunchSurface, x, y)
                .setLayer(hunchSurface, 1000)
                .apply();
    }

    private Rect findMoreButtonInWindow(AccessibilityWindowInfo window) {
        AccessibilityNodeInfo node = findMoreNode(window.getRoot());
        if (node == null) return null;

        Rect r = new Rect();
        node.getBoundsInScreen(r);
        return r;
    }

    private AccessibilityNodeInfo findMoreNode(AccessibilityNodeInfo root) {
        if (root == null) return null;

        AccessibilityNodeInfo best = null;
        int bestRight = -1;

        java.util.ArrayDeque<AccessibilityNodeInfo> queue = new java.util.ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.removeFirst();

            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String label = ((text == null ? "" : text.toString()) + " "
                    + (desc == null ? "" : desc.toString())).toLowerCase();

            boolean namedMore = label.contains("more")
                    || label.contains("more options")
                    || label.contains("additional")
                    || label.contains("options");

            Rect r = new Rect();
            node.getBoundsInScreen(r);

            if (namedMore && node.isVisibleToUser()
                    && r.width() > 0 && r.height() > 0
                    && r.right > bestRight) {
                best = node;
                bestRight = r.right;
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }

        return best;
    }

    private boolean isMoreAction(AccessibilityNodeInfo node) {
        if (node == null) return false;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String label = ((text == null ? "" : text.toString()) + " "
                + (desc == null ? "" : desc.toString())).toLowerCase();

        return label.contains("more")
                || label.contains("more options")
                || label.contains("additional")
                || label.contains("options");
    }

    private AccessibilityWindowInfo findKeyboardWindow() {
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                if (w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    String pkg = getWindowPackage(w);
                    if (pkg != null && !pkg.isEmpty()) {
                        keyboardPackageName = pkg;
                    }
                    return w;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private AccessibilityWindowInfo findKeyboardMenuWindow() {
        try {
            AccessibilityWindowInfo keyboard = findKeyboardWindow();
            String keyboardPkg = keyboardPackageName != null
                    ? keyboardPackageName
                    : getWindowPackage(keyboard);

            if (keyboardPkg == null || keyboardPkg.isEmpty()) return null;

            for (AccessibilityWindowInfo w : getWindows()) {
                if (w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue;

                String pkg = getWindowPackage(w);
                if (!keyboardPkg.equals(pkg)) continue;

                Rect r = new Rect();
                w.getBoundsInScreen(r);
                if (r.width() <= 0 || r.height() <= 0) continue;

                AccessibilityNodeInfo root = w.getRoot();
                if (containsMenuHints(root)) {
                    return w;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String getWindowPackage(AccessibilityWindowInfo window) {
        if (window == null) return null;

        AccessibilityNodeInfo root = window.getRoot();
        if (root == null) return null;

        CharSequence pkg = root.getPackageName();
        return pkg == null ? null : pkg.toString();
    }

    private boolean containsMenuHints(AccessibilityNodeInfo root) {
        if (root == null) return false;

        java.util.ArrayDeque<AccessibilityNodeInfo> queue = new java.util.ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.removeFirst();
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();

            String label = ((text == null ? "" : text.toString()) + " "
                    + (desc == null ? "" : desc.toString())).toLowerCase();

            if (label.contains("clipboard")
                    || label.contains("keyboard settings")
                    || label.contains("settings")
                    || label.contains("toolbar")
                    || label.contains("emoji")
                    || label.contains("handwriting")
                    || label.contains("translate")
                    || label.contains("voice input")
                    || label.contains("modes")
                    || label.contains("more options")) {
                return true;
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }

        return false;
    }

    private void showLegacyHunch(boolean fromMenu) {
        if (legacyOverlay != null) return;

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Button b = createHunchButton(fromMenu);

        legacyOverlay = b;
        legacyParams = new WindowManager.LayoutParams(
                54,
                54,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        legacyParams.gravity = Gravity.TOP | Gravity.LEFT;
        legacyParams.x = 8;
        legacyParams.y = 900;

        try {
            wm.addView(legacyOverlay, legacyParams);
            menuMode = fromMenu;
        } catch (Exception ignored) {
            legacyOverlay = null;
        }
    }

    private void positionLegacyHunch(AccessibilityWindowInfo window) {
        if (legacyOverlay == null || wm == null || legacyParams == null || window == null) return;

        Rect bounds = new Rect();
        window.getBoundsInScreen(bounds);

        if (menuMode && !toolbarEnabled()) {
            legacyParams.x = Math.max(8, bounds.right - 62);
            legacyParams.y = Math.max(8, bounds.top + 8);
        } else {
            Rect anchor = findMoreButtonInWindow(window);

            if (anchor != null) {
                legacyParams.x = Math.max(4, anchor.left - 58);
                legacyParams.y = Math.max(0, anchor.centerY() - 27);
            } else {
                legacyParams.x = Math.max(8, bounds.right - 116);
                legacyParams.y = Math.max(0, bounds.top + 4);
            }
        }

        try {
            wm.updateViewLayout(legacyOverlay, legacyParams);
        } catch (Exception ignored) {
        }
    }

    private void removeLegacyOverlay() {
        if (legacyOverlay != null && wm != null) {
            try {
                wm.removeView(legacyOverlay);
            } catch (Exception ignored) {
            }
        }
        legacyOverlay = null;
        legacyParams = null;
    }

    private void releaseWindowAttachedHunch() {
        if (hunchSurface != null) {
            try {
                new SurfaceControl.Transaction()
                        .reparent(hunchSurface, null)
                        .apply();
            } catch (Exception ignored) {
            }
        }

        if (hunchHost != null) {
            try {
                hunchHost.release();
            } catch (Exception ignored) {
            }
        }

        hunchSurface = null;
        hunchHost = null;
        targetWindow = null;
    }

    private void showHunchChoices() {
        AccessibilityNodeInfo input = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (input != null && input.isEditable()) {
            focusedNode = input;
        }

        String text = focusedNode == null || focusedNode.getText() == null
                ? ""
                : focusedNode.getText().toString().trim();

        if (text.isEmpty()) {
            Toast.makeText(
                    this,
                    "Type something first, then tap HunchText.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        final String[] choices = {
                "❤️ Warm",
                "😊 Friendly",
                "🌿 Gentle",
                "💬 Direct",
                "🔥 Confident"
        };

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);

        for (String choice : choices) {
            Button b = new Button(this);
            b.setText(choice);
            b.setAllCaps(false);
            b.setOnClickListener(
                    v -> applyHunch(text, ((Button) v).getText().toString())
            );
            panel.addView(b, new LinearLayout.LayoutParams(0, 62, 1));
        }

        if (Build.VERSION.SDK_INT >= 34) {
            releaseWindowAttachedHunch();

            AccessibilityWindowInfo window = findKeyboardWindow();
            if (window == null) return;

            DisplayManager dm = getSystemService(DisplayManager.class);
            Display display = dm.getDisplay(window.getDisplayId());
            if (display == null) return;

            try {
                hunchHost = new SurfaceControlViewHost(this, display, null);
                hunchHost.setView(
                        panel,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        70
                );

                SurfaceControlViewHost.SurfacePackage pkg =
                        hunchHost.getSurfacePackage();

                if (pkg == null || pkg.getSurfaceControl() == null) return;

                hunchSurface = pkg.getSurfaceControl();
                targetWindow = window;
                attachAccessibilityOverlayToWindow(window.getId(), hunchSurface);

                new SurfaceControl.Transaction()
                        .setPosition(hunchSurface, 0, 4)
                        .setLayer(hunchSurface, 1000)
                        .apply();
            } catch (Throwable t) {
                releaseWindowAttachedHunch();
            }
        } else {
            removeLegacyOverlay();

            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            legacyOverlay = panel;
            legacyParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    70,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            legacyParams.gravity = Gravity.TOP | Gravity.LEFT;
            legacyParams.y = findKeyboardTop();

            try {
                wm.addView(panel, legacyParams);
            } catch (Exception ignored) {
            }
        }
    }

    private int findKeyboardTop() {
        AccessibilityWindowInfo w = findKeyboardWindow();
        if (w != null) {
            Rect r = new Rect();
            w.getBoundsInScreen(r);
            return Math.max(0, r.top + 4);
        }
        return 900;
    }

    private void applyHunch(String text, String mode) {
        String out = text;

        if (mode.contains("Warm")) {
            out = "I really mean this: " + text + " ❤️";
        } else if (mode.contains("Friendly")) {
            out = "Just wanted to say: " + text + " 😊";
        } else if (mode.contains("Gentle")) {
            out = "I just wanted to share that " + text + " 🌿";
        } else if (mode.contains("Confident")) {
            out = text + " — I mean it. 🔥";
        }

        AccessibilityNodeInfo n = focusedNode;

        if (n != null) {
            Bundle args = new Bundle();
            args.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    out
            );
            n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        } else {
            Toast.makeText(this, out, Toast.LENGTH_LONG).show();
        }

        releaseWindowAttachedHunch();
        removeLegacyOverlay();
        menuMode = false;

        showHunchButton();
        scheduleReposition();
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releaseWindowAttachedHunch();
        removeLegacyOverlay();
        super.onDestroy();
    }
}
