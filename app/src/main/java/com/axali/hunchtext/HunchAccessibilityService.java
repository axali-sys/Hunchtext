package com.axali.hunchtext;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

public class HunchAccessibilityService extends AccessibilityService {
    private WindowManager wm;
    private View overlay;
    private AccessibilityNodeInfo focusedNode;

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }
        showHunchButton();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo n = event.getSource();
        if (n != null && n.isEditable()) focusedNode = n;
    }

    private void showHunchButton() {
        if (overlay != null) return;
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        Button b = new Button(this);
        b.setText("✨ Hunch");
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setOnClickListener(v -> showHunchChoices());
        overlay = b;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, 58,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        p.y = 155;
        wm.addView(overlay, p);
    }

    private void showHunchChoices() {
        String text = focusedNode == null || focusedNode.getText() == null ? "" : focusedNode.getText().toString().trim();
        if (text.isEmpty()) { Toast.makeText(this, "Type something first, then tap Hunch.", Toast.LENGTH_SHORT).show(); return; }
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
        if (overlay != null) { wm.removeView(overlay); overlay = panel; }
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, 70,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.BOTTOM; p.y = 155; wm.addView(panel, p);
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
        if (overlay != null) { wm.removeView(overlay); overlay = null; showHunchButton(); }
    }

    @Override public void onInterrupt() {}
    @Override public void onDestroy() { if (overlay != null && wm != null) wm.removeView(overlay); overlay = null; super.onDestroy(); }
}
