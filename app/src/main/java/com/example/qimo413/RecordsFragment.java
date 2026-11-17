package com.example.qimo413;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;

/** 历史记录页：RecyclerView 展示设备自动操作日志，左滑确认删除，空数据显示占位提示。 */
public class RecordsFragment extends Fragment {
    /** 页面创建：清理过期记录，从数据库读所有设备操作日志，用 RecyclerView 展示。 */
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        View view = inflater.inflate(R.layout.fragment_records, parent, false);
        EventDbHelper database = new EventDbHelper(requireContext());
        database.deleteOlderThanWeek();
        List<EventDbHelper.Entry> events = database.recentEntries();
        View empty = view.findViewById(R.id.empty_records);
        empty.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
        RecyclerView list = view.findViewById(R.id.records_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        RecyclerView.Adapter<RecordHolder> adapter = new RecyclerView.Adapter<RecordHolder>() {
            @NonNull @Override public RecordHolder onCreateViewHolder(@NonNull ViewGroup group, int type) { return new RecordHolder((TextView) inflater.inflate(R.layout.item_record, group, false)); }
            @Override public void onBindViewHolder(@NonNull RecordHolder holder, int position) {
                EventDbHelper.Entry entry = events.get(position);
                holder.text.setText(entry.time + "  ·  " + entry.title + "\n" + entry.detail);
                // 点击单条记录弹出详情对话框
                holder.text.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(entry.time + " · " + entry.title)
                        .setMessage(entry.detail)
                        .setPositiveButton(R.string.ok, null).show());
            }
            @Override public int getItemCount() { return events.size(); }
        };
        list.setAdapter(adapter);
        // 左滑删除：弹确认对话框，确认后从数据库和列表移除
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) { return false; }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                int position = holder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                EventDbHelper.Entry entry = events.get(position);
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("删除历史记录？").setMessage(entry.time + " · " + entry.title)
                        .setNegativeButton(R.string.cancel, (dialog, which) -> adapter.notifyItemChanged(position))
                        .setPositiveButton(R.string.confirm, (dialog, which) -> {
                            database.delete(entry.id); events.remove(position); adapter.notifyItemRemoved(position);
                            empty.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
                        })
                        .setOnCancelListener(dialog -> adapter.notifyItemChanged(position)).show();
            }
        }).attachToRecyclerView(list);
        return view;
    }
    static class RecordHolder extends RecyclerView.ViewHolder {
        final TextView text;
        RecordHolder(TextView text) { super(text); this.text = text; }
    }
}
