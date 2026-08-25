package com.clinicalassistant.agent;

import com.clinicalassistant.domain.ClinicalNote;
import com.clinicalassistant.domain.CodeProposal;
import com.clinicalassistant.domain.EvidenceItem;
import com.clinicalassistant.domain.RetrievedEvidence;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class CodingProposalAgent {

    private final ChatClient chatClient;

    public CodingProposalAgent(@Qualifier("proposalChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public CodeProposal propose(ClinicalNote note, RetrievedEvidence evidence, List<String> excludedCodes,
            String priorRejectionFeedback) {
        return chatClient.prompt()
                .user(buildPrompt(note, evidence, excludedCodes, priorRejectionFeedback))
                .call()
                .entity(CodeProposal.class);
    }

    private String buildPrompt(ClinicalNote note, RetrievedEvidence evidence, List<String> excludedCodes,
            String priorRejectionFeedback) {
        StringBuilder sb = new StringBuilder();
        sb.append("Clinical note:\n").append(note.text()).append("\n\n");
        sb.append("Retrieved ICD-11 catalog entries:\n").append(formatEvidence(evidence.catalogMatches())).append("\n\n");
        sb.append("Retrieved guideline snippets:\n").append(formatEvidence(evidence.guidelineMatches())).append("\n");
        if (!excludedCodes.isEmpty()) {
            sb.append("\nThese codes were already rejected on verification and must NOT be proposed again unless newly-cited evidence justifies them: ")
                    .append(String.join(", ", excludedCodes)).append("\n");
        }
        if (priorRejectionFeedback != null && !priorRejectionFeedback.isBlank()) {
            sb.append("\nAn independent verifier rejected your previous proposal for this reason, take it into account: ")
                    .append(priorRejectionFeedback).append("\n");
        }
        sb.append("\nPropose ICD-11 code(s) for this note, citing only the evidence ids listed above by their bracketed id. ")
                .append("If nothing in the evidence clearly matches, return an empty proposedCodes list instead of guessing.");
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
