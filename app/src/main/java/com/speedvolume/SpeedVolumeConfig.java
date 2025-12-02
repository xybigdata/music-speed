package com.speedvolume;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class SpeedVolumeConfig {
    private static final String PREFS_NAME = "SpeedVolumePrefs";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_ACTIVE = "activeProfile";
    private static final String KEY_UNLOCK_P2 = "unlockProfile2";
    private static final String KEY_UNLOCK_P3 = "unlockProfile3";
    private static final String KEY_P2_MODE = "profile2Mode";
    private static final String KEY_P2_VOLUME_CRIT = "profile2VolumeCrit";
    private static final String KEY_P2_SPEED_CRIT = "profile2SpeedCrit";
    private static final String KEY_P2_EASE_COEF = "profile2EaseCoef";
    private static final String KEY_P2_POWER_INDEX = "profile2PowerIndex";
    
    public static class SpeedRange {
        public float minSpeed;
        public float maxSpeed;
        public int volume;
        
        public SpeedRange(float min, float max, int vol) {
            minSpeed = min;
            maxSpeed = max;
            volume = vol;
        }
    }
    
    public static class Profile {
        public String name;
        public List<SpeedRange> ranges;
        
        public Profile(String n) {
            name = n;
            ranges = new ArrayList<>();
        }
    }
    
    private List<Profile> profiles = new ArrayList<>();
    private int activeProfileIndex = 0;
    
    public SpeedVolumeConfig() {
        initDefaultProfiles();
    }
    
    private void initDefaultProfiles() {
        profiles.clear();
        
        Profile p1 = new Profile("小电驴");
        p1.ranges.add(new SpeedRange(0, 10, 20));
        p1.ranges.add(new SpeedRange(10, 20, 30));
        p1.ranges.add(new SpeedRange(20, 30, 50));
        p1.ranges.add(new SpeedRange(30, 35, 80));
        p1.ranges.add(new SpeedRange(35, 999, 100));
        
        Profile p2 = new Profile("狂飙模式");
        p2.ranges.add(new SpeedRange(0, 999, 50));
        
        Profile p3 = new Profile("叫卖模式");
        p3.ranges.add(new SpeedRange(0, 999, 100));
        
        profiles.add(p1);
        profiles.add(p2);
        profiles.add(p3);
    }
    
    public int getVolumeForSpeed(float speedKmh, Context context) {
        if (profiles.isEmpty()) return 50;
        if (activeProfileIndex == 1) {
            return calculateProfile2Volume(context, speedKmh);
        }
        Profile active = profiles.get(activeProfileIndex);
        for (SpeedRange range : active.ranges) {
            if (speedKmh >= range.minSpeed && speedKmh < range.maxSpeed) {
                return range.volume;
            }
        }
        return active.ranges.get(active.ranges.size() - 1).volume;
    }
    
    public List<Profile> getProfiles() {
        return profiles;
    }
    
    public int getActiveProfileIndex() {
        return activeProfileIndex;
    }
    
    public void setActiveProfileIndex(int index) {
        if (index >= 0 && index < profiles.size()) {
            activeProfileIndex = index;
        }
    }
    
    public void save(Context context) {
        try {
            JSONArray profilesArray = new JSONArray();
            for (Profile profile : profiles) {
                JSONObject profileObj = new JSONObject();
                profileObj.put("name", profile.name);
                JSONArray rangesArray = new JSONArray();
                for (SpeedRange range : profile.ranges) {
                    JSONObject rangeObj = new JSONObject();
                    rangeObj.put("min", range.minSpeed);
                    rangeObj.put("max", range.maxSpeed);
                    rangeObj.put("vol", range.volume);
                    rangesArray.put(rangeObj);
                }
                profileObj.put("ranges", rangesArray);
                profilesArray.put(profileObj);
            }
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                .putString(KEY_PROFILES, profilesArray.toString())
                .putInt(KEY_ACTIVE, activeProfileIndex)
                .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void load(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_PROFILES, null);
            if (json != null) {
                profiles.clear();
                JSONArray profilesArray = new JSONArray(json);
                for (int i = 0; i < profilesArray.length(); i++) {
                    JSONObject profileObj = profilesArray.getJSONObject(i);
                    Profile profile = new Profile(profileObj.getString("name"));
                    JSONArray rangesArray = profileObj.getJSONArray("ranges");
                    for (int j = 0; j < rangesArray.length(); j++) {
                        JSONObject rangeObj = rangesArray.getJSONObject(j);
                        profile.ranges.add(new SpeedRange(
                            (float) rangeObj.getDouble("min"),
                            (float) rangeObj.getDouble("max"),
                            rangeObj.getInt("vol")
                        ));
                    }
                    profiles.add(profile);
                }
                activeProfileIndex = prefs.getInt(KEY_ACTIVE, 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
            initDefaultProfiles();
        }
    }
    
    public boolean isProfile2Unlocked(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_UNLOCK_P2, false);
    }
    
    public boolean isProfile3Unlocked(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_UNLOCK_P3, false);
    }
    
    public void unlockProfile2(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_UNLOCK_P2, true).apply();
    }
    
    public void unlockProfile3(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_UNLOCK_P3, true).apply();
    }
    
    public int getProfile2Mode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_P2_MODE, 0);
    }
    
    public void setProfile2Mode(Context context, int mode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_P2_MODE, mode).apply();
    }
    
    public int getProfile2VolumeCrit(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_P2_VOLUME_CRIT, 30);
    }
    
    public void setProfile2VolumeCrit(Context context, int value) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_P2_VOLUME_CRIT, value).apply();
    }
    
    public int getProfile2SpeedCrit(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_P2_SPEED_CRIT, 20);
    }
    
    public void setProfile2SpeedCrit(Context context, int value) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_P2_SPEED_CRIT, value).apply();
    }
    
    public float getProfile2EaseCoef(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getFloat(KEY_P2_EASE_COEF, 0.1f);
    }
    
    public void setProfile2EaseCoef(Context context, float value) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putFloat(KEY_P2_EASE_COEF, value).apply();
    }
    
    public float getProfile2PowerIndex(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getFloat(KEY_P2_POWER_INDEX, 1.0f);
    }
    
    public void setProfile2PowerIndex(Context context, float value) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putFloat(KEY_P2_POWER_INDEX, value).apply();
    }
    
    public int calculateProfile2Volume(Context context, float speedKmh) {
        int mode = getProfile2Mode(context);
        if (mode == 0) {
            int volumeCrit = getProfile2VolumeCrit(context);
            int speedCrit = getProfile2SpeedCrit(context);
            if (speedKmh < speedCrit) {
                return volumeCrit;
            } else {
                float k = (float) volumeCrit / speedCrit;
                return Math.min(100, (int) (k * speedKmh));
            }
        } else {
            float easeCoef = getProfile2EaseCoef(context);
            float powerIndex = getProfile2PowerIndex(context);
            return Math.min(100, (int) (0.1f * easeCoef * Math.pow(speedKmh, powerIndex)));
        }
    }
    
    public void reloadActiveProfile(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            activeProfileIndex = prefs.getInt(KEY_ACTIVE, 0);
            String json = prefs.getString(KEY_PROFILES, null);
            if (json != null) {
                JSONArray profilesArray = new JSONArray(json);
                if (activeProfileIndex < profilesArray.length()) {
                    JSONObject profileObj = profilesArray.getJSONObject(activeProfileIndex);
                    Profile profile = new Profile(profileObj.getString("name"));
                    JSONArray rangesArray = profileObj.getJSONArray("ranges");
                    for (int j = 0; j < rangesArray.length(); j++) {
                        JSONObject rangeObj = rangesArray.getJSONObject(j);
                        profile.ranges.add(new SpeedRange(
                            (float) rangeObj.getDouble("min"),
                            (float) rangeObj.getDouble("max"),
                            rangeObj.getInt("vol")
                        ));
                    }
                    if (activeProfileIndex < profiles.size()) {
                        profiles.set(activeProfileIndex, profile);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
