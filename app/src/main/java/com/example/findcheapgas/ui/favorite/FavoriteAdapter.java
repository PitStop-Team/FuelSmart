package com.example.findcheapgas.ui.favorite;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.FavoriteItem;
import com.example.findcheapgas.model.FavoritePrice;
import com.example.findcheapgas.model.FavoriteStation;
import com.example.findcheapgas.model.StationSaving;
import com.example.findcheapgas.ui.SavingColors;
import com.example.findcheapgas.util.FavoritePriceResolver;
import com.example.findcheapgas.util.FormatUtil;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

/**
 * 즐겨찾기 탭 목록. 최근에 저장한 순서 그대로 보여준다. 한 줄은 목록 한 줄 조각(view_list_row_amount)이다.
 * - 왼쪽: 상호 / 브랜드 · 조회 위치에서 거리, 30분이 지난 캐시 가격이면 배지 ("약 2시간 전 가격")
 * - 오른쪽 위: 지금 고른 유종 가격. 가격이 없으면 비운다.
 * - 오른쪽 아래: 예상 이득 설명 ("약 2,583원 아껴요", 색은 지도 탭 목록과 같음), 또는 짧은 이유
 *   (5km 밖이라 계산 안 해요, 이 유종을 팔지 않아요, 찾을 수 없어요, 오늘 한도에 도달했어요 등)
 * - 오피넷에서 찾을 수 없으면 아래에 삭제 버튼. 길게 누르면 삭제 메뉴 (접근성 동작 "삭제"도 같음)
 */
