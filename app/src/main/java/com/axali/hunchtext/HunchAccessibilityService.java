package com.axali.hunchtext;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
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
    private WindowManager wm;
    private View legacyOverlay;
    private AccessibilityNodeInfo focusedNode;
    private WindowManager.LayoutParams legacyParams;

    private SurfaceControlViewHost hunchHost;
    private SurfaceControl hunchSurface;
    private AccessibilityWindowInfo keyboardWindow;
    private final Handler handler = new Handler();

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes =
                    AccessibilityEvent.TYPE_VIEW_FOCUSED
                            | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                            | AccessibilityEvent.TYPE_WINDOWS_CHANGED
                            | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }

        showHunchButton();
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

        scheduleReposition();
    }

    private void scheduleReposition() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::positionHunch, 100);
        handler.postDelayed(this::positionHunch, 350);
        handler.postDelayed(this::positionHunch, 800);
    }

    private void showHunchButton() {
        if (Build.VERSION.SDK_INT >= 34) {
            showWindowAttachedHunch();
        } else {
            showLegacyHunch();
        }
    }

    private Button createHunchButton() {
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

        b.setOnClickListener(v -> showHunchChoices());
        return b;
    }

    private void showWindowAttachedHunch() {
        if (hunchHost != null) return;

        AccessibilityWindowInfo window = findKeyboardWindow();
        if (window == null) return;

        DisplayManager dm = getSystemService(DisplayManager.class);
        Display display = dm.getDisplay(window.getDisplayId());
        if (display == null) return;

        try {
            hunchHost = new SurfaceControlViewHost(this, display, null);
            Button button = createHunchButton();
            hunchHost.setView(button, 54, 54);

            SurfaceControlViewHost.SurfacePackage pkg = hunchHost.getSurfacePackage();
            if (pkg == null || pkg.getSurfaceControl() == null) {
                releaseWindowAttachedHunch();
                return;
            }

            hunchSurface = pkg.getSurfaceControl();
            attachAccessibilityOverlayToWindow(window.getId(), hunchSurface);
            keyboardWindow = window;
            positionWindowAttachedHunch(window);
        } catch (Throwable t) {
            releaseWindowAttachedHunch();
            showLegacyHunch();
        }
    }

    private void positionHunch() {
        AccessibilityWindowInfo window = findKeyboardWindow();

        if (Build.VERSION.SDK_INT >= 34) {
            if (window == null) {
                releaseWindowAttachedHunch();
                return;
            }

            if (hunchHost == null || hunchSurface == null || keyboardWindow == null
                    || keyboardWindow.getId() != window.getId()) {
                releaseWindowAttachedHunch();
                showWindowAttachedHunch();
                return;
            }

            positionWindowAttachedHunch(window);
        } else {
            positionLegacyHunch(window);
        }
    }

    private void positionWindowAttachedHunch(AccessibilityWindowInfo window) {
        if (hunchSurface == null) return;

        Rect keyboard = new Rect();
        window.getBoundsInScreen(keyboard);

        Rect anchor = findMoreButtonInWindow(window);

        int x;
        int y;

        if (anchor != null) {
            // Window-attached overlays use window coordinates.
            Rect anchorWindow = new Rect();
            AccessibilityNodeInfo anchorNode = findMoreNode(window.getRoot());
            if (anchorNode != null) {
                anchorNode.getBoundsInWindow(anchorWindow);
            }

            if (anchorWindow.width() > 0 && anchorWindow.height() > 0) {
                x = Math.max(4, anchorWindow.left - 58);
                y = Math.max(2, anchorWindow.centerY() - 27);
            } else {
                x = Math.max(4, anchor.left - keyboard.left - 58);
                y = Math.max(2, anchor.centerY() - keyboard.top - 27);
            }
        } else {
            // Samsung may not expose the ⋮ button through accessibility.
            // Keep Hunch in the suggestion-toolbar area near the right edge.
            x = Math.max(4, keyboard.width() - 116);
            y = 4;
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

    private AccessibilityWindowInfo findKeyboardWindow() {
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                if (w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    return w;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void showLegacyHunch() {
        if (legacyOverlay != null || wm == null && getSystemService(WINDOW_SERVICE) == null) return;

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Button b = createHunchButton();

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
        } catch (Exception ignored) {
            legacyOverlay = null;
        }
    }

    private void positionLegacyHunch(AccessibilityWindowInfo window) {
        if (legacyOverlay == null || wm == null || legacyParams == null || window == null) return;

        Rect keyboard = new Rect();
        window.getBoundsInScreen(keyboard);

        Rect anchor = findMoreButtonInWindow(window);

        if (anchor != null) {
            legacyParams.x = Math.max(4, anchor.left - 58);
            legacyParams.y = Math.max(0, anchor.centerY() - 27);
        } else {
            legacyParams.x = Math.max(8, keyboard.right - 116);
            legacyParams.y = Math.max(0, keyboard.top + 4);
        }

        try {
            wm.updateViewLayout(legacyOverlay, legacyParams);
        } catch (Exception ignored) {
        }
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
        keyboardWindow = null;
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
                keyboardWindow = window;
                attachAccessibilityOverlayToWindow(window.getId(), hunchSurface);

                new SurfaceControl.Transaction()
                        .setPosition(hunchSurface, 0, 4)
                        .setLayer(hunchSurface, 1000)
                        .apply();
            } catch (Throwable t) {
                releaseWindowAttachedHunch();
            }
        } else {
            if (legacyOverlay != null && wm != null) {
                wm.removeView(legacyOverlay);
                legacyOverlay = null;
            }

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

            wm.addView(panel, legacyParams);
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

        if (legacyOverlay != null && wm != null) {
            try {
                wm.removeView(legacyOverlay);
            } catch (Exception ignored) {
            }
            legacyOverlay = null;
            legacyParams = null;
        }

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

        if (legacyOverlay != null && wm != null) {
            try {
                wm.removeView(legacyOverlay);
            } catch (Exception ignored) {
            }
        }

        legacyOverlay = null;
        super.onDestroy();
    }
}
