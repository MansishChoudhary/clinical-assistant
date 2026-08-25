package com.clinicalassistant.retrieval;

import com.clinicalassistant.domain.EvidenceItem;
import com.clinicalassistant.domain.RetrievedEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvidenceRetrievalServiceTest {

    @Mock
    private VectorStore vectorStore;

    private final RetrievalProperties properties = new RetrievalProperties(5, 0.5);

    @Test
    void mapsCatalogAndGuidelineMatchesSeparately() {
        Document catalogDoc = Document.builder()
                .id("catalog:CA22")
                .text("Community-acquired pneumonia (CA22). Acute lung infection.")
                .metadata(Map.of("sourceType", "CATALOG", "sourceId", "CA22", "code", "CA22", "title", "Community-acquired pneumonia"))
                .score(0.91)
                .build();

        Document guidelineDoc = Document.builder()
                .id("guideline:GDL-003")
                .text("Distinguishing pneumonia from acute bronchitis...")
                .metadata(Map.of("sourceType", "GUIDELINE", "sourceId", "GDL-003", "linkedCodes", "CA22,CA23"))
                .score(0.82)
                .build();

        when(vectorStore.similaritySearch(argThat((SearchRequest req) ->
                req != null && req.hasFilterExpression() && req.getFilterExpression().toString().contains("CATALOG"))))
                .thenReturn(List.of(catalogDoc));
        when(vectorStore.similaritySearch(argThat((SearchRequest req) ->
                req != null && req.hasFilterExpression() && req.getFilterExpression().toString().contains("GUIDELINE"))))
                .thenReturn(List.of(guidelineDoc));

        EvidenceRetrievalService service = new EvidenceRetrievalService(vectorStore, properties);
        RetrievedEvidence evidence = service.retrieve("fever and productive cough");

        assertThat(evidence.catalogMatches()).hasSize(1);
        EvidenceItem catalogItem = evidence.catalogMatches().get(0);
        assertThat(catalogItem.sourceId()).isEqualTo("CA22");
        assertThat(catalogItem.relatedCodes()).containsExactly("CA22");

        assertThat(evidence.guidelineMatches()).hasSize(1);
        EvidenceItem guidelineItem = evidence.guidelineMatches().get(0);
        assertThat(guidelineItem.sourceId()).isEqualTo("GDL-003");
        assertThat(guidelineItem.relatedCodes()).containsExactly("CA22", "CA23");
    }

    @Test
    void returnsEmptyEvidenceWhenNothingMatches() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        EvidenceRetrievalService service = new EvidenceRetrievalService(vectorStore, properties);
        RetrievedEvidence evidence = service.retrieve("unrelated query");

        assertThat(evidence.isEmpty()).isTrue();
    }
}