public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.ViewHolder> {

    public interface Listener {
        void onItemClick(FavoriteItem item);

        void onMenuClick(View anchor, FavoriteItem item);

        void onDeleteClick(FavoriteItem item);
    }

    private final Listener listener;
    private final List<FavoriteItem> items = new ArrayList<>();
    private long now = System.currentTimeMillis();

    public FavoriteAdapter(Listener listener) {
        this.listener = listener;
    }

    /** @param originLabel 조회 위치 이름. 거리 기준은 맨 위 기준 문구에 쓰므로 줄에는 쓰지 않는다. */
    public void submit(List<FavoriteItem> newItems, String originLabel) {
        items.clear();
        items.addAll(newItems);
        now = System.currentTimeMillis();
        notifyDataSetChanged();
    }

    @Nullable
    public FavoriteItem getItem(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_favorite, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FavoriteItem item = items.get(position);
        FavoriteStation favorite = item.getFavorite();
        Context context = holder.itemView.getContext();

        holder.name.setText(favorite.getName());
        Double distance = item.getDistance();
        holder.info.setText(distance != null
                ? context.getString(R.string.station_info, favorite.getBrandName(), FormatUtil.formatDistance(distance))
                : favorite.getBrandName());

        FavoritePrice price = item.getPrice();
        String stale = bindPrice(holder, item, context);
        String note = bindNote(holder, item, context);

        boolean notFound = price != null && price.getStatus() == FavoritePrice.Status.NOT_FOUND;
        holder.delete.setVisibility(notFound ? View.VISIBLE : View.GONE);
        holder.delete.setOnClickListener(v -> listener.onDeleteClick(item));

        // 접근성 설명: 상호, 브랜드 · 거리, 가격, 예상 이득 또는 이유, 오래된 가격
        List<String> parts = new ArrayList<>();
        parts.add(favorite.getName());
        parts.add(holder.info.getText().toString());
        if (holder.price.getVisibility() == View.VISIBLE) {
            parts.add(holder.price.getText().toString());
        }
        if (note != null) {
            parts.add(note);
        }
        if (stale != null) {
            parts.add(stale);
        }
        holder.row.setContentDescription(TextUtils.join(", ", parts));
        holder.row.setOnClickListener(v -> listener.onItemClick(item));
        holder.row.setOnLongClickListener(v -> {
            listener.onMenuClick(v, item);
            return true;
        });
        // 길게 누르는 메뉴는 화면 낭독기에서 찾기 어려우므로 "삭제" 동작을 따로 단다.
        if (holder.deleteActionId != View.NO_ID) {
            ViewCompat.removeAccessibilityAction(holder.row, holder.deleteActionId);
        }
        holder.deleteActionId = ViewCompat.addAccessibilityAction(holder.row,
                context.getString(R.string.favorite_menu_delete), (view, arguments) -> {
                    listener.onDeleteClick(item);
                    return true;
                });
    }

    /**
     * 오른쪽 위 가격. 가격이 없으면 비우고 이유는 아래 줄에 쓴다.
     * 30분이 지난 캐시 가격이면 왼쪽 배지에 "약 2시간 전 가격"을 쓰고 그 문구를 돌려준다.
     */
    @Nullable
    private String bindPrice(ViewHolder holder, FavoriteItem item, Context context) {
        FavoritePrice price = item.getPrice();
        holder.badge.setVisibility(View.GONE);
        boolean available = price != null && price.isAvailable();
        holder.price.setVisibility(available ? View.VISIBLE : View.GONE);
        if (!available) {
            return null;
        }
        holder.price.setText(FormatUtil.formatPrice(price.getPrice()));
        String age = staleAge(context, price);
        if (age == null) {
            return null;
        }
        String stale = context.getString(R.string.favorite_stale, age);
        holder.badge.setText(stale);
        holder.badge.setBackgroundResource(R.drawable.bg_badge_outline);
        holder.badge.setTextColor(holder.secondaryColor);
        holder.badge.setVisibility(View.VISIBLE);
        return stale;
    }

    /** 오른쪽 아래: 예상 이득 설명이나 짧은 이유. 쓴 문구를 돌려준다. */
    @Nullable
    private String bindNote(ViewHolder holder, FavoriteItem item, Context context) {
        holder.note.setTextColor(holder.secondaryColor);
        String note = null;
        FavoritePrice price = item.getPrice();
        if (price == null) {
            note = context.getString(R.string.favorite_price_loading);
        } else if (!price.isAvailable()) {
            note = priceProblem(context, price);
        } else if (item.getSavingStatus() == FavoriteItem.SavingStatus.CALCULATED && item.getSaving() != null) {
            StationSaving saving = item.getSaving();
            note = FormatUtil.formatSavingNote(saving);
            if (!saving.isBaseline()) {
                holder.note.setTextColor(ContextCompat.getColor(context, SavingColors.textColorOf(saving.getLevel())));
            }
        } else if (item.getSavingStatus() == FavoriteItem.SavingStatus.TOO_FAR) {
            note = context.getString(R.string.favorite_too_far);
        } else if (item.getSavingStatus() == FavoriteItem.SavingStatus.NO_BASELINE) {
            note = context.getString(R.string.favorite_no_baseline);
        } else if (item.getSavingStatus() == FavoriteItem.SavingStatus.NO_ORIGIN) {
            note = context.getString(R.string.favorite_no_origin);
        }
        holder.note.setText(note);
        holder.note.setVisibility(note != null ? View.VISIBLE : View.GONE);
        return note;
    }

    private static String priceProblem(Context context, FavoritePrice price) {
        switch (price.getStatus()) {
            case NOT_SOLD:
                return context.getString(R.string.favorite_not_sold);
            case NOT_FOUND:
                return context.getString(R.string.favorite_not_found);
            case DAILY_LIMIT:
                return context.getString(R.string.favorite_daily_limit);
            case NETWORK:
                return context.getString(R.string.favorite_network_error);
            case API_KEY:
                return context.getString(R.string.favorite_api_key_error);
            default:
                return context.getString(R.string.favorite_opinet_error);
        }
    }

    /** 30분이 지난 캐시면 "40분", "2시간". 아니면 null */
    @Nullable
    private String staleAge(Context context, FavoritePrice price) {
        if (price.getSource() != FavoritePrice.Source.CACHE) {
            return null;
        }
        long age = now - price.getFetchedAt();
        if (age < FavoritePriceResolver.FRESH_CACHE_MS) {
            return null;
        }
        long minutes = age / 60_000;
        if (minutes < 60) {
            return context.getString(R.string.favorite_stale_minutes, (int) minutes);
        }
        return context.getString(R.string.favorite_stale_hours, (int) (minutes / 60));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View row;
        final TextView name;
        final TextView info;
        final TextView badge;
        final TextView price;
        final TextView note;
        final View delete;
        final int secondaryColor;
        int deleteActionId = View.NO_ID;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            row = itemView.findViewById(R.id.favorite_row);
            name = itemView.findViewById(R.id.list_row_title);
            info = itemView.findViewById(R.id.list_row_subtitle);
            badge = itemView.findViewById(R.id.list_row_badge);
            price = itemView.findViewById(R.id.list_row_amount);
            note = itemView.findViewById(R.id.list_row_amount_note);
            delete = itemView.findViewById(R.id.favorite_delete);
            secondaryColor = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurfaceVariant);
        }
    }
}
