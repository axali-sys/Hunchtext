package com.axali.hunchtext;
import android.app.*;import android.os.*;import android.content.*;import android.view.*;import android.view.inputmethod.*;import android.widget.*;
public class MainActivity extends Activity {
  public void onCreate(Bundle b){
    super.onCreate(b);
    LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(32,48,32,32);
    TextView t=new TextView(this);
    t.setText("HunchText V1\n\nAI keyboard for richer expression.\n\n1. Enable HunchText.\n2. Select HunchText as your keyboard.\n3. Tap ✦ HunchText in the keyboard toolbar.");
    t.setTextSize(18); l.addView(t);
    Button enable=new Button(this); enable.setText("Enable HunchText Keyboard");
    enable.setOnClickListener(v -> startActivity(new Intent("android.settings.INPUT_METHOD_SETTINGS"))); l.addView(enable);
    Button select=new Button(this); select.setText("Select HunchText Keyboard");
    select.setOnClickListener(v -> { InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE); if(imm!=null) imm.showInputMethodPicker(); }); l.addView(select);
    setContentView(l);
  }
}
