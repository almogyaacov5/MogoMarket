package com.mogomarket.app;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class PnlCalculatorFragment extends Fragment {

    private TextInputEditText etEntryPrice, etTargetPrice, etStopLoss, etQuantity;
    private TextView tvProfit, tvLoss, tvProfitPercent, tvRiskReward, tvTotalInvested;
    private View resultsCard;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pnl_calculator, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etEntryPrice  = view.findViewById(R.id.etEntryPrice);
        etTargetPrice = view.findViewById(R.id.etTargetPrice);
        etStopLoss    = view.findViewById(R.id.etStopLoss);
        etQuantity    = view.findViewById(R.id.etQuantity);

        tvProfit        = view.findViewById(R.id.tvProfit);
        tvLoss          = view.findViewById(R.id.tvLoss);
        tvProfitPercent = view.findViewById(R.id.tvProfitPercent);
        tvRiskReward    = view.findViewById(R.id.tvRiskReward);
        tvTotalInvested = view.findViewById(R.id.tvTotalInvested);
        resultsCard     = view.findViewById(R.id.resultsCardPnl);

        MaterialButton btnCalculate = view.findViewById(R.id.btnCalculate);
        MaterialButton btnClear     = view.findViewById(R.id.btnClearCalc);

        btnCalculate.setOnClickListener(v -> calculate());
        btnClear.setOnClickListener(v -> clearFields());
    }

    private void calculate() {
        String sEntry  = etEntryPrice.getText()  != null ? etEntryPrice.getText().toString().trim()  : "";
        String sTarget = etTargetPrice.getText() != null ? etTargetPrice.getText().toString().trim() : "";
        String sStop   = etStopLoss.getText()    != null ? etStopLoss.getText().toString().trim()    : "";
        String sQty    = etQuantity.getText()    != null ? etQuantity.getText().toString().trim()    : "";

        if (TextUtils.isEmpty(sEntry) || TextUtils.isEmpty(sTarget) ||
                TextUtils.isEmpty(sStop) || TextUtils.isEmpty(sQty)) {
            if (getContext() != null) {
                android.widget.Toast.makeText(getContext(),
                        "Please fill in all fields", android.widget.Toast.LENGTH_SHORT).show();
            }
            return;
        }

        try {
            double entry  = Double.parseDouble(sEntry);
            double target = Double.parseDouble(sTarget);
            double stop   = Double.parseDouble(sStop);
            double qty    = Double.parseDouble(sQty);

            if (entry <= 0 || qty <= 0) {
                android.widget.Toast.makeText(getContext(),
                        "Entry price and quantity must be > 0", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            double potentialProfit = (target - entry) * qty;
            double maxLoss         = (entry - stop)   * qty;
            double profitPct       = ((target - entry) / entry) * 100.0;
            double riskReward      = (stop > 0 && maxLoss != 0)
                    ? Math.abs(potentialProfit / maxLoss) : 0;
            double totalInvested   = entry * qty;

            int profitColor = potentialProfit >= 0
                    ? requireContext().getColor(R.color.gain)
                    : requireContext().getColor(R.color.loss);

            tvProfit.setText(String.format("$%.2f", potentialProfit));
            tvProfit.setTextColor(profitColor);

            tvLoss.setText(String.format("-$%.2f", Math.abs(maxLoss)));

            tvProfitPercent.setText(String.format("%.2f%%", profitPct));
            tvProfitPercent.setTextColor(profitColor);

            tvRiskReward.setText(String.format("1 : %.2f", riskReward));
            tvTotalInvested.setText(String.format("$%.2f", totalInvested));

            resultsCard.setVisibility(View.VISIBLE);

        } catch (NumberFormatException e) {
            android.widget.Toast.makeText(getContext(),
                    "Invalid number format", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void clearFields() {
        etEntryPrice.setText("");
        etTargetPrice.setText("");
        etStopLoss.setText("");
        etQuantity.setText("");
        resultsCard.setVisibility(View.GONE);
    }
}
