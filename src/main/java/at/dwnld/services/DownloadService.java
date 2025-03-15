package at.dwnld.services;

import at.dwnld.controllers.MainController;
import at.dwnld.models.FileInfoModel;
import at.dwnld.models.FileModel;
import at.dwnld.models.FileStatus;
import at.dwnld.models.SettingModel;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static at.dwnld.controllers.MainController.getDownloads;

public class DownloadService {

    private final OkHttpClient client;
    private final MainController mainController;
    private final ConcurrentHashMap<String, ExecutorService> downloadExecutors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Future<?>>> downloadTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, Long>> segmentProgress = new ConcurrentHashMap<>();

    public DownloadService(MainController mainController) {
        this.mainController = mainController;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public FileInfoModel getFileInfo(String url, Map<String, String> headers) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(url).head();
        if (headers != null) {
            headers.forEach(requestBuilder::addHeader);
        }

        OkHttpClient redirectClient = client.newBuilder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        try (Response response = redirectClient.newCall(requestBuilder.build()).execute()) {
            if (response.isSuccessful()) {
                String finalUrl = response.request().url().toString();
                String fileName = null;

                String contentDisposition = response.header("Content-Disposition");
                if (contentDisposition != null) {
                    if (contentDisposition.contains("filename=")) {
                        String[] parts = contentDisposition.split("filename=");
                        if (parts.length > 1) {
                            fileName = parts[1].replaceAll("[\"';]", "").trim();
                        }
                    } else if (contentDisposition.contains("filename*=")) {
                        String[] parts = contentDisposition.split("filename\\*=");
                        if (parts.length > 1) {
                            String encodedPart = parts[1].trim();
                            if (encodedPart.contains("''")) {
                                fileName = encodedPart.substring(encodedPart.lastIndexOf("''") + 2)
                                        .replaceAll("[\"';]", "").trim();
                                try {
                                    fileName = java.net.URLDecoder.decode(fileName, StandardCharsets.UTF_8);
                                } catch (Exception e) {
                                    System.out.println(e);
                                }
                            }
                        }
                    }
                }

                if (fileName == null || fileName.isEmpty()) {
                    String urlPath = finalUrl.split("\\?")[0];
                    fileName = urlPath.substring(urlPath.lastIndexOf("/") + 1);

                    if (!fileName.contains(".")) {
                        String contentType = response.header("Content-Type");
                        String extension = getExtension(contentType);

                        fileName = "downloaded_file_" + System.currentTimeMillis() + extension;
                    }
                }

                long fileSize = response.header("Content-Length") != null ?
                        Long.parseLong(Objects.requireNonNull(response.header("Content-Length"))) : -1L;

                return new FileInfoModel(finalUrl, fileName, fileSize);
            }
        }
        return new FileInfoModel(null, null, -1L);
    }

    @NotNull
    private static String getExtension(String contentType) {
        String extension = ".bin";

        if (contentType != null) {
            if (contentType.contains("text/html")) extension = ".html";
            else if (contentType.contains("text/plain")) extension = ".txt";
            else if (contentType.contains("application/pdf")) extension = ".pdf";
            else if (contentType.contains("image/jpeg")) extension = ".jpg";
            else if (contentType.contains("image/png")) extension = ".png";
            else if (contentType.contains("application/zip")) extension = ".zip";
            else if (contentType.contains("application/json")) extension = ".json";
            else if (contentType.contains("application/xml")) extension = ".xml";
            else if (contentType.contains("audio/mpeg")) extension = ".mp3";
            else if (contentType.contains("video/mp4")) extension = ".mp4";
            else if (contentType.contains("application/msword")) extension = ".doc";
            else if (contentType.contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) extension = ".docx";
            else if (contentType.contains("application/vnd.ms-excel")) extension = ".xls";
            else if (contentType.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) extension = ".xlsx";
        }
        return extension;
    }


