package com.eqdom.document.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.eqdom.document.exception.StorageException;

@Service
public class FileStorageService {

    private final Path storageRoot;

    public FileStorageService(@Value("${document.storage.path}") String storagePath) {
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException ex) {
            throw new StorageException("Could not initialize storage directory: " + storageRoot, ex);
        }
    }

    public String store(Long creditApplicationId, MultipartFile file) {
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = originalName.substring(dotIndex);
        }
        String storedFilename = UUID.randomUUID() + extension;

        Path applicationDir = storageRoot.resolve(String.valueOf(creditApplicationId)).normalize();
        if (!applicationDir.startsWith(storageRoot)) {
            throw new StorageException("Invalid storage path", null);
        }

        try {
            Files.createDirectories(applicationDir);
            Path target = applicationDir.resolve(storedFilename);
            try (var inputStream = file.getInputStream()) {
                Files.copy(inputStream, target);
            }
            return storageRoot.relativize(target).toString();
        } catch (IOException ex) {
            throw new StorageException("Failed to store file: " + originalName, ex);
        }
    }

    public byte[] load(String relativePath) {
        Path target = storageRoot.resolve(relativePath).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new StorageException("Invalid storage path", null);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new StorageException("Failed to read file: " + relativePath, ex);
        }
    }

    public void delete(String relativePath) {
        Path target = storageRoot.resolve(relativePath).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new StorageException("Invalid storage path", null);
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new StorageException("Failed to delete file: " + relativePath, ex);
        }
    }
}
