package com.eduadmin.school.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface NoteStorageService {
    /** Saves the uploaded file and returns a unique stored file name. */
    String store(MultipartFile file) throws IOException;
    java.nio.file.Path resolve(String storedFileName);
    void delete(String storedFileName);
}