    public void download(String url, String filePath, Map<String, String> headers) throws IOException {
        FileInfoModel fileInfo = getFileInfo(url, headers);
        url = fileInfo.finalUrl();
        String fileName = fileInfo.name() != null ? fileInfo.name() : "downloaded_file";
        if (!filePath.endsWith(File.separator)) {
            filePath += File.separator;
        }
        filePath += fileName;
        long fileSize = fileInfo.size();

        FileModel file = new FileModel(fileName, url, filePath, LocalDateTime.now(), fileSize, LocalDateTime.now(), FileStatus.pending, 0, 0, headers,null);
        mainController.addDownload(file);

        if (checkMaxParallelDownloads()) {
            file.setStatus(FileStatus.hold);
            Platform.runLater(mainController::refreshTable);
            return;
        }

        startDownload(file);
    }

    public void startDownload(FileModel file) {
        if (file.getSize() > 0) {
            downloadSegmentedFile(file);
        } else {
            file.setStatus(FileStatus.inProgress);
            Platform.runLater(mainController::refreshTable);

            ExecutorService singleExecutor = Executors.newSingleThreadExecutor();
            downloadExecutors.put(file.getPath(), singleExecutor);

            Future<?> task = singleExecutor.submit(() -> {
                try {
                    downloadSegment(file, 0, 0, new AtomicLong(0), 0);
                } catch (IOException e) {
                    file.setStatus(FileStatus.failed);
                    Platform.runLater(mainController::refreshTable);
                }
            });

            List<Future<?>> tasks = new ArrayList<>();
            tasks.add(task);
            downloadTasks.put(file.getPath(), tasks);
        }
    }

