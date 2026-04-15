package com.kimiclaw.pet;

import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateManager {

    private static final String TAG = "UpdateManager";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/catcatboy-cyber/KimiClaw/releases/latest";
    private static final String GITHUB_RELEASES_URL = "https://github.com/catcatboy-cyber/KimiClaw/releases";

    private Context context;
    private SharedPreferences prefs;
    private ExecutorService executor;
    private Handler mainHandler;

    private long downloadId = -1;
    private DownloadManager downloadManager;
    private BroadcastReceiver downloadReceiver;

    public UpdateManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences("KimiClawPrefs", Context.MODE_PRIVATE);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    /**
     * 检查更新
     */
    public void checkForUpdate(boolean showNoUpdateToast) {
        executor.execute(() -> {
            try {
                URL url = new URL(GITHUB_API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
                    );
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    parseReleaseInfo(response.toString(), showNoUpdateToast);
                } else {
                    mainHandler.post(() -> {
                        if (showNoUpdateToast) {
                            Toast.makeText(context, "检查更新失败，请稍后重试", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Check update error", e);
                mainHandler.post(() -> {
                    if (showNoUpdateToast) {
                        Toast.makeText(context, "网络错误，请检查网络连接", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /**
     * 解析Release信息
     */
    private void parseReleaseInfo(String jsonResponse, boolean showNoUpdateToast) {
        try {
            JSONObject release = new JSONObject(jsonResponse);
            String tagName = release.getString("tag_name");
            String releaseName = release.getString("name");
            String body = release.getString("body");
            String publishedAt = release.getString("published_at");

            // 获取最新版本号
            int latestVersion = parseVersionNumber(tagName);
            int currentVersion = getCurrentVersionCode();

            Log.d(TAG, "Current version: " + currentVersion + ", Latest version: " + latestVersion);

            // 获取APK下载链接
            JSONArray assets = release.getJSONArray("assets");
            String downloadUrl = null;
            long fileSize = 0;

            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String name = asset.getString("name");
                if (name.endsWith(".apk")) {
                    downloadUrl = asset.getString("browser_download_url");
                    fileSize = asset.getLong("size");
                    break;
                }
            }

            final String finalDownloadUrl = downloadUrl;
            final long finalFileSize = fileSize;
            final String finalReleaseName = releaseName;
            final String finalBody = body;

            if (latestVersion > currentVersion) {
                // 有新版本
                mainHandler.post(() -> showUpdateDialog(finalReleaseName, finalBody, finalDownloadUrl, finalFileSize));
            } else {
                // 已是最新版本
                if (showNoUpdateToast) {
                    mainHandler.post(() -> Toast.makeText(context, "已是最新版本！", Toast.LENGTH_SHORT).show());
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Parse release error", e);
        }
    }

    /**
     * 解析版本号
     */
    private int parseVersionNumber(String tagName) {
        try {
            // 移除 'v' 前缀
            String version = tagName.replace("v", "");
            return Integer.parseInt(version);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 获取当前版本号
     */
    private int getCurrentVersionCode() {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 1;
        }
    }

    /**
     * 显示更新对话框
     */
    private void showUpdateDialog(String releaseName, String releaseNotes, String downloadUrl, long fileSize) {
        String sizeText = formatFileSize(fileSize);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("🎉 发现新版本！");
        builder.setMessage(releaseName + "\n\n" + releaseNotes + "\n\n文件大小: " + sizeText);
        builder.setPositiveButton("立即更新", (dialog, which) -> {
            if (downloadUrl != null) {
                downloadAndInstall(downloadUrl);
            } else {
                // 如果没有找到APK链接，打开浏览器
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL));
                context.startActivity(intent);
            }
        });
        builder.setNegativeButton("稍后提醒", null);
        builder.setNeutralButton("去GitHub查看", (dialog, which) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL));
            context.startActivity(intent);
        });
        builder.show();
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        }
    }

    /**
     * 下载并安装APK
     */
    private void downloadAndInstall(String downloadUrl) {
        // 检查存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "需要存储权限才能下载", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // 删除旧文件
        File oldFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "KimiClaw_update.apk");
        if (oldFile.exists()) {
            oldFile.delete();
        }

        // 创建下载请求
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
        request.setTitle("KimiClaw 更新下载");
        request.setDescription("正在下载最新版本...");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "KimiClaw_update.apk");
        request.setMimeType("application/vnd.android.package-archive");

        // 开始下载
        downloadId = downloadManager.enqueue(request);

        Toast.makeText(context, "开始下载更新...", Toast.LENGTH_SHORT).show();

        // 注册下载完成监听
        registerDownloadReceiver();
    }

    /**
     * 注册下载完成监听
     */
    private void registerDownloadReceiver() {
        if (downloadReceiver != null) {
            context.unregisterReceiver(downloadReceiver);
        }

        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    installApk();
                }
            }
        };

        context.registerReceiver(downloadReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    /**
     * 安装APK
     */
    private void installApk() {
        File apkFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "KimiClaw_update.apk");

        if (!apkFile.exists()) {
            Toast.makeText(context, "下载文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Android 7.0+ 使用 FileProvider
            Uri apkUri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", apkFile);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        } else {
            intent.setDataAndType(Uri.fromFile(apkFile),
                    "application/vnd.android.package-archive");
        }

        context.startActivity(intent);
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        if (downloadReceiver != null) {
            try {
                context.unregisterReceiver(downloadReceiver);
            } catch (Exception e) {
                // 忽略
            }
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
