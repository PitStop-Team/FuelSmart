package com.example.findcheapgas.ui.map;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.StationSaving;
import com.example.findcheapgas.ui.SavingColors;
import com.example.findcheapgas.util.FormatUtil;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

/**
 * 아래 시트의 주유소 목록. 한 줄은 view_list_row_amount 조각을 쓴다.
 * - 왼쪽 첫 줄: 상호 (선호 브랜드는 앞에 작은 ★)
 * - 왼쪽 둘째 줄: 브랜드 · 거리 + 배지 하나 (이득 1위 > 할인 적용 > 최저가 중 가장 앞의 하나만)
 * - 오른쪽: 가격, 그 아래 "약 2,583원 아껴요" / "비슷해요" / "약 1,200원 더 들어요"
 * 순서는 지금 정렬 기준 그대로이고, 이득 1위 배지는 실제 예상 이득 1위(isBest)에 붙는다.
 * 즐겨찾기는 상세 화면에서만 다루므로 줄에는 표시하지 않는다.
 */
public class StationAdapter extends RecyclerView.Adapter<StationAdapter.ViewHolder> {

    public interface OnStationClickListener {
        void onStationClick(StationSaving saving);
    }

    private final OnStationClickListener clickListener;
    private final List<StationSaving> stations = new ArrayList<>();
    private int cheapestPrice = Integer.MAX_VALUE;
    @Nullable
    private String selectedId;

    public StationAdapter(OnStationClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void submit(List<StationSaving> newStations, int newCheapestPrice) {
        stations.clear();
        stations.addAll(newStations);
        cheapestPrice = newCheapestPrice;
        notifyDataSetChanged();
    }

    /** 선택이 바뀐 두 항목만 다시 그린다. */
    public void setSelectedId(@Nullable String id) {
        int oldPosition = positionOf(selectedId);
        selectedId = id;
        int newPosition = positionOf(id);
        if (oldPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(oldPosition);
        }
        if (newPosition != RecyclerView.NO_POSITION && newPosition != oldPosition) {
            notifyItemChanged(newPosition);
        }
    }

    public int positionOf(@Nullable String id) {
        if (id == null) {
            return RecyclerView.NO_POSITION;
        }
        for (int i = 0; i < stations.size(); i++) {
            if (stations.get(i).getStation().getId().equals(id)) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_list_row_amount, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StationSaving saving = stations.get(position);
        GasStation station = saving.getStation();
        Context context = holder.itemView.getContext();

        holder.name.setText(station.getName());
        // 선호 브랜드는 상호 앞에 작은 ★
        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(holder.name,
                saving.isPreferred() ? R.drawable.ic_star_small : 0, 0, 0, 0);
        String distance = FormatUtil.formatDistance(station.getDistance());
        holder.info.setText(context.getString(R.string.station_info, station.getBrandName(), distance));
        String price = FormatUtil.formatPrice(station.getPrice());
        holder.price.setText(price);
        String note = FormatUtil.formatSavingNote(saving);
        holder.note.setText(note);
        holder.note.setTextColor(saving.isBaseline() ? holder.secondaryColor
                : ContextCompat.getColor(context, SavingColors.textColorOf(saving.getLevel())));
        String badge = bindBadge(holder, saving, context);

        // 접근성 설명: 상호, 선호 여부, 브랜드, 거리, 가격, 예상 이득, 배지 순서
        List<String> parts = new ArrayList<>();
        parts.add(station.getName());
        if (saving.isPreferred()) {
            parts.add(context.getString(R.string.station_preferred));
        }
        parts.add(station.getBrandName());
        parts.add(distance);
        parts.add(price);
        parts.add(note);
        if (badge != null) {
            parts.add(badge);
        }
        holder.itemView.setContentDescription(TextUtils.join(", ", parts));

        holder.itemView.setActivated(station.getId().equals(selectedId));
        holder.itemView.setOnClickListener(v -> clickListener.onStationClick(saving));
    }

    /**
     * 배지는 하나만: 이득 1위(라임 바탕) > 할인 적용(올리브 글자) > 최저가(회색 테두리).
     * @return 붙인 배지 글자, 없으면 null
     */
    @Nullable
    private String bindBadge(ViewHolder holder, StationSaving saving, Context context) {
        String text;
        if (saving.isBest()) {
            text = context.getString(saving.isBaseline() ? R.string.station_best_baseline : R.string.station_best);
            holder.badge.setBackgroundResource(R.drawable.bg_badge_best);
            holder.badge.setTextColor(holder.onBrandColor);
        } else if (saving.getDiscountRule().hasDiscount()) {
            text = context.getString(R.string.station_discount_applied,
                    FormatUtil.formatPrice(saving.getDiscountRule().getPerLiter()));
            holder.badge.setBackground(null);
            holder.badge.setTextColor(holder.accentColor);
        } else if (saving.getStation().getPrice() == cheapestPrice) {
            text = context.getString(R.string.station_cheapest);
            holder.badge.setBackgroundResource(R.drawable.bg_badge_outline);
            holder.badge.setTextColor(holder.secondaryColor);
        } else {
            holder.badge.setVisibility(View.GONE);
            return null;
        }
        holder.badge.setText(text);
        holder.badge.setVisibility(View.VISIBLE);
        return text;
    }

    @Override
    public int getItemCount() {
        return stations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView info;
        final TextView badge;
        final TextView price;
        final TextView note;
        // 테마 색: 보조 글자색, 강조 글자색(밝게 올리브, 어둡게 라임), 라임 위 글자색
        final int secondaryColor;
        final int accentColor;
        final int onBrandColor;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.list_row_title);
            info = itemView.findViewById(R.id.list_row_subtitle);
            badge = itemView.findViewById(R.id.list_row_badge);
            price = itemView.findViewById(R.id.list_row_amount);
            note = itemView.findViewById(R.id.list_row_amount_note);
            secondaryColor = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurfaceVariant);
            accentColor = MaterialColors.getColor(itemView, androidx.appcompat.R.attr.colorPrimary);
            onBrandColor = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnPrimaryContainer);
            // 선호 브랜드 ★의 간격과 색
            name.setCompoundDrawablePadding(itemView.getResources().getDimensionPixelSize(R.dimen.space_xs));
            TextViewCompat.setCompoundDrawableTintList(name, ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.getContext(), R.color.preferred_star)));
        }
    }
}
