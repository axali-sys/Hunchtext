package com.axali.hunchtext;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED
                    | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                    | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }
        showHunchButton();
        positionNearKeyboard();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo n = event.getSource();
        if (n != null && n.isEditable()) focusedNode = n;
        positionNearKeyboard();
    }

    private void showHunchButton() {
        if (overlay != null) return;
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        Button b = new Button(this);
        b.setText("✦");
        b.setTextSize(22);
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
                54, 54,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.TOP | Gravity.RIGHT;
        overlayParams.x = 68;
        overlayParams.y = 900;
        wm.addView(overlay, overlayParams);
    }

    private void positionNearKeyboard() {
        if (overlay == null || wm == null || overlayParams == null) return;
        AccessibilityWindowInfo ime = null;
        for (AccessibilityWindowInfo w : getWindows()) {
            if (w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) { ime = w; break; }
        }
        if (ime == null) return;
        Rect r = new Rect();
        ime.getBoundsInScreen(r);
        // Place the Hunch logo in the suggestion-toolbar area, immediately before Samsung's three-dot menu.
        overlayParams.gravity = Gravity.TOP | Gravity.RIGHT;
        overlayParams.x = 68;
        overlayParams.y = Math.max(0, r.top + 4);
        try { wm.updateViewLayout(overlay, overlayParams); } catch (Exception ignored) { }
    }

    private void showHunchChoices() {
        String text = focusedNode == null || focusedNode.getText() == null ? "" : focusedNode.getText().toString().trim();
        if (text.isEmpty()) { Toast.makeText(this, "Type something first, then tap HunchText.", Toast.LENGTH_SHORT).show(); return; }
        final String[] choices = {"❤️ Warm", "😊 Friendly", "🌿 Gentle", "💬 Direct", "🔥 Confident"};
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        for (String choice : choices) {
            Button b = new Button(this);
            b.setText(choice);
            b.setAllCaps(false);
            b.setOnClickListener(v -> applyHunch(text, ((Button)v).getText().toString()));
            panel.addView(b, new LinearLayout.LayoutParams(0, 62, 1));
        }
        if (overlay != null) wm.removeView(overlay);
        overlay = panel;
        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, 70,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.TOP;
        overlayParams.y = findKeyboardTop();
        wm.addView(panel, overlayParams);
    }

    private int findKeyboardTop() {
        for (AccessibilityWindowInfo w : getWindows()) {
            if (w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                Rect r = new Rect(); w.getBoundsInScreen(r); return Math.max(0, r.top + 4);
            }
        }
        return 900;
    }

    private void applyHunch(String text, String mode) {
        String out = text;
        if (mode.contains("Warm")) out = "I really mean this: " + text + " ❤️";
        else if (mode.contains("Friendly")) out = "Just wanted to say: " + text + " 😊";
        else if (mode.contains("Gentle")) out = "I just wanted to share that " + text + " 🌿";
        else if (mode.contains("Confident")) out = text + " — I mean it. 🔥";
        AccessibilityNodeInfo n = focusedNode;
        if (n != null) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, out);
            n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        } else Toast.makeText(this, out, Toast.LENGTH_LONG).show();
        if (overlay != null) { wm.removeView(overlay); overlay = null; overlayParams = null; showHunchButton(); positionNearKeyboard(); }
    }

    @Override public void onInterrupt() {}
    @Override public void onDestroy() { if (overlay != null && wm != null) wm.removeView(overlay); overlay = null; super.onDestroy(); }
}
