package com.axali.hunchtext;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

public class HunchAccessibilityService extends AccessibilityService {
    private WindowManager wm;
    private View overlay;
    private AccessibilityNodeInfo focusedNode;
    private WindowManager.LayoutParams overlayParams;
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
        handler.postDelayed(this::positionNearSamsungToolbar, 120);
        handler.postDelayed(this::positionNearSamsungToolbar, 500);
    }

    private void showHunchButton() {
        if (overlay != null) return;

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        Button b = new Button(this);
        b.setText("✦");
        b.setTextSize(20);
        b.setTextColor(Color.WHITE);
        b.setContentDescription("HunchText");
        b.setPadding(0, 0, 0, 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(103, 80, 164));
        bg.setCornerRadius(28);
        b.setBackground(bg);

        b.setOnClickListener(v -> showHunchChoices());

        overlay = b;
        overlayParams = new WindowManager.LayoutParams(
                50,
                50,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.TOP | Gravity.LEFT;
        overlayParams.x = 0;
        overlayParams.y = 900;

        wm.addView(overlay, overlayParams);
    }

    private void positionNearSamsungToolbar() {
        if (overlay == null || wm == null || overlayParams == null) return;

        AccessibilityWindowInfo inputMethod = null;
        for (AccessibilityWindowInfo w : getWindows()) {
            if (w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                inputMethod = w;
                break;
            }
        }

        if (inputMethod == null) {
            overlayParams.x = 0;
            overlayParams.y = 900;
            safeUpdate();
            return;
        }

        Rect keyboard = new Rect();
        inputMethod.getBoundsInScreen(keyboard);

        Rect more = findMoreButton(inputMethod.getRoot());

        if (more != null && more.width() > 0 && more.height() > 0) {
            // Put Hunch immediately to the left of Samsung Keyboard's right-side
            // More/options control, aligned to the same vertical center.
            int size = 50;
            overlayParams.x = Math.max(0, more.left - size - 4);
            overlayParams.y = Math.max(0, more.centerY() - size / 2);
        } else {
            // Fallback: place it at the upper-left of the keyboard/suggestion area.
            overlayParams.x = Math.max(8, keyboard.left + 8);
            overlayParams.y = Math.max(0, keyboard.top + 4);
        }

        overlayParams.gravity = Gravity.TOP | Gravity.LEFT;
        safeUpdate();
    }

    private Rect findMoreButton(AccessibilityNodeInfo root) {
        if (root == null) return null;

        Rect best = null;
        int bestRight = -1;

        java.util.ArrayDeque<AccessibilityNodeInfo> queue = new java.util.ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.removeFirst();

            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String label = ((text == null ? "" : text.toString()) + " "
                    + (desc == null ? "" : desc.toString())).toLowerCase();

            if (label.contains("more")
                    || label.contains("more options")
                    || label.contains("additional")
                    || label.contains("options")) {
                Rect r = new Rect();
                node.getBoundsInScreen(r);
                if (r.width() > 0 && r.height() > 0 && r.right > bestRight) {
                    best = r;
                    bestRight = r.right;
                }
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }

        return best;
    }

    private void safeUpdate() {
        try {
            wm.updateViewLayout(overlay, overlayParams);
        } catch (Exception ignored) {
        }
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
            panel.addView(
                    b,
                    new LinearLayout.LayoutParams(0, 62, 1)
            );
        }

        if (overlay != null) {
            wm.removeView(overlay);
        }

        overlay = panel;
        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                70,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.TOP | Gravity.LEFT;
        overlayParams.x = 0;
        overlayParams.y = findKeyboardTop();

        wm.addView(panel, overlayParams);
    }

    private int findKeyboardTop() {
        for (AccessibilityWindowInfo w : getWindows()) {
            if (w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                Rect r = new Rect();
                w.getBoundsInScreen(r);
                return Math.max(0, r.top + 4);
            }
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

        if (overlay != null) {
            wm.removeView(overlay);
            overlay = null;
            overlayParams = null;
            showHunchButton();
            scheduleReposition();
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (overlay != null && wm != null) {
            wm.removeView(overlay);
        }
        overlay = null;
        super.onDestroy();
    }
}
