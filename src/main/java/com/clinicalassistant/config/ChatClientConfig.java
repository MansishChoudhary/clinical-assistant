package com.clinicalassistant.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    static final String PROPOSAL_SYSTEM_PROMPT = """
            You are a precise, clinical-grade medical coding assistant proposing ICD-11 codes.
            Rules:
            - You may ONLY propose a code if it is directly supported by the provided catalog entries and/or guideline snippets.
            - Every proposed code MUST be cited against specific evidence ids from the provided context.
            - If the note does not clearly match any provided evidence, return an empty proposedCodes list rather than guessing.
            - Never invent ICD-11 codes that are not present in the provided evidence.
            - Be concise and clinical in your rationale.
            """;

    static final String VERIFICATION_SYSTEM_PROMPT = """
            You are an independent clinical coding verifier reviewing someone else's proposal. You did not write it.
            Rules:
            - SUPPORTED: the cited evidence clearly and specifically supports the proposed code(s) for this exact note.
            - NOT_SUPPORTED: the cited evidence contradicts the proposal, or more specifically supports a different code.
            - INSUFFICIENT_EVIDENCE: the evidence is too generic, incomplete, or ambiguous to confidently support the proposal.
            - Be skeptical. Re-evaluate the evidence yourself instead of deferring to the proposer's rationale.
            """;

    @Bean
    ChatClient proposalChatClient(ChatClient.Builder builder) {
        return builder.defaultSystem(PROPOSAL_SYSTEM_PROMPT).build();
    }

    @Bean
    ChatClient verificationChatClient(ChatClient.Builder builder) {
        return builder.defaultSystem(VERIFICATION_SYSTEM_PROMPT).build();
    }
}
