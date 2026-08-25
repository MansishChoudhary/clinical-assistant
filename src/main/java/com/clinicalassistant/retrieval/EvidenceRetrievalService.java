package com.clinicalassistant.retrieval;

import com.clinicalassistant.domain.EvidenceItem;
import com.clinicalassistant.domain.EvidenceSourceType;
import com.clinicalassistant.domain.RetrievedEvidence;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class EvidenceRetrievalService {

    private final VectorStore vectorStore;
    private final RetrievalProperties properties;

    public EvidenceRetrievalService(VectorStore vectorStore, RetrievalProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public RetrievedEvidence retrieve(String query) {
        List<EvidenceItem> catalogMatches = search(query, EvidenceSourceType.CATALOG);
        List<EvidenceItem> guidelineMatches = search(query, EvidenceSourceType.GUIDELINE);
        return new RetrievedEvidence(catalogMatches, guidelineMatches);
    }

    private List<EvidenceItem> search(String query, EvidenceSourceType sourceType) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(properties.topK())
                .similarityThreshold(properties.similarityThreshold())
                .filterExpression("sourceType == '" + sourceType.name() + "'")
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);
        return documents.stream()
                .map(document -> toEvidenceItem(document, sourceType))
                .toList();
    }

    private EvidenceItem toEvidenceItem(Document document, EvidenceSourceType sourceType) {
        Map<String, Object> metadata = document.getMetadata();
        String sourceId = String.valueOf(metadata.getOrDefault("sourceId", document.getId()));

        List<String> relatedCodes = switch (sourceType) {
            case CATALOG -> List.of(String.valueOf(metadata.getOrDefault("code", "")));
            case GUIDELINE -> Arrays.stream(String.valueOf(metadata.getOrDefault("linkedCodes", "")).split(","))
                    .filter(code -> !code.isBlank())
                    .toList();
        };

        String title = sourceType == EvidenceSourceType.CATALOG
                ? String.valueOf(metadata.getOrDefault("title", ""))
                : sourceId;

        Double score = document.getScore();
        return new EvidenceItem(sourceType, sourceId, relatedCodes, title, document.getText(),
                score == null ? 0.0 : score);
    }
}
