package com.axali.hunchtext;

import android.inputmethodservice.InputMethodService;
import android.view.*;
import android.view.inputmethod.InputConnection;
import android.widget.*;
import java.util.*;

public class HunchTextImeService extends InputMethodService {
    LinearLayout root;
    EditText preview;
    TextView prediction;
    int mode = 0;

    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(6,6,6,6);

        preview = new EditText(this);
        preview.setHint("Type here — Hunch will predict and enrich");
        preview.setSingleLine(false);
        root.addView(preview, new LinearLayout.LayoutParams(-1,140));

        prediction = new TextView(this);
        prediction.setText("Next: the • we • can • should");
        prediction.setPadding(10,8,10,8);
        root.addView(prediction, new LinearLayout.LayoutParams(-1,70));

        LinearLayout tools = new LinearLayout(this);
        String[] moods = {"✨ Hunch","❤️ Warm","😊 Friendly","💬 Direct","🌿 Gentle","🔥 Confident","✦ Long Text"};
        for (String m : moods) {
            Button b = new Button(this);
            b.setText(m);
            b.setOnClickListener(v -> transform(((Button)v).getText().toString()));
            tools.addView(b,new LinearLayout.LayoutParams(0,120,1));
        }
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
                    updatePrediction(preview.getText().toString()+s);
                });
                r.addView(b,new LinearLayout.LayoutParams(0,110,1));
            }
            keys.addView(r);
        }
        root.addView(keys);

        Button space = new Button(this);
        space.setText("SPACE");
        space.setOnClickListener(v -> { commit(" "); updatePrediction(preview.getText().toString()+" "); });
        root.addView(space);
        return root;
    }

    void commit(String s) {
        InputConnection ic=getCurrentInputConnection();
        if(ic!=null) ic.commitText(s,1);
        if(preview!=null) preview.append(s);
    }

    void updatePrediction(String text) {
        String t=text.toLowerCase(Locale.US).trim();
        String p="Next: the • we • can • should";
        if(t.endsWith("i think we should")) p="Next: talk • try • go • wait • consider";
        else if(t.endsWith("i want to")) p="Next: make • share • build • create • learn";
        else if(t.endsWith("thank you")) p="Next: for • so • very • again";
        else if(t.endsWith("i feel")) p="Next: happy • worried • ready • calm • hopeful";
        else if(t.length()>40) p="Next: and • because • however • therefore • finally";
        prediction.setText(p);
    }

    void transform(String modeText) {
        String s=preview.getText().toString().trim();
        if(s.isEmpty()) return;
        String out=s;
        if(modeText.contains("Long Text")) {
            out=longText(s);
        } else if(modeText.contains("Warm")) {
            out="I truly appreciate this: "+s+" ❤️";
        } else if(modeText.contains("Friendly")) {
            out="Hey! "+s+" 😊";
        } else if(modeText.contains("Gentle")) {
            out="Just wanted to share this gently: "+s;
        } else if(modeText.contains("Confident")) {
            out=s+" — I mean it.";
        } else if(modeText.contains("Direct")) {
            out=s;
        } else {
            out=s+"\n\nPossible hunches: caring • concerned • thoughtful\nChoose the expression that fits you.";
        }
        InputConnection ic=getCurrentInputConnection();
        if(ic!=null) ic.commitText(out,1);
    }

    String longText(String s) {
        String clean=s.replaceAll("\\s+"," ").trim();
        if(clean.length()<80)
            return "Refined expression:\n"+clean+"\n\nA little more warmth and clarity can make the message feel natural, thoughtful, and complete.";
        String[] parts=clean.split("(?<=[.!?])\\s+");
        StringBuilder b=new StringBuilder();
        for(int i=0;i<parts.length;i++){
            String part=parts[i].trim();
            if(part.isEmpty()) continue;
            if(i==0) b.append(part);
            else if(i==parts.length-1) b.append("\n\nFinally, ").append(lowerFirst(part));
            else b.append(" ").append(part);
        }
        b.append("\n\nThe meaning stays yours; HunchText simply refines the flow, clarity, and expression.");
        return b.toString();
    }

    String lowerFirst(String s) {
        if(s.length()<2) return s;
        return Character.toLowerCase(s.charAt(0))+s.substring(1);
    }
}
