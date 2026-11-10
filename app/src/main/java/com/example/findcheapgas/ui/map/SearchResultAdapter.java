package com.example.findcheapgas.ui.map;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.findcheapgas.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 검색 결과 목록. 장소(이름, 분류, 주소)와 주유소(상호, 브랜드, 주소)를 같은 줄 모양으로 보여준다.
 * 지도 탭 검색 결과 시트와 집·회사 위치 검색 시트가 같이 쓴다.
 */
public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.Holder> {

    /** 한 줄에 보여줄 내용과 눌렀을 때 할 일 */
    public static final class Row {
        final String name;
        final String tag;
        final String address;
        final Runnable onClick;
        // 화면 읽기 프로그램용 설명. null이면 지도 탭 검색 결과 설명("눌러서 이 위치 기준으로 보기")
        final String description;

        public Row(String name, String tag, String address, Runnable onClick) {
            this(name, tag, address, onClick, null);
        }

        public Row(String name, String tag, String address, Runnable onClick, String description) {
            this.name = name;
            this.tag = tag;
            this.address = address;
            this.onClick = onClick;
            this.description = description;
        }
    }

    private final List<Row> rows = new ArrayList<>();

    public void submit(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Row row = rows.get(position);
        holder.name.setText(row.name);
        holder.tag.setText(row.tag);
        holder.tag.setVisibility(row.tag.isEmpty() ? View.GONE : View.VISIBLE);
        holder.address.setText(row.address);
        holder.address.setVisibility(row.address.isEmpty() ? View.GONE : View.VISIBLE);
        holder.itemView.setContentDescription(row.description != null ? row.description
                : holder.itemView.getContext().getString(
                R.string.search_result_description, row.name, row.tag, row.address));
        holder.itemView.setOnClickListener(v -> row.onClick.run());
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView tag;
        final TextView address;

        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.search_result_name);
            tag = itemView.findViewById(R.id.search_result_tag);
            address = itemView.findViewById(R.id.search_result_address);
        }
    }
}
