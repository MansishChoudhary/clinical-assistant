package com.clinicalassistant.workflow;

import com.clinicalassistant.agent.CodingProposalAgent;
import com.clinicalassistant.agent.VerificationAgent;
import com.clinicalassistant.audit.CodingAuditService;
import com.clinicalassistant.domain.ClinicalNote;
import com.clinicalassistant.domain.CodeProposal;
import com.clinicalassistant.domain.CodingOutcome;
import com.clinicalassistant.domain.CodingResult;
import com.clinicalassistant.domain.ConfidenceLevel;
import com.clinicalassistant.domain.RetrievedEvidence;
import com.clinicalassistant.domain.VerificationResult;
import com.clinicalassistant.domain.VerificationVerdict;
import com.clinicalassistant.retrieval.EvidenceRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgenticCodingWorkflowTest {

    @Mock
    private EvidenceRetrievalService retrievalService;
    @Mock
    private CodingProposalAgent proposalAgent;
    @Mock
    private VerificationAgent verificationAgent;
    @Mock
    private CodingAuditService auditService;

    private AgenticCodingWorkflow workflow;
    private final RetrievedEvidence emptyEvidence = new RetrievedEvidence(List.of(), List.of());
    private final ClinicalNote note = new ClinicalNote("Patient presents with fever and cough.");

    @BeforeEach
    void setUp() {
        workflow = new AgenticCodingWorkflow(retrievalService, proposalAgent, verificationAgent, auditService,
                new WorkflowProperties(3));
        when(retrievalService.retrieve(any())).thenReturn(emptyEvidence);
    }

    @Test
    void firstPassSupportedYieldsHighConfidence() {
        CodeProposal proposal = new CodeProposal(List.of("CA22"), "matches pneumonia pattern", List.of("catalog:CA22"));
        when(proposalAgent.propose(any(), any(), any(), any())).thenReturn(proposal);
        when(verificationAgent.verify(any(), any(), any()))
                .thenReturn(new VerificationResult(VerificationVerdict.SUPPORTED, "evidence backs this", List.of("catalog:CA22")));

        CodingResult result = workflow.analyze(note);

        assertThat(result.outcome()).isEqualTo(CodingOutcome.CODED);
        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(result.proposedCodes()).containsExactly("CA22");
        verify(auditService, times(1)).record(any(), any(), any());
    }

    @Test
    void supportedOnlyAfterRetryYieldsMediumConfidence() {
        CodeProposal firstProposal = new CodeProposal(List.of("CA23"), "guess 1", List.of("catalog:CA23"));
        CodeProposal secondProposal = new CodeProposal(List.of("CA22"), "guess 2", List.of("catalog:CA22"));
        when(proposalAgent.propose(any(), any(), any(), any())).thenReturn(firstProposal, secondProposal);
        when(verificationAgent.verify(any(), any(), any()))
                .thenReturn(new VerificationResult(VerificationVerdict.NOT_SUPPORTED, "wrong code", List.of()))
                .thenReturn(new VerificationResult(VerificationVerdict.SUPPORTED, "this one fits", List.of("catalog:CA22")));

        CodingResult result = workflow.analyze(note);

        assertThat(result.outcome()).isEqualTo(CodingOutcome.CODED);
        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(result.proposedCodes()).containsExactly("CA22");
    }

    @Test
    void insufficientEvidenceFailsSafeImmediately() {
        CodeProposal proposal = new CodeProposal(List.of("MG30"), "vague fever", List.of("catalog:MG30"));
        when(proposalAgent.propose(any(), any(), any(), any())).thenReturn(proposal);
        when(verificationAgent.verify(any(), any(), any()))
                .thenReturn(new VerificationResult(VerificationVerdict.INSUFFICIENT_EVIDENCE, "too generic", List.of()));

        CodingResult result = workflow.analyze(note);

        assertThat(result.outcome()).isEqualTo(CodingOutcome.NO_CONFIDENT_MATCH);
        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(result.proposedCodes()).isEmpty();
        verify(verificationAgent, times(1)).verify(any(), any(), any());
    }

    @Test
    void emptyProposalFailsSafeWithoutCallingVerifier() {
        CodeProposal emptyProposal = new CodeProposal(List.of(), "nothing matches", List.of());
        when(proposalAgent.propose(any(), any(), any(), any())).thenReturn(emptyProposal);

        CodingResult result = workflow.analyze(note);

        assertThat(result.outcome()).isEqualTo(CodingOutcome.NO_CONFIDENT_MATCH);
        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.LOW);
        verify(verificationAgent, times(0)).verify(any(), any(), any());
    }

    @Test
    void exhaustingRetriesOnRepeatedNotSupportedFailsSafe() {
        CodeProposal proposal = new CodeProposal(List.of("BA40"), "angina guess", List.of("catalog:BA40"));
        when(proposalAgent.propose(any(), any(), any(), any())).thenReturn(proposal);
        when(verificationAgent.verify(any(), any(), any()))
                .thenReturn(new VerificationResult(VerificationVerdict.NOT_SUPPORTED, "not this", List.of()));

        CodingResult result = workflow.analyze(note);

        assertThat(result.outcome()).isEqualTo(CodingOutcome.NO_CONFIDENT_MATCH);
        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.LOW);
        verify(proposalAgent, times(3)).propose(any(), any(), any(), any());
    }
}
