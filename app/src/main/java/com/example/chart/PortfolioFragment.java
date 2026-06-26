package com.example.chart;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class PortfolioFragment extends Fragment {

    private RecyclerView recyclerView;
    private MaterialButton btnRefreshPortfolio;
    private MaterialButton btnAddStockToPortfolio;

    private List<StockData> stocksList;
    private StocksAdapter adapter;
    private DatabaseReference portfolioRef;
    private DatabaseReference closedTradesRef;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_portfolio, container, false);

        recyclerView           = v.findViewById(R.id.tradesRecyclerView);
        btnAddStockToPortfolio = v.findViewById(R.id.btnAddStockToPortfolio);
        btnRefreshPortfolio    = v.findViewById(R.id.btnRefreshPortfolio);

        stocksList = new ArrayList<>();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(requireContext(), AuthLogin.class));
            requireActivity().finish();
            return v;
        }

        String uid = user.getUid();

        portfolioRef = FirebaseDatabase.getInstance()
                .getReference("users").child(uid).child("portfolio-stocks");

        closedTradesRef = FirebaseDatabase.getInstance()
                .getReference("users").child(uid).child("closed-trades");

        adapter = new StocksAdapter(stocksList, new StocksAdapter.OnStockClickListener() {
            @Override
            public void onStockClick(String symbol) { }

            @Override
            public void onStockDelete(String symbol, double sellPrice) {
                portfolioRef.child(symbol).get().addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        StockData data = snapshot.getValue(StockData.class);
                        if (data != null) {
                            data.sellPrice = sellPrice;
                            closedTradesRef.child(symbol).setValue(data);
                            portfolioRef.child(symbol).removeValue();
                        }
                    }
                });
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        portfolioRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                stocksList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockData data = ds.getValue(StockData.class);
                    if (data != null) stocksList.add(data);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });

        btnAddStockToPortfolio.setOnClickListener(view -> {
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.fade_in_fast, R.anim.fade_out_fast)
                    .replace(R.id.fragment_container, new PortfolioAddStockFragment())
                    .addToBackStack(null)
                    .commit();
        });

        btnRefreshPortfolio.setOnClickListener(view -> adapter.refreshPrices());

        return v;
    }
}
