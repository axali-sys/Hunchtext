package com.axali.hunchtext;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.view.inputmethod.InputConnection;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class HunchTextImeService extends InputMethodService {
    LinearLayout root;
    EditText preview;
    TextView prediction;
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Handler main = new Handler(Looper.getMainLooper());
    static final String AI_URL = "https://hunchtext-ai.vercel.app/api/hunch";

    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(6,6,6,6);

        preview = new EditText(this);
        preview.setHint("Type here — Hunch AI will predict and enrich");
        preview.setSingleLine(false);
        root.addView(preview, new LinearLayout.LayoutParams(-1,140));

        prediction = new TextView(this);
        prediction.setText("Next: AI prediction loading when you type");
        prediction.setPadding(10,8,10,8);
        root.addView(prediction, new LinearLayout.LayoutParams(-1,70));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        Button hunchText = new Button(this);
        hunchText.setText("✦ HunchText");
        hunchText.setOnClickListener(v -> transform("✨ Hunch"));
        toolbar.addView(hunchText, new LinearLayout.LayoutParams(0,110,1));
        Button more = new Button(this);
        more.setText("⋮");
        more.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, more);
            menu.getMenu().add("✨ Hunch");
            menu.getMenu().add("❤️ Warm");
            menu.getMenu().add("😊 Friendly");
            menu.getMenu().add("💬 Direct");
            menu.getMenu().add("🌿 Gentle");
            menu.getMenu().add("🔥 Confident");
            menu.getMenu().add("✦ Long Text");
            menu.setOnMenuItemClickListener(item -> { transform(item.getTitle().toString()); return true; });
            menu.show();
        });
        toolbar.addView(more, new LinearLayout.LayoutParams(120,110));
        root.addView(toolbar);

        LinearLayout tools = new LinearLayout(this);
        String[] moods = {"✨ Hunch","❤️ Warm","😊 Friendly","💬 Direct","🌿 Gentle","🔥 Confident","✦ Long Text"};
        for (String m : moods) {
            Button b = new Button(this);
            b.setText(m);
            b.setOnClickListener(v -> transform(((Button)v).getText().toString()));
            tools.addView(b,new LinearLayout.LayoutParams(0,120,1));
        }
        tools.setVisibility(View.GONE);
        root.addView(tools);

        LinearLayout keys = new LinearLayout(this);
        String[] rows = {"qwertyuiop","asdfghjkl","zxcvbnm"};
        for (String row : rows) {
            LinearLayout r = new LinearLayout(this);
            for (char c : row.toCharArray()) {
                Button b = new Button(this);
                b.setText(String.valueOf(c));
                b.setOnClickListener(v -> {
                    String s=((Button)v).getText().toString();
                    commit(s);
                    requestPrediction(preview.getText().toString());
                });
                r.addView(b,new LinearLayout.LayoutParams(0,110,1));
            }
            keys.addView(r);
        }
        root.addView(keys);

        Button space = new Button(this);
        space.setText("SPACE");
        space.setOnClickListener(v -> { commit(" "); requestPrediction(preview.getText().toString()); });
        root.addView(space);
        return root;
    }

    void commit(String s) {
        InputConnection ic=getCurrentInputConnection();
        if(ic!=null) ic.commitText(s,1);
        if(preview!=null) preview.append(s);
    }

    void requestPrediction(String text) {
        if (text == null || text.trim().isEmpty()) return;
        prediction.setText("Next: Hunch AI is thinking…");
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("text", text);
                body.put("context", text);
                body.put("mode", "predict");
                body.put("style", "natural");
                body.put("maxSuggestions", 5);
                String response = postJson(body.toString());
                JSONObject json = new JSONObject(response);
                JSONArray a = json.optJSONArray("suggestions");
                StringBuilder out = new StringBuilder("Next: ");
                if (a != null && a.length() > 0) {
                    for (int i=0;i<a.length();i++) {
                        if (i>0) out.append(" • ");
                        out.append(a.optString(i));
                    }
                } else out.append("no suggestion");
                main.post(() -> prediction.setText(out.toString()));
            } catch (Exception e) {
                main.post(() -> prediction.setText("Next: AI unavailable — local mode"));
            }
        });
    }

    void transform(String modeText) {
        String s=preview.getText().toString().trim();
        if(s.isEmpty()) return;
        String mode = modeText.contains("Long Text") ? "long-text" : "hunch";
        String style = modeText.contains("Warm") ? "warm" :
                modeText.contains("Friendly") ? "friendly" :
                modeText.contains("Direct") ? "direct" :
                modeText.contains("Gentle") ? "gentle" :
                modeText.contains("Confident") ? "confident" :
                "natural";
        prediction.setText("Hunch AI is creating options…");
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("text", s);
                body.put("context", s);
                body.put("mode", mode);
                body.put("style", style);
                body.put("maxSuggestions", 5);
                JSONObject json = new JSONObject(postJson(body.toString()));
                main.post(() -> {
                    try {
                        InputConnection ic=getCurrentInputConnection();
                        if (ic == null) return;
                        if ("long-text".equals(mode)) {
                            String out=json.optString("text", s);
                            ic.commitText(out,1);
                        } else {
                            JSONArray a=json.optJSONArray("suggestions");
                            StringBuilder out=new StringBuilder();
                            if(a!=null) for(int i=0;i<a.length();i++) {
                                if(i>0) out.append("\n\n");
                                out.append(a.optString(i));
                            }
                            if(out.length()>0) ic.commitText(out.toString(),1);
                        }
                    } catch(Exception ignored) {}
                });
            } catch (Exception e) {
                main.post(() -> prediction.setText("Hunch AI unavailable"));
            }
        });
    }

    String postJson(String body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(AI_URL).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json");
        try(OutputStream os=c.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        InputStream in=c.getResponseCode()<400 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buf=new byte[4096]; int n;
        while((n=in.read(buf))!=-1) out.write(buf,0,n);
        String result=new String(out.toByteArray(),StandardCharsets.UTF_8);
        if(c.getResponseCode()>=400) throw new IOException(result);
        return result;
    }

    @Override public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
