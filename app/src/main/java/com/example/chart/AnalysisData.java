package com.mogomarket.app;

// מחלקת מודל (Model Class) - מייצגת אובייקט נתוני ניתוח שמור ב-Firebase
public class AnalysisData {

    // חותמת הזמן (Unix timestamp) של הניתוח - מאחסנים מתי הניתוח נוצר
    public long timestamp;

    // תוכן הניתוח שנשמר - הטקסט שה-LLM החזיר
    public String analysis;

    // בנאי ריק - חובה עבור Firebase Firestore כדי שיוכל לבנות את האובייקט ממסד הנתונים
    public AnalysisData() {}

    // בנאי מלא - לשימוש בקוד כשיוצרים אובייקט ניתוח חדש עם כל השדות
    public AnalysisData(long timestamp, String analysis) {
        this.timestamp = timestamp;
        this.analysis = analysis;
    }
    //AKUO
}