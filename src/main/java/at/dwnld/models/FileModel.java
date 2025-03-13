package at.dwnld.models;

import java.util.Collections;
import java.util.Map;
import java.time.LocalDateTime;

public class FileModel {

    String name;
    String url;
    String path;
    LocalDateTime added;
    long size;
    LocalDateTime lastTried;
    FileStatus status;
    double speed;
    long downloadedSize;
    Map<String, String> headers;

    public FileModel(String name, String url, String path, LocalDateTime added, long size, LocalDateTime lastTried, FileStatus status, double speed, long downloadedSize, Map<String, String> headers) {
        this.name = name != null ? name : "unknown";
        this.url = url;
        this.path = path;
        this.added = added != null ? added : LocalDateTime.now();
        this.size = size;
        this.lastTried = lastTried;
        this.status = status != null ? status : FileStatus.pending;
        this.speed = speed;
        this.downloadedSize = downloadedSize;
        this.headers = headers != null ? headers : Collections.emptyMap();
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public LocalDateTime getAdded() {
        return added;
    }

    public void setAdded(LocalDateTime added) {
        this.added = added;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public LocalDateTime getLastTried() {
        return lastTried;
    }

    public void setLastTried(LocalDateTime lastTried) {
        this.lastTried = lastTried;
    }

    public FileStatus getStatus() {
        return status;
    }

    public void setStatus(FileStatus status) {
        this.status = status;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public long getDownloadedSize() {
        return downloadedSize;
    }

    public void setDownloadedSize(int downloadedSize) {
        this.downloadedSize = downloadedSize;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }


}


