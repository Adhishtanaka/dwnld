package at.dwnld.services;

import at.dwnld.controllers.MainController;
import at.dwnld.models.FileInfoModel;
import at.dwnld.models.FileModel;
import at.dwnld.models.FileStatus;
import javafx.application.Platform;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class DownloadService {

    private final OkHttpClient client;
    private final MainController mainController;

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
        String fileName = fileInfo.name() != null ? fileInfo.name(): "downloaded_file";
        if (!filePath.endsWith(File.separator)) {
            filePath += File.separator;
        }
        filePath += fileName;
        long fileSize = fileInfo.size();

        FileModel file = new FileModel(fileName, url, filePath, LocalDateTime.now(), fileSize, LocalDateTime.now(), FileStatus.pending, 0, 0, headers);

        mainController.addDownload(file);

        if (fileSize > 0) {
            downloadSegmentedFile(file);
        } else {
            downloadSegment(file, 0, 0, new AtomicLong(0));
        }
    }

    private void downloadSegmentedFile(FileModel file) {
        int threadCount = 4;
        long segmentSize = file.getSize() / threadCount;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        file.setStatus(FileStatus.inProgress);
        Platform.runLater(mainController::refreshTable);

        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        AtomicLong totalDownloadedBytes = new AtomicLong(0);

        long startTime = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            long start = i * segmentSize;
            long end = (i == threadCount - 1) ? file.getSize() - 1 : (start + segmentSize - 1);

            executorService.submit(() -> {
                try {
                    downloadSegment(file, start, end, totalDownloadedBytes);
                } catch (IOException e) {
                    hasFailure.set(true);
                    file.setStatus(FileStatus.failed);
                    Platform.runLater(mainController::refreshTable);
                } finally {
                    latch.countDown();

                    if (latch.getCount() == 0 && !hasFailure.get()) {
                        final double elapsedTime = (System.nanoTime() - startTime) / 1e9;
                        Platform.runLater(() -> {
                            if (elapsedTime > 0) {
                                file.setSpeed(totalDownloadedBytes.get() / elapsedTime);
                            }
                            file.setStatus(FileStatus.completed);
                            file.setDownloadedSize((int) file.getSize());
                            mainController.refreshTable();
                        });
                    }
                }
            });
        }
        executorService.shutdown();
    }

    private void downloadSegment(FileModel file, long start, long end, AtomicLong totalDownloadedBytes) throws IOException {
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

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    raf.write(buffer, 0, bytesRead);
                    bytesReadInSegment += bytesRead;
                    long newTotalDownloaded = totalDownloadedBytes.addAndGet(bytesRead);

                    if (bytesReadInSegment % (1024 * 1024) < 8192) {
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
            } catch (IOException e) {
                Platform.runLater(() -> {
                    file.setStatus(FileStatus.failed);
                    mainController.refreshTable();
                });
                System.out.println(e);
                throw e;
            }
        } catch (IOException e) {
            Platform.runLater(() -> {
                file.setStatus(FileStatus.failed);
                mainController.refreshTable();
            });
            System.out.println(e);
            throw e;
        }
    }

}