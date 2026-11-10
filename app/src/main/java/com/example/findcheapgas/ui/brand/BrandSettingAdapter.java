package com.example.findcheapgas.ui.brand;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.Brand;
import com.example.findcheapgas.model.BrandSetting;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.util.FormatUtil;

import java.util.List;

/** 내 브랜드 목록. Brand.codes() 순서로 모든 브랜드를 보여준다. */
public class BrandSettingAdapter extends RecyclerView.Adapter<BrandSettingAdapter.ViewHolder> {

    public interface OnBrandClickListener {
        void onBrandClick(String brandCode);
    }

    private final List<String> codes = Brand.codes();
    private final OnBrandClickListener clickListener;
    private BrandSettings settings = BrandSettings.EMPTY;

    public BrandSettingAdapter(OnBrandClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void submit(BrandSettings newSettings) {
        settings = newSettings;
        notifyItemRangeChanged(0, codes.size());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_brand_setting, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String code = codes.get(position);
        BrandSetting setting = settings.get(code);
        String name = Brand.nameOf(code);
        String summary = FormatUtil.formatDiscountSummary(setting.getDiscountRule());
        holder.name.setText(name);
        holder.summary.setText(summary);
        holder.preferred.setVisibility(setting.isPreferred() ? View.VISIBLE : View.GONE);
        holder.hidden.setVisibility(setting.isHidden() ? View.VISIBLE : View.GONE);

        StringBuilder state = new StringBuilder(summary);
        if (setting.isPreferred()) {
            state.append(", ").append(holder.preferred.getText());
        }
        if (setting.isHidden()) {
            state.append(", ").append(holder.hidden.getText());
        }
        holder.itemView.setContentDescription(
                holder.itemView.getContext().getString(R.string.brand_settings_item_description, name, state));
        holder.itemView.setOnClickListener(v -> clickListener.onBrandClick(code));
    }

    @Override
    public int getItemCount() {
        return codes.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView preferred;
        final TextView hidden;
        final TextView summary;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.brand_setting_name);
            preferred = itemView.findViewById(R.id.brand_setting_preferred);
            hidden = itemView.findViewById(R.id.brand_setting_hidden);
            summary = itemView.findViewById(R.id.brand_setting_summary);
        }
    }
}
