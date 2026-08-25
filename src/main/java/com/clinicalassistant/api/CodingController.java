package com.clinicalassistant.api;

import com.clinicalassistant.domain.ClinicalNote;
import com.clinicalassistant.domain.CodingResult;
import com.clinicalassistant.workflow.AgenticCodingWorkflow;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CodingController {

    private final AgenticCodingWorkflow workflow;

    public CodingController(AgenticCodingWorkflow workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/api/coding/analyze")
    public CodingResult analyze(@Valid @RequestBody AnalyzeNoteRequest request) {
        return workflow.analyze(new ClinicalNote(request.text()));
    }
}
