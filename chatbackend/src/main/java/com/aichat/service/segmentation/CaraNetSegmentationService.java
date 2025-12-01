package com.aichat.service.segmentation;

import com.aichat.domain.dto.segmentation.PolypSegmentationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CaraNetSegmentationService {

    public PolypSegmentationResponse segment(MultipartFile file) {
        try {
            Path tempImage = Files.createTempFile("polyp_img_", getFileSuffix(file.getOriginalFilename()));
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, tempImage, StandardCopyOption.REPLACE_EXISTING);
            }

            Path outDir = Files.createTempDirectory("polyp_out_");
            Path scriptPath = resolveProjectPath("mdfra/infer_single.py");

            Path weightsPath = resolveWeightsPath();
            long weightsSize = Files.exists(weightsPath) ? Files.size(weightsPath) : -1;
            boolean weightsPointer = false;
            if (Files.exists(weightsPath)) {
                try (InputStream is = new FileInputStream(weightsPath.toFile())) {
                    byte[] head = is.readNBytes(64);
                    String s = new String(head);
                    weightsPointer = s.startsWith("version ") || s.contains("git-lfs");
                } catch (Exception ignored) {}
            }

            String python = resolvePython();
            ProcessBuilder pb = new ProcessBuilder(
                    python,
                    scriptPath.toString(),
                    "--image", tempImage.toString(),
                    "--out_dir", outDir.toString(),
                    "--pth_path", weightsPath.toString()
            );
            pb.redirectErrorStream(true);
            log.info("mdfra run: python={}, script={}, weights={}, size={}, pointer={}", python, scriptPath, weightsPath, weightsSize, weightsPointer);
            Process proc = pb.start();

            String stdout = readAll(proc.getInputStream());
            int exit = proc.waitFor();
            if (exit != 0) {
                log.error("mdfra inference failed: exit={}, output=\n{}", exit, stdout);
                throw new RuntimeException("分割模型推理失败");
            }

            Path maskPath = outDir.resolve("mask.png");
            Path overlayPath = outDir.resolve("overlay.png");

            String maskBase64 = toBase64(maskPath);
            String overlayBase64 = toBase64(overlayPath);

            try { Files.deleteIfExists(tempImage); } catch (Exception ignored) {}

            return PolypSegmentationResponse.builder()
                    .maskBase64(maskBase64)
                    .overlayBase64(overlayBase64)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("分割处理异常: " + e.getMessage(), e);
        }
    }

    private Path resolveWeightsPath() {
        return resolveProjectPath("mdfra/snapshots/best/MdfraNet-best.pth");
    }

    

    private static String getFileSuffix(String name) {
        if (name == null) return ".png";
        int idx = name.lastIndexOf('.');
        if (idx < 0) return ".png";
        return name.substring(idx);
    }

    private static Path resolveProjectPath(String relative) {
        Path root = Path.of("").toAbsolutePath();
        Path projectRoot = root.getParent() != null && root.getFileName() != null && "backend".equals(root.getFileName().toString())
                ? root.getParent()
                : root;
        Path candidate1 = projectRoot.resolve(relative);
        if (Files.exists(candidate1)) return candidate1;
        Path candidate2 = projectRoot.resolve("backend").resolve(relative);
        if (Files.exists(candidate2)) return candidate2;
        return candidate1;
    }

    private static String resolvePython() {
        Path root = Path.of("").toAbsolutePath();
        Path projectRoot = root.getParent() != null && root.getFileName() != null && "backend".equals(root.getFileName().toString())
                ? root.getParent()
                : root;
        Path venvWindows1 = projectRoot.resolve("mdfra").resolve(".venv").resolve("Scripts").resolve("python.exe");
        if (Files.exists(venvWindows1)) {
            return venvWindows1.toString();
        }
        Path venvWindows2 = projectRoot.resolve("backend").resolve("mdfra").resolve(".venv").resolve("Scripts").resolve("python.exe");
        if (Files.exists(venvWindows2)) {
            return venvWindows2.toString();
        }
        Path venvLinux1 = projectRoot.resolve("mdfra").resolve(".venv").resolve("bin").resolve("python");
        if (Files.exists(venvLinux1)) {
            return venvLinux1.toString();
        }
        Path venvLinux2 = projectRoot.resolve("backend").resolve("mdfra").resolve(".venv").resolve("bin").resolve("python");
        if (Files.exists(venvLinux2)) {
            return venvLinux2.toString();
        }
        String env = System.getenv("PYTHON");
        if (env != null && !env.isBlank()) return env;
        return "python";
    }

    private static String readAll(InputStream is) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private static String toBase64(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        String mime = guessMime(path.getFileName().toString());
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static String guessMime(String filename) {
        String ext = "";
        int idx = filename.lastIndexOf('.');
        if (idx >= 0) ext = filename.substring(idx + 1).toLowerCase();
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }
}
