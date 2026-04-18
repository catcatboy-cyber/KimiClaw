package com.kimiclaw.pet;

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

import javax.net.ssl.HttpsURLConnection;

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

                    // 成功检查后更新时间戳
                    prefs.edit().putLong("last_update_check", System.currentTimeMillis()).apply();
                } else {
                    String errorMsg = getErrorMessage(responseCode);
                    mainHandler.post(() -> {
                        if (showNoUpdateToast) {
                            Toast.makeText(context, "检查更新失败 (" + responseCode + "): " + errorMsg, Toast.LENGTH_LONG).show();
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
     * 获取错误码对应的消息
     */
    private String getErrorMessage(int code) {
        switch (code) {
            case 301:
            case 302:
            case 307:
            case 308:
                return "重定向错误";
            case 400:
                return "请求参数错误";
            case 401:
                return "未授权访问";
            case 403:
                return "访问被拒绝(API限流)";
            case 404:
                return "资源不存在";
            case 408:
                return "请求超时";
            case 429:
                return "请求过于频繁";
            case 500:
                return "服务器内部错误";
            case 502:
                return "网关错误";
            case 503:
                return "服务不可用";
            case 504:
                return "网关超时";
            default:
                if (code >= 500) {
                    return "服务器错误";
                } else if (code >= 400) {
                    return "客户端错误";
                } else {
                    return "未知错误";
                }
        }
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

            int latestVersion = parseVersionNumber(tagName);
            int currentVersion = getCurrentVersionCode();

            Log.d(TAG, "Current version: " + currentVersion + " (from app)");
            Log.d(TAG, "Latest version: " + latestVersion + " (from GitHub tag: " + tagName + ")");
            Log.d(TAG, "Need update: " + (latestVersion > currentVersion));

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
                mainHandler.post(() -> showUpdateDialog(finalReleaseName, finalBody, finalDownloadUrl, finalFileSize));
            } else {
                if (showNoUpdateToast) {
                    mainHandler.post(() -> Toast.makeText(context, "已是最新版本！", Toast.LENGTH_SHORT).show());
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Parse release error", e);
        }
    }

    /**
     * 解析版本号 - 支持 v1.2.3 格式
     */
    private int parseVersionNumber(String tagName) {
        try {
            String version = tagName.replaceAll("[^0-9.]", "");
            String[] parts = version.split("\\.");
            int result = 0;
            for (String part : parts) {
                result = result * 1000 + Integer.parseInt(part);
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Version parse error for: " + tagName, e);
            return 0;
        }
    }

    /**
     * 获取当前版本号 - 直接使用 versionCode
     */
    private int getCurrentVersionCode() {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) packageInfo.getLongVersionCode();
            } else {
                return packageInfo.versionCode;
            }
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
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
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(downloadUrl));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);

            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                Toast.makeText(context, "请在浏览器中下载APK，下载完成后点击安装", Toast.LENGTH_LONG).show();
            } else {
                downloadWithDownloadManager(downloadUrl);
            }
        } catch (Exception e) {
            Log.e(TAG, "Browser download error: " + e.getMessage(), e);
            downloadWithDownloadManager(downloadUrl);
        }
    }

    /**
     * 使用系统DownloadManager下载
     */
    private void downloadWithDownloadManager(String downloadUrl) {
        try {
            Log.d(TAG, "Starting download with DownloadManager: " + downloadUrl);

            File oldFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "KimiClaw_update.apk");
            if (oldFile.exists()) {
                boolean deleted = oldFile.delete();
                Log.d(TAG, "Old APK file deleted: " + deleted);
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle("KimiClaw 更新下载");
            request.setDescription("正在下载最新版本...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "KimiClaw_update.apk");
            request.setMimeType("application/vnd.android.package-archive");
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            // 不添加自定义User-Agent，让系统默认处理

            downloadId = downloadManager.enqueue(request);
            Log.d(TAG, "Download enqueued with ID: " + downloadId);

            Toast.makeText(context, "开始下载更新，请在通知栏查看进度", Toast.LENGTH_LONG).show();

            registerDownloadReceiver();

        } catch (Exception e) {
            Log.e(TAG, "DownloadManager error: " + e.getMessage(), e);
            Toast.makeText(context, "下载启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 注册下载完成监听
     */
    private void registerDownloadReceiver() {
        if (downloadReceiver != null) {
            try {
                context.unregisterReceiver(downloadReceiver);
                Log.d(TAG, "Unregistered old download receiver");
            } catch (Exception e) {
                Log.d(TAG, "No old receiver to unregister");
            }
        }

        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "BroadcastReceiver.onReceive called, action: " + intent.getAction());

                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                Log.d(TAG, "Received download ID: " + id + ", expected: " + downloadId);

                if (id == downloadId) {
                    Log.d(TAG, "Download ID matched, querying download status");
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadId);
                    Cursor cursor = downloadManager.query(query);

                    if (cursor != null && cursor.moveToFirst()) {
                        int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        int status = cursor.getInt(statusIndex);
                        Log.d(TAG, "Download status: " + status + " (8=SUCCESS, 16=FAILED)");

                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            // 获取实际下载的文件路径
                            int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                            String localUri = cursor.getString(uriIndex);
                            Log.d(TAG, "Downloaded file URI: " + localUri);

                            mainHandler.post(() -> {
                                Log.d(TAG, "Posting install task to main thread");
                                if (localUri != null) {
                                    // 使用 DownloadManager 返回的 URI
                                    Uri apkUri = Uri.parse(localUri);
                                    Log.d(TAG, "Calling installApkFromUri with: " + apkUri);
                                    installApkFromUri(apkUri);
                                } else {
                                    // 备用：使用硬编码路径
                                    File apkFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                            "KimiClaw_update.apk");
                                    Log.d(TAG, "Calling installApkFile with: " + apkFile.getAbsolutePath());
                                    installApkFile(apkFile);
                                }
                            });
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                            int reason = cursor.getInt(reasonIndex);
                            Log.e(TAG, "Download failed, reason: " + reason);
                            mainHandler.post(() -> Toast.makeText(context, "下载失败，请重试", Toast.LENGTH_SHORT).show());
                        } else {
                            Log.d(TAG, "Download status is neither SUCCESS nor FAILED: " + status);
                        }

                        cursor.close();
                    } else {
                        Log.e(TAG, "Cursor is null or empty");
                    }
                } else {
                    Log.d(TAG, "Download ID mismatch, ignoring");
                }
            }
        };

        context.registerReceiver(downloadReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        Log.d(TAG, "Download receiver registered for downloadId: " + downloadId);
    }

    /**
     * 使用URI直接安装APK（从DownloadManager）
     * 增加文件头校验 + content URI 转私有缓存 FileProvider URI，解决安装器无权限问题
     */
    private void installApkFromUri(Uri apkUri) {
        Log.d(TAG, "installApkFromUri called with URI: " + apkUri.toString());

        android.os.ParcelFileDescriptor pfd = null;
        try {
            // 1. 读取文件头校验（支持 content:// 和 file://）
            byte[] header = new byte[4];
            int read = 0;

            if ("content".equals(apkUri.getScheme())) {
                Log.d(TAG, "Reading from content URI");
                java.io.InputStream is = context.getContentResolver().openInputStream(apkUri);
                if (is != null) {
                    read = is.read(header);
                    is.close();
                }
            } else if ("file".equals(apkUri.getScheme())) {
                Log.d(TAG, "Reading from file URI");
                // Android 10+ 分区存储：不能直接访问 file:// 路径
                // 需要通过 DownloadManager 的 openDownloadedFile 或 ContentResolver
                try {
                    // 尝试通过 DownloadManager 打开
                    pfd = downloadManager.openDownloadedFile(downloadId);
                    if (pfd != null) {
                        java.io.FileInputStream fis = new java.io.FileInputStream(pfd.getFileDescriptor());
                        read = fis.read(header);
                        fis.close();
                        Log.d(TAG, "Successfully read via DownloadManager.openDownloadedFile");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to read via DownloadManager, trying direct file access", e);
                    // 降级：尝试直接文件访问（可能在旧版本 Android 上工作）
                    String path = apkUri.getPath();
                    if (path != null && path.startsWith("/raw:")) {
                        path = path.substring(5);
                    }
                    java.io.FileInputStream fis = new java.io.FileInputStream(path);
                    read = fis.read(header);
                    fis.close();
                }
            }

            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(read, 4); i++) {
                hex.append(String.format("%02X ", header[i]));
            }
            Log.d(TAG, "Downloaded file header: " + hex.toString().trim());

            boolean isValidApk = read >= 2 && header[0] == 0x50 && header[1] == 0x4B;
            if (!isValidApk) {
                Log.e(TAG, "Downloaded file is not a valid APK");
                showInstallErrorDialog("下载文件异常",
                        hex.toString().trim(),
                        "不是有效APK（可能下载到了网页）",
                        0,
                        "请尝试用浏览器访问 GitHub Release 页面手动下载安装");
                return;
            }

            // 2. 统一拷贝到应用私有缓存目录，再用 FileProvider 生成带权限的 URI
            Log.d(TAG, "Copying APK to cache directory");
            File cacheApk = new File(context.getCacheDir(), "KimiClaw_update.apk");

            // 删除旧的缓存文件
            if (cacheApk.exists()) {
                boolean deleted = cacheApk.delete();
                Log.d(TAG, "Old cache APK deleted: " + deleted);
            }

            copyUriToFile(apkUri, cacheApk);

            String authority = context.getPackageName() + ".fileprovider";
            // 检查是否有安装未知来源应用的权限（Android 8.0+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && !context.getPackageManager().canRequestPackageInstalls()) {
                Log.w(TAG, "Missing install permission for Android 8.0+");
                showInstallPermissionDialog();
                return;
            }

            Uri installUri = FileProvider.getUriForFile(context, authority, cacheApk);
            Log.d(TAG, "Generated FileProvider URI: " + installUri);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(installUri, "application/vnd.android.package-archive");

            Log.d(TAG, "Starting install activity");
            context.startActivity(intent);
            Log.d(TAG, "Install activity started successfully");
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            Log.e(TAG, "Install error: " + errorMsg, e);
            Toast.makeText(context, "安装失败: " + errorMsg, Toast.LENGTH_LONG).show();
        } finally {
            // 关闭 ParcelFileDescriptor 防止泄漏
            if (pfd != null) {
                try {
                    pfd.close();
                    Log.d(TAG, "ParcelFileDescriptor closed");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to close ParcelFileDescriptor", e);
                }
            }
        }
    }

    /**
     * 将 URI 指向的文件拷贝到本地文件
     */
    private void copyUriToFile(Uri sourceUri, File destFile) throws Exception {
        java.io.InputStream in = null;
        java.io.FileOutputStream out = null;
        android.os.ParcelFileDescriptor pfd = null;
        try {
            if ("content".equals(sourceUri.getScheme())) {
                in = context.getContentResolver().openInputStream(sourceUri);
            } else if ("file".equals(sourceUri.getScheme())) {
                // Android 10+ 分区存储：优先使用 DownloadManager 打开
                try {
                    pfd = downloadManager.openDownloadedFile(downloadId);
                    if (pfd != null) {
                        in = new java.io.FileInputStream(pfd.getFileDescriptor());
                        Log.d(TAG, "Opened file via DownloadManager.openDownloadedFile");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to open via DownloadManager, trying direct file access", e);
                    // 降级：尝试直接文件访问
                    String path = sourceUri.getPath();
                    if (path != null && path.startsWith("/raw:")) {
                        path = path.substring(5);
                    }
                    in = new java.io.FileInputStream(path);
                }
            }
            if (in == null) {
                throw new Exception("Cannot open input stream for URI: " + sourceUri);
            }
            out = new java.io.FileOutputStream(destFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            Log.d(TAG, "Copied APK to cache: " + destFile.getAbsolutePath() + " (" + destFile.length() + " bytes)");
        } finally {
            if (in != null) in.close();
            if (out != null) out.close();
            if (pfd != null) {
                pfd.close();
                Log.d(TAG, "ParcelFileDescriptor closed in copyUriToFile");
            }
        }
    }

    /**
     * 安装APK文件
     */
    private void installApkFile(File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(context, "APK文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        long fileSize = apkFile.length();
        final String[] fileHeader = new String[]{"未检查"};
        final String[] headerType = new String[]{"未知"};

        // 校验文件头，确认是 ZIP/APK 格式
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(apkFile);
            byte[] header = new byte[4];
            int read = fis.read(header);
            fis.close();
            
            // 保存文件头信息
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(read, 4); i++) {
                hex.append(String.format("%02X ", header[i]));
            }
            fileHeader[0] = hex.toString().trim();
            
            // 判断文件类型
            if (read >= 2 && header[0] == 0x50 && header[1] == 0x4B) {
                headerType[0] = "正常APK文件";
            } else if (read >= 2 && header[0] == 0x3C && header[1] == 0x21) {
                headerType[0] = "HTML页面(网络拦截)";
            } else {
                headerType[0] = "未知/损坏";
            }
            
            Log.d(TAG, "File header: " + fileHeader[0] + ", Type: " + headerType[0]);
            
        } catch (Exception e) {
            fileHeader[0] = "读取失败";
            headerType[0] = e.getMessage();
        }

        if (fileSize < 100000) {
            showInstallErrorDialog("文件不完整", fileHeader[0], headerType[0], fileSize, "文件太小");
            return;
        }

        if (!headerType[0].equals("正常APK文件")) {
            showInstallErrorDialog("文件头错误", fileHeader[0], headerType[0], fileSize, "不是有效APK");
            return;
        }

        // 检查是否有安装未知来源应用的权限（Android 8.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !context.getPackageManager().canRequestPackageInstalls()) {
            showInstallPermissionDialog();
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                String authority = context.getPackageName() + ".fileprovider";
                apkUri = FileProvider.getUriForFile(context, authority, apkFile);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }

            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            } else {
                Toast.makeText(context, "无法打开安装界面，请手动安装", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && (
                    errorMsg.contains("SIGNATURES") ||
                    errorMsg.contains("signature") ||
                    errorMsg.contains("conflict"))) {
                showInstallErrorDialog("签名不匹配", fileHeader[0], headerType[0], fileSize, errorMsg);
            } else {
                showInstallErrorDialog("安装失败", fileHeader[0], headerType[0], fileSize, errorMsg);
            }
        }
    }

    /**
     * 显示安装错误对话框
     */
    private void showInstallErrorDialog(String title, String header, String type, long size, String error) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("❌ " + title);
        builder.setMessage(
            "文件头: " + header + "\n" +
            "类型: " + type + "\n" +
            "文件大小: " + formatFileSize(size) + "\n\n" +
            "错误信息:\n" + (error != null ? error : "未知错误") + "\n\n" +
            "可能原因:\n" +
            "• 签名不匹配（需卸载重装）\n" +
            "• 下载被拦截（用浏览器下载）\n" +
            "• 文件损坏（重新下载）"
        );
        builder.setPositiveButton("去GitHub下载", (dialog, which) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL));
            context.startActivity(intent);
        });
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    /**
     * 显示签名错误对话框
     */
    private void showSignatureErrorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("签名不匹配");
        builder.setMessage("检测到签名不一致！\n\n可能原因：\n1. 旧版本是Debug签名，新版本是Release签名\n2. 从不同渠道安装的APK\n\n解决方法：\n请先卸载当前版本，再安装新版本\n（卸载后配置会丢失，建议先备份）");
        builder.setPositiveButton("去GitHub下载", (dialog, which) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL));
            context.startActivity(intent);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 显示手动安装对话框
     */
    private void showManualInstallDialog(File apkFile) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("自动安装失败");
        builder.setMessage("APK已下载到:\n" + apkFile.getAbsolutePath() + "\n\n请手动安装：\n1. 打开文件管理器\n2. 找到下载目录\n3. 点击KimiClaw_update.apk安装\n\n如果提示签名冲突，请先卸载旧版本");
        builder.setPositiveButton("知道了", null);
        builder.show();
    }

    /**
     * 显示安装权限引导对话框（Android 8.0+）
     */
    private void showInstallPermissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("需要安装权限");
        builder.setMessage("Android 8.0+ 需要单独授予「安装未知来源应用」权限才能自动更新\n\n请在设置中打开此权限后重试");
        builder.setPositiveButton("去开启", (dialog, which) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                context.startActivity(intent);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
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
