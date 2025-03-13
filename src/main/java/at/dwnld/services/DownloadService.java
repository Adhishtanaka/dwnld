package at.dwnld.services;

import at.dwnld.controllers.MainController;
import at.dwnld.models.FileModel;
import at.dwnld.models.FileStatus;
import javafx.application.Platform;
import okhttp3.*;
import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

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

    public AbstractMap.SimpleEntry<String, Long> getFileInfo(String url, Map<String, String> headers) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(url).head();
        if (headers != null) {
            headers.forEach(requestBuilder::addHeader);
        }
        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String fileName;
                String contentDisposition = response.header("Content-Disposition");
                if (contentDisposition != null && contentDisposition.contains("filename=")) {
                    fileName = contentDisposition.split("filename=")[1].replace("\"", "").trim();
                } else {
                    fileName = url.substring(url.lastIndexOf("/") + 1);
                    if (!fileName.contains(".")) {
                        fileName = "downloaded_file_" + System.currentTimeMillis() + ".bin";
                    }
                }
                long fileSize = response.header("Content-Length") != null ?
                        Long.parseLong(Objects.requireNonNull(response.header("Content-Length"))) : -1L;
                return new AbstractMap.SimpleEntry<>(fileName, fileSize);
            }
        }
        return new AbstractMap.SimpleEntry<>(null, -1L);
    }


    public void download(String url, String filePath, Map<String, String> headers) throws IOException {
        AbstractMap.SimpleEntry<String, Long> fileInfo = getFileInfo(url, headers);
        String fileName = fileInfo.getKey() != null ? fileInfo.getKey() : "downloaded_file";
        if (!filePath.endsWith(File.separator)) {
            filePath += File.separator;
        }
        filePath += fileName;
        long fileSize = fileInfo.getValue();

        FileModel file = new FileModel(fileName, url, filePath, LocalDateTime.now(), fileSize, LocalDateTime.now(), FileStatus.pending, 0, 0, headers);

        mainController.addDownload(file);

        if (fileSize > 0) {
            downloadSegmentedFile(file);
        } else {
            downloadSegment(file, 0, 0);
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

        for (int i = 0; i < threadCount; i++) {
            long start = i * segmentSize;
            long end = (i == threadCount - 1) ? file.getSize() - 1 : (start + segmentSize - 1);

            executorService.submit(() -> {
                try {
                    downloadSegment(file, start, end);
                } catch (IOException e) {
                    hasFailure.set(true);
                    file.setStatus(FileStatus.failed);
                    Platform.runLater(mainController::refreshTable);
                } finally {
                    latch.countDown();

                    if (latch.getCount() == 0 && !hasFailure.get()) {
                        Platform.runLater(() -> {
                            file.setStatus(FileStatus.completed);
                            mainController.refreshTable();
                        });
                    }
                }
            });
        }
        executorService.shutdown();
    }

    private void downloadSegment(FileModel file, long start, long end) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(file.getUrl());
        if(file.getHeaders() != null) {
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
                long totalBytesRead = start;
                long startTime = System.nanoTime();

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    raf.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    if (totalBytesRead % (1024 * 1024) < 8192) {
                        final long finalTotalBytesRead = totalBytesRead;
                        final double elapsedTime = (System.nanoTime() - startTime) / 1e9;

                        Platform.runLater(() -> {
                            file.setDownloadedSize((int) finalTotalBytesRead);
                            if (elapsedTime > 0) {
                                file.setSpeed(finalTotalBytesRead / elapsedTime);
                            }
                            mainController.refreshTable();
                        });
                    }
                }

                final long finalTotalBytesRead = totalBytesRead;
                final double elapsedTime = (System.nanoTime() - startTime) / 1e9;
                Platform.runLater(() -> {
                    long fileDownloadedSize = file.getDownloadedSize() + finalTotalBytesRead;
                    file.setDownloadedSize((int) fileDownloadedSize);
                    if (elapsedTime > 0) {
                        file.setSpeed(finalTotalBytesRead / elapsedTime);
                    }
                    mainController.refreshTable();
                });

            }catch (IOException e) {
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
