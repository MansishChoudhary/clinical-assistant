package com.clinicalassistant.ingestion;

import com.clinicalassistant.domain.EvidenceSourceType;
import com.clinicalassistant.domain.GuidelineSnippet;
import com.clinicalassistant.domain.IcdCatalogEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CatalogDataLoader {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final IngestionProperties properties;

    public CatalogDataLoader(ResourceLoader resourceLoader, ObjectMapper objectMapper, IngestionProperties properties) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<Document> loadCatalogDocuments() {
        List<IcdCatalogEntry> entries = readJson(properties.catalogPath(), IcdCatalogEntry[].class);
        return entries.stream()
                .map(entry -> Document.builder()
                        .id(deterministicId("catalog:" + entry.code()))
                        .text(entry.title() + " (" + entry.code() + "). " + entry.description())
                        .metadata(Map.of(
                                "sourceType", EvidenceSourceType.CATALOG.name(),
                                "sourceId", entry.code(),
                                "code", entry.code(),
                                "title", entry.title(),
                                "chapter", entry.chapter() == null ? "" : entry.chapter()))
                        .build())
                .toList();
    }

    public List<Document> loadGuidelineDocuments() {
        List<GuidelineSnippet> snippets = readJson(properties.guidelinePath(), GuidelineSnippet[].class);
        return snippets.stream()
                .map(snippet -> Document.builder()
                        .id(deterministicId("guideline:" + snippet.id()))
                        .text(snippet.text())
                        .metadata(Map.of(
                                "sourceType", EvidenceSourceType.GUIDELINE.name(),
                                "sourceId", snippet.id(),
                                "linkedCodes", String.join(",", snippet.linkedCodes())))
                        .build())
                .toList();
    }

    private String deterministicId(String naturalKey) {
        return UUID.nameUUIDFromBytes(naturalKey.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private <T> List<T> readJson(String path, Class<T[]> arrayType) {
        Resource resource = resourceLoader.getResource(path);
        try (InputStream inputStream = resource.getInputStream()) {
            T[] array = objectMapper.readValue(inputStream, arrayType);
            return List.of(array);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load ingestion data from " + path, e);
        }
    }
}
