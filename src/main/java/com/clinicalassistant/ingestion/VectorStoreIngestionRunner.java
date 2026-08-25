package com.clinicalassistant.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VectorStoreIngestionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreIngestionRunner.class);

    private final CatalogDataLoader dataLoader;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final String vectorStoreTableName;

    public VectorStoreIngestionRunner(CatalogDataLoader dataLoader, VectorStore vectorStore, JdbcTemplate jdbcTemplate,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String vectorStoreTableName) {
        this.dataLoader = dataLoader;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.vectorStoreTableName = vectorStoreTableName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (alreadyIngested()) {
            log.info("Vector store table '{}' already contains documents; skipping ingestion.", vectorStoreTableName);
            return;
        }

        List<Document> catalogDocuments = dataLoader.loadCatalogDocuments();
        List<Document> guidelineDocuments = dataLoader.loadGuidelineDocuments();

        vectorStore.add(catalogDocuments);
        vectorStore.add(guidelineDocuments);

        log.info("Ingested {} catalog entries and {} guideline snippets into the vector store.",
                catalogDocuments.size(), guidelineDocuments.size());
    }

    private boolean alreadyIngested() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + vectorStoreTableName, Integer.class);
        return count != null && count > 0;
    }
}
