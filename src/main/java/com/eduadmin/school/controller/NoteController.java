package com.eduadmin.school.controller;

import com.eduadmin.school.model.Note;
import com.eduadmin.school.model.Role;
import com.eduadmin.school.model.Student;
import com.eduadmin.school.model.User;
import com.eduadmin.school.repository.NoteRepository;
import com.eduadmin.school.repository.StudentRepository;
import com.eduadmin.school.repository.UserRepository;
import com.eduadmin.school.service.NoteStorageService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/notes")
public class NoteController {

    private static final List<String> CLASSES = List.of(
            "1-A", "1-B", "1-C", "2-A", "2-B", "2-C",
            "3-A", "3-B", "3-C", "4-A", "4-B", "4-C",
            "5-A", "5-B", "5-C", "6-A", "6-B", "6-C",
            "7-A", "7-B", "7-C", "8-A", "8-B", "8-C",
            "9-A", "9-B", "9-C", "10-A", "10-B", "10-C",
            "11-A", "11-B", "11-C", "12-A", "12-B", "12-C"
    );

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final NoteStorageService storageService;

    public NoteController(NoteRepository noteRepository,
                          UserRepository userRepository,
                          StudentRepository studentRepository,
                          NoteStorageService storageService) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.storageService = storageService;
    }

    @GetMapping
    public String list(Model model) {
        User user = currentUser();
        boolean isStudent = user != null && user.getRole() == Role.student;
        model.addAttribute("isStudent", isStudent);
        model.addAttribute("currentUser", user);

        List<Note> notes;
        if (isStudent) {
            Student me = studentRepository.findByUser(user).orElse(null);
            notes = (me == null) ? List.of() : noteRepository.findByTargetClass(me.getClassDisplay());
        } else {
            notes = noteRepository.findAllByOrderByCreatedAtDesc();
        }

        model.addAttribute("notes", notes);
        model.addAttribute("classes", CLASSES);
        model.addAttribute("activePage", "notes");
        return "notes";
    }

    /** Uploads a note for the selected target classes. Teachers and admins only. */
    @PostMapping("/upload")
    public String upload(@RequestParam String title,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) List<String> targetClasses,
                         @RequestParam("file") MultipartFile file,
                         Model model) {
        User user = currentUser();
        if (user == null || user.getRole() == Role.student) {
            return "redirect:/notes";
        }

        List<String> errorMessages = new ArrayList<>();
        String cleanTitle = title != null ? title.trim() : "";
        if (cleanTitle.isEmpty()) {
            errorMessages.add("Please provide a title for the note.");
        }
        if (file == null || file.isEmpty()) {
            errorMessages.add("Please choose a file to upload.");
        } else {
            Note.FileKind kind = classify(file.getOriginalFilename(), file.getContentType());
            if (kind == null) {
                errorMessages.add("Unsupported file type. Please upload a video, image or PDF.");
            }
        }

        Set<String> classes = (targetClasses == null) ? new HashSet<>() : new HashSet<>(targetClasses);
        classes.removeIf(String::isBlank);
        if (classes.isEmpty()) {
            errorMessages.add("Please select at least one class.");
        }

        if (!errorMessages.isEmpty()) {
            model.addAttribute("errorMessages", errorMessages);
            model.addAttribute("notes", noteRepository.findAllByOrderByCreatedAtDesc());
            model.addAttribute("classes", CLASSES);
            model.addAttribute("isStudent", false);
            model.addAttribute("currentUser", user);
            model.addAttribute("activePage", "notes");
            return "notes";
        }

        try {
            String stored = storageService.store(file);
            Note note = new Note();
            note.setTitle(cleanTitle);
            note.setDescription(description != null ? description.trim() : "");
            note.setOriginalFileName(file.getOriginalFilename());
            note.setStoredFileName(stored);
            note.setFileKind(classify(file.getOriginalFilename(), file.getContentType()));
            note.setContentType(file.getContentType());
            note.setFileSize(file.getSize());
            note.setUploadedBy(user);
            note.setTargetClasses(classes);
            note.setCreatedAt(LocalDateTime.now());
            noteRepository.save(note);
        } catch (Exception e) {
            model.addAttribute("errorMessages", List.of("Could not save the uploaded file. Please try again."));
            model.addAttribute("notes", noteRepository.findAllByOrderByCreatedAtDesc());
            model.addAttribute("classes", CLASSES);
            model.addAttribute("isStudent", false);
            model.addAttribute("currentUser", user);
            model.addAttribute("activePage", "notes");
            return "notes";
        }
        return "redirect:/notes";
    }

    /** Streams the file back. Only students in a target class (or teachers/admins) may view. */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        User user = currentUser();
        Note note = noteRepository.findById(id).orElse(null);
        if (user == null || note == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccess(user, note)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        Path path = storageService.resolve(note.getStoredFileName());
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            String contentType = note.getContentType() != null ? note.getContentType()
                    : MediaType.APPLICATION_OCTET_STREAM_VALUE;
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + note.getOriginalFileName() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Deletes a note. Only the uploader or an admin may delete. */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        User user = currentUser();
        Note note = noteRepository.findById(id).orElse(null);
        if (user == null || note == null) {
            return "redirect:/notes";
        }
        if (user.getRole() != Role.admin && !user.getId().equals(note.getUploadedBy().getId())) {
            return "redirect:/notes";
        }
        noteRepository.delete(note);
        storageService.delete(note.getStoredFileName());
        return "redirect:/notes";
    }

    /** Classifies a file by extension/content type as video, image or pdf (null = unsupported). */
    private Note.FileKind classify(String fileName, String contentType) {
        if (fileName == null) return null;
        String name = fileName.toLowerCase();
        String type = contentType != null ? contentType.toLowerCase() : "";
        if (name.endsWith(".pdf") || type.contains("pdf")) return Note.FileKind.pdf;
        if (name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".mov")
                || name.endsWith(".avi") || name.endsWith(".mkv") || type.startsWith("video/")) {
            return Note.FileKind.video;
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp")
                || type.startsWith("image/")) {
            return Note.FileKind.image;
        }
        return null;
    }

    /** Teachers/admins see everything; students only if their class is a target. */
    private boolean canAccess(User user, Note note) {
        if (user.getRole() != Role.student) return true;
        Student me = studentRepository.findByUser(user).orElse(null);
        if (me == null) return false;
        return note.getTargetClasses().contains(me.getClassDisplay());
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }
}
