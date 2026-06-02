package com.deepsleep.memory.ui.treasure_view.composition_view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.deepsleep.memory.R;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CompositionRecordAdapter extends BaseAdapter {
    private Context context;
    private List<CompositionRecord> records;
    private LayoutInflater inflater;

    public CompositionRecordAdapter(Context context, List<CompositionRecord> records) {
        this.context = context;
        this.records = records;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return records.size();
    }

    @Override
    public Object getItem(int position) {
        return records.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.composition_records_item, parent, false);
            holder = new ViewHolder();
            holder.previewContent = convertView.findViewById(R.id.preview_content);
            holder.score = convertView.findViewById(R.id.score);
            holder.time = convertView.findViewById(R.id.time);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        CompositionRecord record = records.get(position);
        holder.previewContent.setText(record.getPreviewContent());
        holder.score.setText(record.getScore());

        // 格式化时间显示
        String formattedTime = formatTime(record.getCreatedTime());
        holder.time.setText(formattedTime);

        return convertView;
    }

    private String formatTime(String createdTime) {
        if (createdTime == null || createdTime.isEmpty()) {
            return "";
        }

        try {
            // 输入格式: "2025-08-16 21:17:00.0"
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S", Locale.getDefault());
            Date date = inputFormat.parse(createdTime);

            // 输出格式: "2025-08-16 21:17"
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            return outputFormat.format(date);
        } catch (Exception e) {
            e.printStackTrace();
            return createdTime;
        }
    }

    static class ViewHolder {
        TextView previewContent;
        TextView score;
        TextView time;
    }
}
