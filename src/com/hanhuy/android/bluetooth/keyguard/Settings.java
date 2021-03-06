package com.hanhuy.android.bluetooth.keyguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

/**
 * TODO add a way to prune scoped-settings that are no longer used.
 * i.e. when a wifi configuration or bluetooth device is removed (unpaired)
 */
public class Settings {
    /**
     * This setting is scoped by device() or network()
     */
    public final static Setting<Boolean> REQUIRE_UNLOCK =
            new BooleanSetting("require_unlock");
    /**
     * This setting is scoped by device() or network()
     */
    public final static Setting<Boolean> DISABLE_KEYGUARD =
            new BooleanSetting("disable_keyguard");
    public final static Setting<Boolean> SHOW_NOTIFICATIONS =
            new BooleanSetting("notifications", true);
    public final static Setting<Boolean> LOCK_DISABLED =
            new BooleanSetting("lock_disabled");
    public final static Setting<String> PASSWORD_HASH =
            new StringSetting("password_hash");
    public final static Setting<String> PASSWORD =
            new StringSetting("password");
    public final static Setting<List<String>> BLUETOOTH_DEVICES =
            new StringListSetting("bluetooth_devices");
    public final static Setting<List<String>> WIFI_NETWORKS =
            new StringListSetting("wifi_networks");
    public final static Setting<Boolean> WIFI_CLEAR_KEYGUARD =
            new BooleanSetting("wifi_clear_keyguard");
    public final static Setting<Boolean> BT_CLEAR_KEYGUARD =
            new BooleanSetting("bt_clear_keyguard");
    public final static Setting<List<String>> BLUETOOTH_CONNECTIONS =
            new StringListSetting("connected_devices");
    public final static Setting<Long> LAST_STATE_CHANGE =
            new LongSetting("last_state_change", 0l);
    private static final String TAG = "Settings";

    private final Gson gson = new Gson();

    public abstract static class Setting<T> {
        public final String key;
        public final T defaultValue;
        public Setting(String key, T defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
        }
        public String toString() {
            return key;
        }

        public abstract Setting<T> prefix(String prefix);
    }

    private static class LongSetting extends Setting<Long> {
        public LongSetting(String key) { super(key, null); }
        public LongSetting(String key, Long defaultValue) {
            super(key, defaultValue);
        }
        public LongSetting prefix(String prefix) {
            return new LongSetting(prefix + "." + key);
        }
    }
    private static class StringSetting extends Setting<String> {
        public StringSetting(String key) { super(key, null); }
        public StringSetting prefix(String prefix) {
            return new StringSetting(prefix + "." + key);
        }
    }
    private static class BooleanSetting extends Setting<Boolean> {
        public BooleanSetting(String key) { this(key, false); }
        public BooleanSetting(String key, boolean defaultValue) {
            super(key, defaultValue);
        }
        public BooleanSetting prefix(String prefix) {
            return new BooleanSetting(prefix + "." + key, defaultValue);
        }
    }

    private static class StringListSetting extends Setting<List<String>> {
        @SuppressWarnings("unchecked")
        public StringListSetting(String key) {
            super(key, Collections.EMPTY_LIST);
        }
        public StringListSetting prefix(String prefix) {
            return new StringListSetting(prefix + "." + key);
        }
    }

    private static Settings instance;
    private SharedPreferences prefs;

    private Settings(Context c) {
         prefs = PreferenceManager.getDefaultSharedPreferences(c);
    }

    @VisibleForTesting
    Settings() { }

    public static Settings getInstance(Context c) {
        if (instance == null)
            instance = new Settings(c.getApplicationContext());
        return instance;
    }

    @SuppressWarnings("unchecked")
    public <T> void set(Setting<T> setting, T value) {
        SharedPreferences.Editor editor = prefs.edit();
        Class<T> settingType = getTypeOf(setting);
        if (settingType == String.class) {
            editor.putString(setting.key, (String) value);
        } else if (settingType == Boolean.class) {
            editor.putBoolean(setting.key, (Boolean) value);
        } else if (settingType == Long.class) {
            editor.putLong(setting.key, (Long) value);
        } else if (settingType == List.class) {
            List<String> values = (List<String>) value;
            String json = gson.toJson(values);
            editor.putString(setting.key, json);
        } else {
            throw new RuntimeException("Unknown setting type");
        }
        editor.commit();
    }

    @SuppressWarnings("unchecked")
    public <T> Class<T> getTypeOf(Setting<T> setting) {
        Type sup = setting.getClass().getGenericSuperclass();
        ParameterizedType type = (ParameterizedType) sup;
        Type[] args = type.getActualTypeArguments();
        if (args[0] instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) args[0];
            return (Class<T>) pt.getRawType();
        } else {
            return (Class<T>) args[0];
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Setting<T> setting) {
        T defaultValue = setting.defaultValue;
        Class<T> settingType = getTypeOf(setting);
        if (settingType == String.class) {
            return (T) prefs.getString(setting.key, (String) defaultValue);
        } else if (settingType == Boolean.class) {
            return (T) Boolean.valueOf(prefs.getBoolean(setting.key,
                    defaultValue == null ? false : (Boolean) defaultValue));
        } else if (settingType == Long.class) {
            return (T) Long.valueOf(prefs.getLong(setting.key,
                    defaultValue == null ? 0 : (Long) defaultValue));
        } else if (settingType == List.class) {
            String json = prefs.getString(setting.key, null);
            if (json == null) {
                return (T) Collections.EMPTY_LIST;
            } else {
                try {
                    List<String> strings = gson.fromJson(json, List.class);
                    return (T) (strings == null ?
                            Collections.EMPTY_LIST : strings);
                } catch (JsonSyntaxException e) {
                    Log.e(TAG, "Bad json", e);
                    return (T) Collections.EMPTY_LIST;
                }
            }
        } else {
            throw new RuntimeException("Unknown setting type");
        }
    }

    public static String network(String net) {
        return "network." + net;
    }
    public static String device(String dev) {
        return "device." + dev;
    }
    public static <T> Setting<T> network(String net, Setting<T> setting) {
        return setting.prefix(network(net));
    }
    public static <T> Setting<T> device(String dev, Setting<T> setting) {
        return setting.prefix(device(dev));
    }
}
