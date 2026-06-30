package com.mogomarket.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

// אדפטר לתפריט הניווט הצדדי (Navigation Drawer) - מציג רשימת מסכים
public class DrawerNavAdapter extends RecyclerView.Adapter<DrawerNavAdapter.VH> {

    // ממשק לאירוע לחיצה על פריט בתפריט
    public interface Listener {
        void onClick(NavDrawerItem item);
    }

    private final List<NavDrawerItem> items; // רשימת פריטי הניווט
    private final Listener listener;
    private int selectedId = 0; // ה-ID של הפריט הנבחר כרגע

    public DrawerNavAdapter(List<NavDrawerItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    // גישה לרשימת הפריטים מבחוץ
    public List<NavDrawerItem> getItems() {
        return items;
    }

    // קביעת הפריט הנבחר ועדכון הרשימה
    public void setSelectedId(int id) {
        selectedId = id;
        notifyDataSetChanged(); // ציור מחדש של כל הפריטים
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // ניפוח ה-layout של שורה בודדת בתפריט
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_drawer_nav, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        NavDrawerItem item = items.get(position);
        holder.txtTitle.setText(item.title); // הצגת שם הפריט

        boolean selected = (item.id == selectedId); // האם פריט זה נבחר?

        if (selected) {
            // פריט נבחר - רקע מודגש וטקסט שחור מלא
            holder.root.setBackgroundResource(R.drawable.nav_item_selected_bg);
            holder.txtTitle.setTextColor(0xFF000000); // שחור מלא
            holder.txtTitle.setAlpha(1.0f);
        } else {
            // פריט רגיל - רקע ברירת מחדל וטקסט שקוף מעט
            holder.root.setBackgroundResource(R.drawable.nav_item_default_bg);
            holder.txtTitle.setTextColor(0xFF000000); // שחור
            holder.txtTitle.setAlpha(0.8f); // דהוי
        }


        holder.itemView.setOnClickListener(v -> listener.onClick(item)); // הפעלת הניווט בלחיצה
    }


    @Override
    public int getItemCount() {
        return items.size();
    }

    // ViewHolder - מחזיק את הרכיבים של שורה אחת בתפריט
    static class VH extends RecyclerView.ViewHolder {
        LinearLayout root;  // הרקע של השורה (לשינוי צבע בבחירה)
        TextView txtTitle;  // כיתוב שם הפריט

        VH(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.root);
            txtTitle = itemView.findViewById(R.id.txtTitle);
        }
    }
}