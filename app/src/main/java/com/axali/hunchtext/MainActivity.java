package com.axali.hunchtext;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    l.setPadding(32,48,32,32);

    TextView t = new TextView(this);
    t.setText("HunchText V1\\n\\nUse HunchText in BOTH ways:\\n\\n1. Samsung Keyboard: enable HunchText Accessibility integration. HunchText appears over the Samsung Keyboard toolbar and inserts the chosen expression into the current text field.\\n\\n2. HunchText Keyboard: enable and select the standalone HunchText keyboard.\\n\\nYour normal Samsung Keyboard remains available.");
    t.setTextSize(18);
    l.addView(t);

    Button samsung = new Button(this);
    samsung.setText("Add HunchText to Samsung Keyboard");
    samsung.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    l.addView(samsung);

    Button enable = new Button(this);
    enable.setText("Enable HunchText Keyboard");
    enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
    l.addView(enable);

    Button select = new Button(this);
    select.setText("Select HunchText Keyboard");
    select.setOnClickListener(v -> {
      InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
      if (imm != null) imm.showInputMethodPicker();
    });
    l.addView(select);

    setContentView(l);
  }
}
