package com.kimiclaw.pet;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ConfigManager {

    private static final String TAG = "ConfigManager";
    private static final String CONFIG_FILE_NAME = "kimiclaw_config.json";
    private static final String PREFS_NAME = "KimiClawPrefs";

    private Context context;
    private SharedPreferences prefs;

    public ConfigManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 导出配置到外部存储
     */
    public boolean exportConfig() {
        try {
            // 获取下载目录
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }

            File configFile = new File(downloadDir, CONFIG_FILE_NAME);

            // 构建配置JSON
            JSONObject config = new JSONObject();

            // 导出所有配置项
            Map<String, ?> allPrefs = prefs.getAll();
            for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof String) {
                    config.put(key, value);
                } else if (value instanceof Integer) {
                    config.put(key, value);
                } else if (value instanceof Boolean) {
                    config.put(key, value);
                } else if (value instanceof Long) {
                    config.put(key, value);
                } else if (value instanceof Float) {
                    config.put(key, value);
                } else if (value instanceof Set) {
                    // 处理Set类型（如联系人列表）
                    JSONArray array = new JSONArray();
                    for (Object item : (Set<?>) value) {
                        array.put(item.toString());
                    }
                    config.put(key, array);
                }
            }

            // 写入文件
            FileWriter writer = new FileWriter(configFile);
            writer.write(config.toString(2));
            writer.close();

            Log.d(TAG, "Config exported to: " + configFile.getAbsolutePath());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Export config error: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 从外部存储导入配置
     */
    public boolean importConfig() {
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File configFile = new File(downloadDir, CONFIG_FILE_NAME);

            if (!configFile.exists()) {
                Log.d(TAG, "Config file not found: " + configFile.getAbsolutePath());
                return false;
            }

            // 读取配置文件
            StringBuilder jsonString = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(configFile));
            String line;
            while ((line = reader.readLine()) != null) {
                jsonString.append(line);
            }
            reader.close();

            // 解析JSON
            JSONObject config = new JSONObject(jsonString.toString());
            SharedPreferences.Editor editor = prefs.edit();

            // 导入所有配置项
            Iterator<String> keys = config.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = config.get(key);

                if (value instanceof String) {
                    editor.putString(key, (String) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Long) {
                    editor.putLong(key, (Long) value);
                } else if (value instanceof Float) {
                    editor.putFloat(key, (Float) value);
                } else if (value instanceof JSONArray) {
                    // 处理Set类型
                    JSONArray array = (JSONArray) value;
                    Set<String> set = new java.util.HashSet<>();
                    for (int i = 0; i < array.length(); i++) {
                        set.add(array.getString(i));
                    }
                    editor.putStringSet(key, set);
                }
            }

            editor.apply();
            Log.d(TAG, "Config imported successfully");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Import config error: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 检查是否有可导入的配置
     */
    public boolean hasBackupConfig() {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File configFile = new File(downloadDir, CONFIG_FILE_NAME);
        return configFile.exists();
    }

    /**
     * 获取配置文件路径
     */
    public String getConfigFilePath() {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File configFile = new File(downloadDir, CONFIG_FILE_NAME);
        return configFile.getAbsolutePath();
    }

    /**
     * 清除配置备份
     */
    public boolean clearBackup() {
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File configFile = new File(downloadDir, CONFIG_FILE_NAME);
            if (configFile.exists()) {
                return configFile.delete();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Clear backup error: " + e.getMessage(), e);
            return false;
        }
    }
}
