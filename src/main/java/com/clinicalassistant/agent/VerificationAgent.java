package com.clinicalassistant.agent;

import com.clinicalassistant.domain.ClinicalNote;
import com.clinicalassistant.domain.CodeProposal;
import com.clinicalassistant.domain.EvidenceItem;
import com.clinicalassistant.domain.RetrievedEvidence;
import com.clinicalassistant.domain.VerificationResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class VerificationAgent {

    private final ChatClient chatClient;

    public VerificationAgent(@Qualifier("verificationChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public VerificationResult verify(ClinicalNote note, RetrievedEvidence evidence, CodeProposal proposal) {
        return chatClient.prompt()
                .user(buildPrompt(note, evidence, proposal))
                .call()
                .entity(VerificationResult.class);
    }

    private String buildPrompt(ClinicalNote note, RetrievedEvidence evidence, CodeProposal proposal) {
        StringBuilder sb = new StringBuilder();
        sb.append("Clinical note:\n").append(note.text()).append("\n\n");
        sb.append("Proposed code(s): ").append(String.join(", ", proposal.proposedCodes())).append("\n");
        sb.append("Proposer's rationale: ").append(proposal.rationale()).append("\n");
        sb.append("Evidence ids the proposer cited: ").append(String.join(", ", proposal.citedEvidenceIds())).append("\n\n");
        sb.append("Full retrieved evidence available (verify against all of it, not just the cited subset):\n");
        sb.append(formatEvidence(evidence.catalogMatches())).append("\n");
        sb.append(formatEvidence(evidence.guidelineMatches())).append("\n\n");
        sb.append("Independently determine whether the evidence actually supports the proposed code(s) for this specific note.");
        return sb.toString();
    }

    private String formatEvidence(List<EvidenceItem> items) {
        if (items.isEmpty()) {
            return "(none retrieved)";
        }
        return items.stream()
                .map(item -> "- [%s] %s: %s".formatted(item.sourceId(), item.title(), item.content()))
                .collect(Collectors.joining("\n"));
    }
}
