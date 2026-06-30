package com.mogomarket.app;

// מודל נתונים פשוט לפריט אחד בתפריט הניווט
public class NavDrawerItem {
    public final int id;       // ID של ה-menu item (לדוגמה: R.id.nav_chart)
    public final String key;   // מחרוזת מזהה לשמירה ב-SharedPreferences (לדוגמה: "nav_chart")
    public final String title; // הטקסט שיוצג בתפריט (לדוגמה: "Chart")

    public NavDrawerItem(int id, String key, String title) {
        this.id = id;
        this.key = key;
        this.title = title;
    }
}