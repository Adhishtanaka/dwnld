package at.dwnld.models;

public class SettingModel {
    String default_path;
    int max_parallel;
    float default_speed;
    boolean is_default_application;

    public SettingModel(String default_path, int max_parallel, float default_speed, boolean is_default_application) {
        this.default_path = (default_path != null) ? default_path : getDefaultDownloadDirectory();
        this.max_parallel = max_parallel;
        this.default_speed = default_speed;
        this.is_default_application = is_default_application;
    }

    public static String getDefaultDownloadDirectory() {
        String userHome = System.getProperty("user.home");
        if (userHome == null) return System.getProperty("java.io.tmpdir");

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return userHome + "\\Downloads";
        } else {
            return userHome + "/Downloads";
        }

    }
}
