package service;

import dao.AuditDAO;
import dao.SettingDAO;
import model.AppSetting;
import security.AuthContext;
import security.Permission;
import security.Session;

import java.util.List;
import java.util.Map;

/**
 * Service for accessing and configuring runtime business settings.
 */
public class SettingsService {
    private final SettingDAO settingDAO;
    private final AuditDAO auditDAO;
    private final AuthContext auth;

    public SettingsService() {
        this(new SettingDAO(), new AuditDAO(), Session.getInstance());
    }

    public SettingsService(SettingDAO settingDAO, AuditDAO auditDAO, AuthContext auth) {
        this.settingDAO = settingDAO;
        this.auditDAO = auditDAO;
        this.auth = auth != null ? auth : Session.getInstance();
    }

    public List<AppSetting> getAll() {
        return settingDAO.getAll();
    }

    public Map<String, AppSetting> getAllMap() {
        return settingDAO.getAllMap();
    }

    public String get(String key, String defaultValue) {
        return settingDAO.get(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return settingDAO.getInt(key, defaultValue);
    }

    public void update(String key, String value) {
        auth.require(Permission.SETTINGS_EDIT);
        String oldVal = settingDAO.get(key, "");
        settingDAO.update(key, value);
        auditDAO.log(auth.currentUser() != null ? auth.currentUser().getUserId() : null,
                "SETTING_CHANGED", "SETTING", null,
                "Updated " + key + " from '" + oldVal + "' to '" + value + "'");
    }
}
