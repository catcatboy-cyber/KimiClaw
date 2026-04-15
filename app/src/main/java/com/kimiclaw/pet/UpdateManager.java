package com.kimiclaw.pet;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

public class UpdateManager {

    private static final String TAG = "UpdateManager";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/catcatboy-cyber/KimiClaw/releases/latest";
    private static final String GITHUB_RELEASES_URL = "https://github.com/catcatboy-cyber/KimiClaw/releases";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private Context context;
    private SharedPreferences prefs;
    private ExecutorService executor;
    private Handler mainHandler;

    private long downloadId = -1;
    private DownloadManager downloadManager;
    private BroadcastReceiver downloadReceiver;

    // 临时存储下载URL，等待权限授予后使用
    private String pendingDownloadUrl = null;

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
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
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
        builder.setTitle("发现新版本！");
        builder.setMessage(releaseName + "\n\n" + releaseNotes + "\n\n文件大小: " + sizeText);
        builder.setPositiveButton("立即更新", (dialog, which) -> {
            if (downloadUrl != null) {
                // 使用浏览器下载
                openBrowserDownload(downloadUrl);
            } else {
                Toast.makeText(context, "下载链接无效", Toast.LENGTH_SHORT).show();
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
     * 使用浏览器下载APK
     */
    private void openBrowserDownload(String downloadUrl) {
        try {
            // 使用系统浏览器下载
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(downloadUrl));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);

            // 检查是否有浏览器可以处理
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                Toast.makeText(context, "请在浏览器中下载APK，下载完成后点击安装", Toast.LENGTH_LONG).show();
            } else {
                // 如果没有浏览器，使用DownloadManager
                downloadWithDownloadManager(downloadUrl);
            }
        } catch (Exception e) {
            Log.e(TAG, "Browser download error: " + e.getMessage(), e);
            // 备用方案：使用DownloadManager
            downloadWithDownloadManager(downloadUrl);
        }
    }

    /**
     * 使用系统DownloadManager下载
     */
    private void downloadWithDownloadManager(String downloadUrl) {
        try {
            // 删除旧文件
            File oldFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "KimiClaw_update.apk");
            if (oldFile.exists()) {
                oldFile.delete();
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle("KimiClaw 更新下载");
            request.setDescription("正在下载最新版本...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "KimiClaw_update.apk");
            request.setMimeType("application/vnd.android.package-archive");
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);

            // 添加Cookie（GitHub可能需要）
            request.addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10)");

            downloadId = downloadManager.enqueue(request);

            Toast.makeText(context, "开始下载更新，请在通知栏查看进度", Toast.LENGTH_LONG).show();

            // 注册下载完成监听
            registerDownloadReceiver();

        } catch (Exception e) {
            Log.e(TAG, "DownloadManager error: " + e.getMessage(), e);
            Toast.makeText(context, "下载启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 检查权限并开始下载（保留用于兼容性）
     */
    private void startDownloadWithPermissionCheck(String downloadUrl) {
        // 直接使用浏览器下载，不需要存储权限
        openBrowserDownload(downloadUrl);
    }

    /**
     * 显示权限请求对话框（保留用于兼容性）
     */
    private void showPermissionRequestDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("需要存储权限");
        builder.setMessage("下载更新需要存储权限。\n\n" +
                "鸿蒙系统设置路径：设置 → 应用 → KimiClaw → 权限 → 存储");

        builder.setPositiveButton("去设置", (dialog, which) -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", context.getPackageName(), null);
            intent.setData(uri);
            context.startActivity(intent);
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 处理权限请求结果（保留用于兼容性）
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // 不再使用
    }

    /**
     * 注册下载完成监听
     */
    private void registerDownloadReceiver() {
        if (downloadReceiver != null) {
            try {
                context.unregisterReceiver(downloadReceiver);
            } catch (Exception e) {
                // 忽略
            }
        }

        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    // 查询下载状态
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadId);
                    Cursor cursor = downloadManager.query(query);

                    if (cursor != null && cursor.moveToFirst()) {
                        int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        int status = cursor.getInt(statusIndex);

                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            // 下载成功，安装APK
                            mainHandler.post(() -> {
                                File apkFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                        "KimiClaw_update.apk");
                                installApkFile(apkFile);
                            });
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                            int reason = cursor.getInt(reasonIndex);
                            Log.e(TAG, "Download failed, reason: " + reason);
                            mainHandler.post(() -> Toast.makeText(context, "下载失败，请重试", Toast.LENGTH_SHORT).show());
                        }

                        cursor.close();
                    }
                }
            }
        };

        context.registerReceiver(downloadReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    /**
     * 安装APK文件
     */
    private void installApkFile(File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(context, "APK文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查文件大小
        long fileSize = apkFile.length();
        Log.d(TAG, "APK file size: " + fileSize + " bytes, path: " + apkFile.getAbsolutePath());

        if (fileSize < 100000) {  // 小于100KB认为不完整
            Toast.makeText(context, "下载文件不完整(" + fileSize + "字节)，请重试", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ 使用 FileProvider
                apkUri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".fileprovider", apkFile);
                Log.d(TAG, "FileProvider URI: " + apkUri.toString());
            } else {
                apkUri = Uri.fromFile(apkFile);
            }

            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");

            // 检查是否有应用可以处理这个intent
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            } else {
                Toast.makeText(context, "无法打开安装界面，请手动安装", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Install error: " + e.getMessage(), e);
            Toast.makeText(context, "安装失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
        }
            }
        });
    }

    /**
     * 安装APK文件
     */
    private void installApkFile(File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(context, "APK文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查文件大小
        long fileSize = apkFile.length();
        Log.d(TAG, "APK file size: " + fileSize + " bytes, path: " + apkFile.getAbsolutePath());

        if (fileSize < 100000) {  // 小于100KB认为不完整
            Toast.makeText(context, "下载文件不完整(" + fileSize + "字节)，请重试", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ 使用 FileProvider
                apkUri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".fileprovider", apkFile);
                Log.d(TAG, "FileProvider URI: " + apkUri.toString());
            } else {
                apkUri = Uri.fromFile(apkFile);
            }

            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");

            // 检查是否有应用可以处理这个intent
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            } else {
                Toast.makeText(context, "无法打开安装界面，请手动安装", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Install error: " + e.getMessage(), e);
            Toast.makeText(context, "安装失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
