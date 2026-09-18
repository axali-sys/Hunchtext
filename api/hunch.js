export default async function handler(req, res) {
  if (req.method !== "POST") return res.status(405).json({ error: "Method not allowed" });
  try {
    const { text = "", context = "", mode = "predict", style = "natural", maxSuggestions = 5 } = req.body || {};
    if (!text.trim()) return res.status(400).json({ error: "text is required" });
    const apiKey = process.env.OPENAI_API_KEY;
    if (!apiKey) return res.status(503).json({ error: "AI service is not configured" });

    const base = (process.env.OPENAI_BASE_URL || "https://api.openai.com/v1").replace(/\/$/, "");
    const model = process.env.HUNCHTEXT_MODEL || "gpt-4o-mini";
    const system = [
      "You are HunchText, an AI writing assistant inside a mobile keyboard.",
      "Preserve the user's meaning and voice. Never make decisions for the user.",
      "Return JSON only.",
      mode === "predict"
        ? `Give up to ${maxSuggestions} short likely next-word or next-phrase continuations.`
        : mode === "long-text"
          ? "Improve coherence, paragraph flow, transitions, clarity and expression while preserving meaning."
          : "Generate alternative expressions that reflect plausible tones without changing the intended meaning.",
      `Style: ${style}.`
    ].join(" ");

    const user = JSON.stringify({ text, context, mode, style, maxSuggestions });
    const r = await fetch(base + "/chat/completions", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${apiKey}` },
      body: JSON.stringify({
        model,
        temperature: 0.7,
        response_format: { type: "json_object" },
        messages: [
          { role: "system", content: system },
          { role: "user", content: user }
        ]
      })
    });
    const data = await r.json();
    if (!r.ok) return res.status(r.status).json({ error: data?.error?.message || "AI provider error" });

    let parsed;
    try { parsed = JSON.parse(data.choices?.[0]?.message?.content || "{}"); }
    catch { parsed = {}; }

    if (mode === "predict") {
      return res.status(200).json({ suggestions: Array.isArray(parsed.suggestions) ? parsed.suggestions.slice(0, maxSuggestions) : [] });
    }
    if (mode === "long-text") {
      return res.status(200).json({ text: parsed.text || parsed.output || text });
    }
    return res.status(200).json({ suggestions: Array.isArray(parsed.suggestions) ? parsed.suggestions : [] });
  } catch (e) {
    return res.status(500).json({ error: "HunchText AI service error" });
  }
}
