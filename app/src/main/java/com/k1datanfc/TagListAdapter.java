package com.k1datanfc;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TagListAdapter extends RecyclerView.Adapter<TagListAdapter.ViewHolder> {

    public interface TagClickListener {
        void onTagClick(NfcTag tag);
        void onTagLongClick(NfcTag tag);
    }

    private final Context context;
    private List<NfcTag> tags;
    private final TagClickListener listener;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US);

    public TagListAdapter(Context context, List<NfcTag> tags, TagClickListener listener) {
        this.context  = context;
        this.tags     = tags;
        this.listener = listener;
    }

    public void updateTags(List<NfcTag> newTags) {
        this.tags = newTags;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_tag, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NfcTag tag = tags.get(position);

        // Name
        String name = tag.getName();
        holder.tvName.setText(name != null && !name.isEmpty() ? name : "تگ بدون نام");

        // Show preview of the latest record's title (index 0 = newest)
        List<TagRecord> records = tag.getRecords();
        if (records != null && !records.isEmpty()) {
            TagRecord latest = records.get(0);
            holder.tvNote.setVisibility(View.VISIBLE);
            String preview = latest.getTitle() != null && !latest.getTitle().isEmpty()
                    ? latest.getTitle()
                    : (latest.getNote() != null ? latest.getNote() : "");
            if (preview.length() > 60) preview = preview.substring(0, 60) + "…";
            holder.tvNote.setText(preview);
        } else {
            holder.tvNote.setVisibility(View.GONE);
        }

        // Tag ID
        holder.tvTagId.setText("ID: " + tag.getTagId());

        // Last scanned date
        holder.tvDate.setText(sdf.format(new Date(tag.getLastScannedAt())));

        // Record count
        int recCount = records != null ? records.size() : 0;
        holder.tvImages.setText(recCount + " رکورد");

        holder.itemView.setOnClickListener(v -> listener.onTagClick(tag));
        holder.itemView.setOnLongClickListener(v -> { listener.onTagLongClick(tag); return true; });
    }

    @Override
    public int getItemCount() { return tags == null ? 0 : tags.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvNote, tvTagId, tvDate, tvImages;
        ViewHolder(View v) {
            super(v);
            tvName   = v.findViewById(R.id.tv_tag_name);
            tvNote   = v.findViewById(R.id.tv_tag_note);
            tvTagId  = v.findViewById(R.id.tv_tag_id);
            tvDate   = v.findViewById(R.id.tv_date);
            tvImages = v.findViewById(R.id.tv_images);
        }
    }
}
