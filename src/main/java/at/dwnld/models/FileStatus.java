package at.dwnld.models;
import java.io.Serializable;

public enum FileStatus implements Serializable {
    pending,
    inProgress,
    paused,
    completed,
    failed
}
