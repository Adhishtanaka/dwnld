package at.dwnld.models;

import java.io.Serial;
import java.io.Serializable;

public class SettingModel implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static SettingModel instance;

    private String default_path;
    private int max_parallel;
    private boolean is_default_application;

    private SettingModel(String default_path, int max_parallel, boolean is_default_application) {
        this.default_path = (default_path != null) ? default_path : getDefaultDownloadDirectory();
        this.max_parallel = max_parallel;
        this.is_default_application = is_default_application;
    }

    public static SettingModel getInstance() {
        if (instance == null) {
            instance = new SettingModel(getDefaultDownloadDirectory(), 4, true);
        }
        return instance;
    }

    public String getDefault_path() { return default_path; }
    public void setDefault_path(String default_path) { this.default_path = default_path; }

    public int getMax_parallel() { return max_parallel; }
    public void setMax_parallel(int max_parallel) { this.max_parallel = max_parallel; }

    public boolean isDefaultApplication() { return is_default_application; }
    public void setDefaultApplication(boolean is_default_application) { this.is_default_application = is_default_application; }

    public static String getDefaultDownloadDirectory() {
        String userHome = System.getProperty("user.home");
        if (userHome == null) return System.getProperty("java.io.tmpdir");

        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win") ? userHome + "\\Downloads" : userHome + "/Downloads";
    }
}
