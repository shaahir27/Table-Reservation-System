package dao;

import db.DatabaseConnection;
import model.AppSetting;
import util.OracleErrorMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for app_setting configuration table.
 */
public class SettingDAO {

    public Map<String, AppSetting> getAllMap() {
        Map<String, AppSetting> map = new HashMap<>();
        String sql = "SELECT setting_key, setting_value, description FROM app_setting";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String key = rs.getString("setting_key");
                String value = rs.getString("setting_value");
                String desc = rs.getString("description");
                map.put(key, new AppSetting(key, value, desc));
            }
            return map;
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "SettingDAO.getAllMap");
        }
    }

    public List<AppSetting> getAll() {
        List<AppSetting> list = new ArrayList<>();
        String sql = "SELECT setting_key, setting_value, description FROM app_setting ORDER BY setting_key";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String key = rs.getString("setting_key");
                String value = rs.getString("setting_value");
                String desc = rs.getString("description");
                list.add(new AppSetting(key, value, desc));
            }
            return list;
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "SettingDAO.getAll");
        }
    }

    public String get(String key, String defaultValue) {
        String sql = "SELECT setting_value FROM app_setting WHERE setting_key = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String val = rs.getString("setting_value");
                    return val != null ? val.trim() : defaultValue;
                }
            }
            return defaultValue;
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "SettingDAO.get");
        }
    }

    public int getInt(String key, int defaultValue) {
        String str = get(key, null);
        if (str == null) return defaultValue;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public void update(String key, String value) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Setting value cannot be null or empty for key: " + key);
        }
        String sql = "UPDATE app_setting SET setting_value = ? WHERE setting_key = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, value.trim());
            ps.setString(2, key.trim());
            int rows = ps.executeUpdate();
            if (rows == 0) {
                String ins = "INSERT INTO app_setting (setting_key, setting_value, description) VALUES (?, ?, 'System Configuration')";
                try (PreparedStatement ips = con.prepareStatement(ins)) {
                    ips.setString(1, key.trim());
                    ips.setString(2, value.trim());
                    ips.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "SettingDAO.update");
        }
    }
}
