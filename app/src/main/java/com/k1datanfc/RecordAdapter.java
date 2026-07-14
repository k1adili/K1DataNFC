package com.k1datanfc;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.VH> {

    public interface Listener {
        void onRecordClick(TagRecord record);
        void onRecordDelete(TagRecord record);
    }

    private final Context  context;
    private List<TagRecord> records;
    private final Listener listener;
    private final SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.US);

    public RecordAdapter(Context ctx, List<TagRecord> records, Listener l) {
        this.context  = ctx;
        this.records  = records;
        this.listener = l;
    }

    public void update(List<TagRecord> r) { this.records = r; notifyDataSetChanged(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_record, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        TagRecord r = records.get(pos);

        h.tvTitle.setText(r.getTitle() != null && !r.getTitle().isEmpty()
                ? r.getTitle() : "رکورد بدون عنوان");

        String note = r.getNote();
        if (note != null && !note.isEmpty()) {
            h.tvNote.setVisibility(View.VISIBLE);
            h.tvNote.setText(note.length() > 80 ? note.substring(0, 80) + "…" : note);
        } else {
            h.tvNote.setVisibility(View.GONE);
        }

        h.tvDate.setText(sdf.format(new Date(r.getCreatedAt())));

        int imgCount = r.getImagePaths() != null ? r.getImagePaths().size() : 0;
        h.tvImages.setText(imgCount + " 📷");
        h.tvImages.setVisibility(imgCount > 0 ? View.VISIBLE : View.GONE);

        h.itemView.setOnClickListener(v -> listener.onRecordClick(r));
        h.btnDelete.setOnClickListener(v -> listener.onRecordDelete(r));

        // Timeline connector — hide line for last item
        h.vLine.setVisibility(pos < records.size() - 1 ? View.VISIBLE : View.INVISIBLE);
    }

    @Override public int getItemCount() { return records == null ? 0 : records.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvNote, tvDate, tvImages;
        ImageButton btnDelete;
        View vLine;
        VH(View v) {
            super(v);
            tvTitle   = v.findViewById(R.id.tv_record_title);
            tvNote    = v.findViewById(R.id.tv_record_note);
            tvDate    = v.findViewById(R.id.tv_record_date);
            tvImages  = v.findViewById(R.id.tv_record_images);
            btnDelete = v.findViewById(R.id.btn_delete_record);
            vLine     = v.findViewById(R.id.v_timeline_line);
        }
    }
}
