package com.speedvolume;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 使用统计界面
 * 显示骑行统计数据和历史记录，支持导出为 CSV
 */
public class StatsActivity extends AppCompatActivity {
    private TextView tvTotalSessions;
    private TextView tvTotalDuration;
    private TextView tvTotalDistance;
    private TextView tvMaxSpeed;
    private TextView tvAvgSpeed;
    private ListView lvRecentRides;
    private Button btnExport;
    private Button btnClearData;
    private ProgressBar progressBar;
    
    private UsageStatsRepository repository;
    private SessionAdapter sessionAdapter;
    private SimpleDateFormat dateFormat;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
        
        // 初始化视图
        tvTotalSessions = findViewById(R.id.tvTotalSessions);
        tvTotalDuration = findViewById(R.id.tvTotalDuration);
        tvTotalDistance = findViewById(R.id.tvTotalDistance);
        tvMaxSpeed = findViewById(R.id.tvMaxSpeed);
        tvAvgSpeed = findViewById(R.id.tvAvgSpeed);
        lvRecentRides = findViewById(R.id.lvRecentRides);
        btnExport = findViewById(R.id.btnExport);
        btnClearData = findViewById(R.id.btnClearData);
        progressBar = findViewById(R.id.progressBar);
        
        // 初始化仓库
        repository = new UsageStatsRepository(this);
        dateFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        
        // 初始化适配器
        sessionAdapter = new SessionAdapter();
        lvRecentRides.setAdapter(sessionAdapter);
        
        // 设置按钮监听
        btnExport.setOnClickListener(v -> exportData());
        btnClearData.setOnClickListener(v -> showClearDataDialog());
        
        // 返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("使用统计");
        }
        
        // 加载数据
        loadData();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }
    
    /**
     * 加载统计数据
     */
    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);
        
        // 获取总体统计
        UsageStatsRepository.RideStats stats = repository.getTotalStats();
        
        // 更新统计显示
        tvTotalSessions.setText(String.valueOf(stats.totalSessions));
        tvTotalDuration.setText(UsageStatsRepository.formatDuration(stats.totalDuration));
        tvTotalDistance.setText(UsageStatsRepository.formatDistance(stats.totalDistance));
        tvMaxSpeed.setText(UsageStatsRepository.formatSpeed(stats.maxSpeedEver));
        tvAvgSpeed.setText(UsageStatsRepository.formatSpeed(stats.avgSpeed));
        
        // 获取最近骑行记录
        List<UsageStatsRepository.RideSession> sessions = repository.getRecentSessions(20);
        sessionAdapter.setSessions(sessions);
        
        progressBar.setVisibility(View.GONE);
        
        // 显示空状态提示
        if (sessions.isEmpty()) {
            Toast.makeText(this, "暂无骑行记录", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 导出数据为 CSV
     */
    private void exportData() {
        // 创建导出文件
        File exportDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "exports");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        
        String fileName = "ride_stats_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".csv";
        File exportFile = new File(exportDir, fileName);
        
        progressBar.setVisibility(View.VISIBLE);
        
        // 执行导出
        boolean success = repository.exportToCsv(exportFile);
        
        progressBar.setVisibility(View.GONE);
        
        if (success) {
            // 显示成功对话框，提供分享选项
            showExportSuccessDialog(exportFile);
        } else {
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 显示导出成功对话框
     */
    private void showExportSuccessDialog(File file) {
        new AlertDialog.Builder(this)
            .setTitle("导出成功")
            .setMessage("数据已导出到:\n" + file.getAbsolutePath())
            .setPositiveButton("分享", (dialog, which) -> shareFile(file))
            .setNegativeButton("关闭", null)
            .show();
    }
    
    /**
     * 分享导出文件
     */
    private void shareFile(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/csv");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        startActivity(Intent.createChooser(shareIntent, "分享骑行数据"));
    }
    
    /**
     * 显示清除数据确认对话框
     */
    private void showClearDataDialog() {
        new AlertDialog.Builder(this)
            .setTitle("清除数据")
            .setMessage("确定要清除所有骑行记录吗？此操作不可恢复。")
            .setPositiveButton("清除", (dialog, which) -> clearAllData())
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 清除所有数据
     */
    private void clearAllData() {
        repository.clearAllData();
        loadData();
        Toast.makeText(this, "数据已清除", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (repository != null) {
            repository.close();
        }
    }
    
    /**
     * 骑行会话列表适配器
     */
    private class SessionAdapter extends BaseAdapter {
        private List<UsageStatsRepository.RideSession> sessions;
        
        public void setSessions(List<UsageStatsRepository.RideSession> sessions) {
            this.sessions = sessions;
            notifyDataSetChanged();
        }
        
        @Override
        public int getCount() {
            return sessions != null ? sessions.size() : 0;
        }
        
        @Override
        public Object getItem(int position) {
            return sessions != null ? sessions.get(position) : null;
        }
        
        @Override
        public long getItemId(int position) {
            return position;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            
            if (convertView == null) {
                convertView = LayoutInflater.from(StatsActivity.this)
                    .inflate(R.layout.item_ride_session, parent, false);
                holder = new ViewHolder();
                holder.tvDate = convertView.findViewById(R.id.tvDate);
                holder.tvDuration = convertView.findViewById(R.id.tvDuration);
                holder.tvDistance = convertView.findViewById(R.id.tvDistance);
                holder.tvMaxSpeed = convertView.findViewById(R.id.tvMaxSpeed);
                holder.tvAvgSpeed = convertView.findViewById(R.id.tvAvgSpeed);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            
            UsageStatsRepository.RideSession session = sessions.get(position);
            
            holder.tvDate.setText(dateFormat.format(new Date(session.startTime)));
            holder.tvDuration.setText(UsageStatsRepository.formatDuration(session.duration));
            holder.tvDistance.setText(UsageStatsRepository.formatDistance(session.distance));
            holder.tvMaxSpeed.setText(UsageStatsRepository.formatSpeed(session.maxSpeed));
            holder.tvAvgSpeed.setText(UsageStatsRepository.formatSpeed(session.avgSpeed));
            
            return convertView;
        }
        
        class ViewHolder {
            TextView tvDate;
            TextView tvDuration;
            TextView tvDistance;
            TextView tvMaxSpeed;
            TextView tvAvgSpeed;
        }
    }
}
