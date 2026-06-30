package com.mogomarket.app;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

// שירות לתקשורת עם Google Gemini AI API
public class LLMService {

    // כתובת ה-API של Gemini
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "gemini-flash-latest:generateContent";
    // מפתח API שמגיע מ-BuildConfig (שמור בצורה מאובטחת ב-local.properties)
    private static final String API_KEY = BuildConfig.GEMINI_API_KEY;

    // HTTP client עם timeout של 30 שניות
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private final DecimalFormat df = new DecimalFormat("#.##");
    private long lastRequestTime = 0; // זמן הבקשה האחרונה - למניעת rate limiting

    // ממשק callback לניתוח גרף
    public interface AnalysisCallback {
        void onAnalysisReceived(String analysis);
        void onError(String error);
    }

    // ממשק callback כללי (פשוט יותר)
    public interface LLMCallback {
        void onSuccess(String result);
        void onFailure(Throwable t);
    }

    public LLMService() { }

    // מתודה ציבורית לשליחת prompt גנרי - עוטפת את AnalysisCallback ב-LLMCallback
    public void generateContent(String prompt, LLMCallback callback) {
        sendToGemini(prompt, new AnalysisCallback() {
            @Override
            public void onAnalysisReceived(String analysis) {
                callback.onSuccess(analysis);
            }
            @Override
            public void onError(String error) {
                callback.onFailure(new Exception(error));
            }
        });
    }

    // מתודה לשאלה ספציפית על מניה - בונה prompt עשיר עם הקשר פיננסי
    public void askQuestion(String symbol, String question, String context,
                            List<Float> closes, AnalysisCallback callback) {
        String pricesStr = "לא זמין";
        if (closes != null && closes.size() >= 2) {
            // לוקח 5 מחירי סגירה אחרונים בלבד (קיצור הפרומפט)
            String[] formatted = closes.subList(0, Math.min(5, closes.size()))
                    .stream()
                    .map(c -> df.format(c))
                    .toArray(String[]::new);
            pricesStr = String.join(", ", formatted);
        }

        // בניית פרומפט מובנה עם הוראות, שאלה והקשר
        String prompt = String.format(
                "אתה יועץ פיננסי מקצועי. ענה תמיד בעברית, תמציתי וברור.\n\n" +
                        "שאלה על מניה %s:\n\n" +
                        "שאלה: %s\n\n" +
                        "הקשר: %s\n" +
                        "מחירי סגירה אחרונים: %s\n\n" +
                        "ענה בעברית בלבד, תמציתי ומקצועי.",
                symbol, question, context, pricesStr
        );

        sendToGemini(prompt, callback);
    }

    // הפונקציה הפרטית שמבצעת את קריאת ה-HTTP בפועל ל-Gemini
    private void sendToGemini(String prompt, AnalysisCallback callback) {
        long now = System.currentTimeMillis();
        long timeSinceLast = now - lastRequestTime;

        // Rate limiting - ממתין 5 שניות בין בקשות כדי למנוע 429 (Too Many Requests)
        if (timeSinceLast < 5000) {
            long delay = 5000 - timeSinceLast;
            android.util.Log.d("LLM", "ממתין " + delay + "ms לפני הקריאה");
            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    sendToGemini(prompt, callback), delay);
            return;
        }

        lastRequestTime = System.currentTimeMillis();
        android.util.Log.d("LLM", "שולח בקשה ל-Gemini...");

        // בניית גוף ה-JSON לפי פורמט API של Gemini
        JSONObject requestBody = new JSONObject();
        try {
            JSONObject part = new JSONObject().put("text", prompt);
            JSONArray parts = new JSONArray().put(part);
            JSONObject content = new JSONObject().put("parts", parts);
            JSONArray contents = new JSONArray().put(content);
            requestBody.put("contents", contents);
        } catch (Exception e) {
            notifyError(callback, "שגיאה בבניית הבקשה: " + e.getMessage());
            return;
        }

        String urlWithKey = GEMINI_API_URL + "?key=" + API_KEY; // הוספת המפתח ל-URL

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(urlWithKey)
                .addHeader("Accept", "application/json")
                .post(body)
                .build();

        // ביצוע הבקשה באופן אסינכרוני
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                android.util.Log.e("LLM", "שגיאת רשת: " + e.getMessage());
                notifyError(callback, "שגיאת רשת: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                android.util.Log.d("LLM", "קוד תגובה: " + response.code());

                // *** תיקון הבעיה – עצירת הלולאה ***
                // קוד 429 = חרגנו ממכסת הבקשות - עוצרים ולא מנסים שוב
                if (response.code() == 429) {
                    android.util.Log.w("LLM", "429 - המכסה נגמרה");
                    notifyError(callback, "המכסה היומית של Gemini נגמרה.\nנסה שוב מחר בשעה 10:00 בבוקר.");
                    return;
                }

                // קוד 403 = גישה נדחית (מפתח לא תקין / מכסה נגמרה)
                if(response.code()==403)
                {
                    android.util.Log.w("LLM", "המכסה ניגמרה להיום, חכה עד מחר בעשר בבוקר");
                    notifyError(callback, "המכסה היומית של Gemini נגמרה.\nנסה שוב מחר בשעה 10:00 בבוקר.");
                    return;
                }

                if (!response.isSuccessful()) {
                    notifyError(callback, "שגיאת API " + response.code() + ": " + responseBody);
                    return;
                }

                try {
                    // פענוח תשובת ה-JSON מ-Gemini
                    JSONObject json = new JSONObject(responseBody);
                    JSONArray candidates = json.optJSONArray("candidates");
                    if (candidates == null || candidates.length() == 0) {
                        notifyError(callback, "לא התקבלה תשובה מה-AI");
                        return;
                    }

                    // חילוץ הטקסט מהמבנה: candidates[0].content.parts[i].text
                    JSONObject firstCandidate = candidates.getJSONObject(0);
                    JSONObject content = firstCandidate.getJSONObject("content");
                    JSONArray parts = content.getJSONArray("parts");
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < parts.length(); i++) {
                        sb.append(parts.getJSONObject(i).optString("text", ""));
                    }
                    String result = sb.toString().trim();
                    android.util.Log.d("LLM", "תשובה התקבלה בהצלחה!");

                    // החזרת התשובה ל-Main Thread
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onAnalysisReceived(result));

                } catch (Exception e) {
                    notifyError(callback, "שגיאה בפענוח תגובה: " + e.getMessage());
                }
            }
        });
    }

    // פונקציית עזר - תמיד מחזירה שגיאה ב-Main Thread
    private void notifyError(AnalysisCallback callback, String error) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onError(error));
    }
}