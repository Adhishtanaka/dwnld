package at.dwnld.models;

public class SettingModel {
    int default_path;
    int max_parallel;
    float default_speed;
    boolean is_default_application;

    public SettingModel(int default_path, int max_parallel, float default_speed, boolean is_default_application) {
        this.default_path = default_path;
        this.max_parallel = max_parallel;
        this.default_speed = default_speed;
        this.is_default_application = is_default_application;
    }
}
