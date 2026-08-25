package com.clinicalassistant.workflow;

import com.clinicalassistant.agent.CodingProposalAgent;
import com.clinicalassistant.agent.VerificationAgent;
import com.clinicalassistant.audit.CodingAuditService;
import com.clinicalassistant.audit.IterationRecord;
import com.clinicalassistant.domain.ClinicalNote;
import com.clinicalassistant.domain.CodeProposal;
import com.clinicalassistant.domain.CodingResult;
import com.clinicalassistant.domain.ConfidenceLevel;
import com.clinicalassistant.domain.RetrievedEvidence;
import com.clinicalassistant.domain.VerificationResult;
import com.clinicalassistant.domain.VerificationVerdict;
import com.clinicalassistant.retrieval.EvidenceRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgenticCodingWorkflow {

    private static final Logger log = LoggerFactory.getLogger(AgenticCodingWorkflow.class);

    private final EvidenceRetrievalService retrievalService;
    private final CodingProposalAgent proposalAgent;
    private final VerificationAgent verificationAgent;
    private final CodingAuditService auditService;
    private final WorkflowProperties properties;

    public AgenticCodingWorkflow(EvidenceRetrievalService retrievalService, CodingProposalAgent proposalAgent,
            VerificationAgent verificationAgent, CodingAuditService auditService, WorkflowProperties properties) {
        this.retrievalService = retrievalService;
        this.proposalAgent = proposalAgent;
        this.verificationAgent = verificationAgent;
        this.auditService = auditService;
        this.properties = properties;
    }

    public CodingResult analyze(ClinicalNote note) {
        RetrievedEvidence evidence = retrievalService.retrieve(note.text());
        List<IterationRecord> iterations = new ArrayList<>();
        List<String> excludedCodes = new ArrayList<>();
        String priorFeedback = null;
        CodingResult result = null;

        for (int iteration = 0; iteration < properties.maxIterations(); iteration++) {
            CodeProposal proposal = proposalAgent.propose(note, evidence, excludedCodes, priorFeedback);

            if (proposal.isEmpty()) {
                iterations.add(new IterationRecord(iteration, proposal, null));
                result = CodingResult.noConfidentMatch(evidence,
                        "Proposal agent found no evidence-backed candidate: " + proposal.rationale());
                break;
            }

            VerificationResult verification = verificationAgent.verify(note, evidence, proposal);
            iterations.add(new IterationRecord(iteration, proposal, verification));

            if (verification.verdict() == VerificationVerdict.SUPPORTED) {
                ConfidenceLevel confidence = iteration == 0 ? ConfidenceLevel.HIGH : ConfidenceLevel.MEDIUM;
                result = CodingResult.coded(proposal.proposedCodes(), evidence, confidence, verification.rationale());
                break;
            }

            if (verification.verdict() == VerificationVerdict.INSUFFICIENT_EVIDENCE) {
                result = CodingResult.noConfidentMatch(evidence,
                        "Verifier found the retrieved evidence insufficient: " + verification.rationale());
                break;
            }

            // NOT_SUPPORTED: exclude these codes and retry, steering the proposer with the verifier's feedback.
            excludedCodes.addAll(proposal.proposedCodes());
            priorFeedback = verification.rationale();
        }

        if (result == null) {
            result = CodingResult.noConfidentMatch(evidence,
                    "Exhausted " + properties.maxIterations() + " propose/verify iterations without a supported code.");
        }

        auditService.record(note.text(), result, iterations);
        log.info("Coding workflow finished with outcome={} confidence={} after {} iteration(s)",
                result.outcome(), result.confidence(), iterations.size());
        return result;
    }
}
