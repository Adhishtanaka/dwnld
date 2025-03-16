package at.dwnld.services;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class defaultAppService {

        private static final String APP_NAME = "dwnld";
        private static final String PACKAGE_NAME = "at.dwnld";
        public static final String URL_SCHEME = "dwnld";

        public static void setupAutoStart(boolean enable) {
            String os = System.getProperty("os.name").toLowerCase();
            try {
                if (os.contains("linux")) {
                    setupLinuxAutoStart(enable);
                } else if (os.contains("mac") || os.contains("darwin")) {
                    setupMacOSAutoStart(enable);
                } else if (os.contains("windows")) {
                    setupWindowsAutoStart(enable);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void setupDeepLink(boolean enable) {
            String os = System.getProperty("os.name").toLowerCase();
            try {
                if (os.contains("linux")) {
                    setupLinuxDeepLink(enable);
                } else if (os.contains("mac") || os.contains("darwin")) {
                    setupMacOSDeepLink(enable);
                } else if (os.contains("windows")) {
                    setupWindowsDeepLink(enable);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private static void setupLinuxAutoStart(boolean enable) throws IOException {
            String homeDir = System.getProperty("user.home");
            File autostartDir = new File(homeDir + "/.config/autostart");
            File autostartFile = new File(autostartDir, PACKAGE_NAME + ".desktop");

            if (enable) {
                autostartDir.mkdirs();
                String execPath = "/usr/bin/" + APP_NAME;
                File optExec = new File("/opt/" + PACKAGE_NAME + "/bin/" + APP_NAME);
                if (optExec.exists()) execPath = optExec.getAbsolutePath();

                try (FileWriter writer = new FileWriter(autostartFile)) {
                    writer.write("[Desktop Entry]\nType=Application\nName=" + APP_NAME +
                            "\nExec=" + execPath + "\nX-GNOME-Autostart-enabled=true\n");
                }
            } else if (autostartFile.exists()) {
                autostartFile.delete();
            }
        }

        private static void setupLinuxDeepLink(boolean enable) throws IOException, InterruptedException {
            String homeDir = System.getProperty("user.home");
            File applicationsDir = new File(homeDir + "/.local/share/applications");
            File deepLinkFile = new File(applicationsDir, PACKAGE_NAME + "-url.desktop");

            if (enable) {
                applicationsDir.mkdirs();
                String execPath = "/usr/bin/" + APP_NAME;
                File optExec = new File("/opt/" + PACKAGE_NAME + "/bin/" + APP_NAME);
                if (optExec.exists()) execPath = optExec.getAbsolutePath();

                try (FileWriter writer = new FileWriter(deepLinkFile)) {
                    writer.write("[Desktop Entry]\nName=" + APP_NAME + "\nExec=" + execPath + " %u\n" +
                            "Type=Application\nMimeType=x-scheme-handler/" + URL_SCHEME + ";\n");
                }

                ProcessBuilder pb = new ProcessBuilder("xdg-mime", "default",
                        PACKAGE_NAME + "-url.desktop", "x-scheme-handler/" + URL_SCHEME);
                pb.start().waitFor();
            } else if (deepLinkFile.exists()) {
                deepLinkFile.delete();
                ProcessBuilder pb = new ProcessBuilder("xdg-mime", "uninstall", deepLinkFile.getAbsolutePath());
                pb.start();
            }
        }

        private static void setupMacOSAutoStart(boolean enable) throws IOException, InterruptedException {
            String homeDir = System.getProperty("user.home");
            File launchAgentsDir = new File(homeDir + "/Library/LaunchAgents");
            File plistFile = new File(launchAgentsDir, PACKAGE_NAME + ".plist");

            if (enable) {
                launchAgentsDir.mkdirs();
                String appPath = "/Applications/" + APP_NAME + ".app/Contents/MacOS/" + APP_NAME;

                try (FileWriter writer = new FileWriter(plistFile)) {
                    writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                            "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
                            "<plist version=\"1.0\">\n<dict>\n" +
                            "    <key>Label</key>\n    <string>" + PACKAGE_NAME + "</string>\n" +
                            "    <key>ProgramArguments</key>\n    <array>\n        <string>" + appPath + "</string>\n" +
                            "    </array>\n    <key>RunAtLoad</key>\n    <true/>\n</dict>\n</plist>\n");
                }

                ProcessBuilder pb = new ProcessBuilder("launchctl", "load", plistFile.getAbsolutePath());
                pb.start();
            } else if (plistFile.exists()) {
                ProcessBuilder pb = new ProcessBuilder("launchctl", "unload", plistFile.getAbsolutePath());
                pb.start().waitFor();
                plistFile.delete();
            }
        }

        private static void setupMacOSDeepLink(boolean enable) {
            System.out.println(enable ?
                    "For URL handling, add to Info.plist: CFBundleURLSchemes array with " + URL_SCHEME :
                    "Remove " + URL_SCHEME + " from CFBundleURLSchemes in Info.plist");
        }

        private static void setupWindowsAutoStart(boolean enable) throws IOException, InterruptedException {
            String appDataDir = System.getenv("LOCALAPPDATA");
            String execPath = appDataDir + "\\" + APP_NAME + "\\" + APP_NAME + ".exe";

            Path tempDir = Files.createTempDirectory("dwnld_setup");
            File regScript = new File(tempDir.toFile(), "autostart.reg");

            try (FileWriter writer = new FileWriter(regScript)) {
                writer.write("Windows Registry Editor Version 5.00\n\n");
                writer.write("[HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Run]\n");
                if (enable) {
                    writer.write("\"" + APP_NAME + "\"=\"" + execPath.replace("\\", "\\\\") + "\"\n");
                } else {
                    writer.write("\"" + APP_NAME + "\"=-\n");
                }
            }

            ProcessBuilder pb = new ProcessBuilder("reg", "import", regScript.getAbsolutePath());
            pb.start().waitFor();
            Files.deleteIfExists(regScript.toPath());
        }

        private static void setupWindowsDeepLink(boolean enable) throws IOException, InterruptedException {
            String appDataDir = System.getenv("LOCALAPPDATA");
            String execPath = appDataDir + "\\" + APP_NAME + "\\" + APP_NAME + ".exe";

            Path tempDir = Files.createTempDirectory("dwnld_setup");
            File regScript = new File(tempDir.toFile(), "deeplink.reg");

            try (FileWriter writer = new FileWriter(regScript)) {
                writer.write("Windows Registry Editor Version 5.00\n\n");

                if (enable) {
                    writer.write("[HKEY_CURRENT_USER\\Software\\Classes\\" + URL_SCHEME + "]\n");
                    writer.write("@=\"URL:" + APP_NAME + " Protocol\"\n");
                    writer.write("\"URL Protocol\"=\"\"\n\n");
                    writer.write("[HKEY_CURRENT_USER\\Software\\Classes\\" + URL_SCHEME + "\\shell\\open\\command]\n");
                    writer.write("@=\"\\\"" + execPath.replace("\\", "\\\\") + "\\\" \\\"%1\\\"\"\n");
                } else {
                    writer.write("[HKEY_CURRENT_USER\\Software\\Classes\\" + URL_SCHEME + "]\n");
                    writer.write("@=-\n");
                    writer.write("\"URL Protocol\"=-\n");
                }
            }

            ProcessBuilder pb = new ProcessBuilder("reg", "import", regScript.getAbsolutePath());
            pb.start().waitFor();
            Files.deleteIfExists(regScript.toPath());
        }

}