    private void downloadSegmentedFile(FileModel file) {
        int threadCount = 4;
        long segmentSize = file.getSize() / threadCount;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        downloadExecutors.put(file.getPath(), executorService);

        List<Future<?>> tasks = new ArrayList<>();
        downloadTasks.put(file.getPath(), tasks);

        ConcurrentHashMap<Integer, Long> fileSegments = new ConcurrentHashMap<>();
        segmentProgress.put(file.getPath(), fileSegments);

        file.setStatus(FileStatus.inProgress);
        Platform.runLater(mainController::refreshTable);

        AtomicLong totalDownloadedBytes = new AtomicLong(file.getDownloadedSize());
        AtomicInteger completedSegments = new AtomicInteger(0);
        long startTime = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            final int segmentId = i;
            long start = i * segmentSize;
            long end = (i == threadCount - 1) ? file.getSize() - 1 : (start + segmentSize - 1);

            long segmentStart = fileSegments.getOrDefault(segmentId, start);

            Future<?> task = executorService.submit(() -> {
                try {
                    downloadSegment(file, segmentStart, end, totalDownloadedBytes, segmentId);
                    if (completedSegments.incrementAndGet() == threadCount) {
                        final double elapsedTime = (System.nanoTime() - startTime) / 1e9;
                        Platform.runLater(() -> {
                            if (elapsedTime > 0) {
                                file.setSpeed(totalDownloadedBytes.get() / elapsedTime);
                            }
                            if(file.getStatus() == FileStatus.inProgress){
                                file.setStatus(FileStatus.completed);
                                file.setDownloadedSize((int) file.getSize());
                            }

                            mainController.refreshTable();

                            downloadExecutors.remove(file.getPath());
                            downloadTasks.remove(file.getPath());
                            segmentProgress.remove(file.getPath());

                            checkDownloadsForHold();
                        });
                    }
                } catch (IOException e) {
                    if (file.getStatus() != FileStatus.paused && file.getStatus() != FileStatus.hold) {
                        file.setStatus(FileStatus.failed);
                        Platform.runLater(mainController::refreshTable);
                    }
                }
            });

            tasks.add(task);
        }
    }

    private void downloadSegment(FileModel file, long start, long end, AtomicLong totalDownloadedBytes, int segmentId) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(file.getUrl());
        if (file.getHeaders() != null) {
            file.getHeaders().forEach(requestBuilder::addHeader);
        }

        if (end != 0) {
            requestBuilder.addHeader("Range", "bytes=" + start + "-" + end);
        }

        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Platform.runLater(() -> {
                    file.setStatus(FileStatus.failed);
                    mainController.refreshTable();
                });
                return;
            }

            File targetFile = new File(file.getPath());
            try (RandomAccessFile raf = new RandomAccessFile(targetFile, "rw");
                 InputStream inputStream = response.body().byteStream()) {

                raf.seek(start);
                byte[] buffer = new byte[8192];
                int bytesRead;
                long bytesReadInSegment = 0;
                long lastUpdateTime = System.nanoTime();
                long lastDownloadedBytes = totalDownloadedBytes.get();
                long currentPosition = start;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    if (file.getStatus() == FileStatus.paused || file.getStatus() == FileStatus.hold) {
                        if (segmentProgress.containsKey(file.getPath())) {
                            segmentProgress.get(file.getPath()).put(segmentId, currentPosition);
                        }
                        break;
                    }

                    raf.write(buffer, 0, bytesRead);
                    bytesReadInSegment += bytesRead;
                    currentPosition += bytesRead;
                    long newTotalDownloaded = totalDownloadedBytes.addAndGet(bytesRead);

                    if (bytesReadInSegment % (1024 * 1024) < 8192) {
                        if (segmentProgress.containsKey(file.getPath())) {
                            segmentProgress.get(file.getPath()).put(segmentId, currentPosition);
                        }

                        long currentTime = System.nanoTime();
                        double timeDiff = (currentTime - lastUpdateTime) / 1e9;

                        if (timeDiff > 0.5) {
                            long bytesDiff = newTotalDownloaded - lastDownloadedBytes;
                            double currentSpeed = bytesDiff / timeDiff;

                            Platform.runLater(() -> {
                                file.setSpeed((long) currentSpeed);
                                file.setDownloadedSize((int) newTotalDownloaded);
                                mainController.refreshTable();
                            });

                            lastUpdateTime = currentTime;
                            lastDownloadedBytes = newTotalDownloaded;
                        } else {
                            Platform.runLater(() -> {
                                file.setDownloadedSize((int) newTotalDownloaded);
                                mainController.refreshTable();
                            });
                        }
                    }
                }
            }
        }
    }

    public void pauseDownload(FileModel file) {
        file.setStatus(FileStatus.paused);
        Platform.runLater(mainController::refreshTable);
        ExecutorService executor = downloadExecutors.get(file.getPath());
        if (executor != null) {
            executor.shutdownNow();
            downloadExecutors.remove(file.getPath());
        }

        List<Future<?>> tasks = downloadTasks.get(file.getPath());
        if (tasks != null) {
            for (Future<?> task : tasks) {
                task.cancel(true);
            }
            downloadTasks.remove(file.getPath());
        }


        checkDownloadsForHold();
    }

    public void resumeDownload(FileModel file) {
        if (file.getStatus() == FileStatus.paused) {
            if (checkMaxParallelDownloads()) {
                file.setStatus(FileStatus.hold);
            } else {
                startDownload(file);
            }
            Platform.runLater(mainController::refreshTable);
        }
    }

    public void cancelDownload(FileModel file) {
        pauseDownload(file);
        File downloadedFile = new File(file.getPath());
        if (downloadedFile.exists()) {
            boolean deleted = downloadedFile.delete();
            if (!deleted) {
                System.out.println("Failed to delete file: " + file.getPath());
            }
        }

        downloadExecutors.remove(file.getPath());
        downloadTasks.remove(file.getPath());
        segmentProgress.remove(file.getPath());
        file.setStatus(FileStatus.cancelled);
        file.setDownloadedSize(0);
        file.setSpeed(0);

        Platform.runLater(() -> {
            mainController.refreshTable();
            checkDownloadsForHold();
        });
    }

    public Boolean checkMaxParallelDownloads() {
        ObservableList<FileModel> fmd = getDownloads();
        int activeDownloads = 0;

        for (FileModel file : fmd) {
            if (file.getStatus() == FileStatus.inProgress) {
                activeDownloads++;
            }
        }

        return activeDownloads >= SettingModel.getInstance().getMax_parallel();
    }

    private void checkDownloadsForHold() {
        if (!checkMaxParallelDownloads()) {
            ObservableList<FileModel> downloads = getDownloads();

            FileModel nextFile = downloads.stream()
                    .filter(file -> file.getStatus() == FileStatus.hold)
                    .min(Comparator.comparing(FileModel::getLastTried))
                    .orElse(null);

            if (nextFile != null) {
                startDownload(nextFile);
            }
        }
    }
}