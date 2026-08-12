package com.eduadmin.school.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class NoteStorageServiceImpl implements NoteStorageService {

    private final Path uploadDir;

    public NoteStorageServiceImpl(@Value("${school.notes.upload-dir:./data/notes}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(uploadDir);
    }

    @Override
    public String store(MultipartFile file) throws IOException {
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }
        String stored = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = uploadDir.resolve(stored);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return stored;
    }

    @Override
    public Path resolve(String storedFileName) {
        return uploadDir.resolve(storedFileName).normalize();
    }

    @Override
    public void delete(String storedFileName) {
        try {
            Files.deleteIfExists(uploadDir.resolve(storedFileName));
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
