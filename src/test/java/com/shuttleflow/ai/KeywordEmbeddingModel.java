package com.shuttleflow.ai;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

/** Offline stand-in for Gemini embeddings: a bag-of-words vector, so similar wording ranks higher. */
public class KeywordEmbeddingModel implements EmbeddingModel {
    private static final int DIMENSIONS = 512;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        for (String text : request.getInstructions()) {
            embeddings.add(new Embedding(vector(text), embeddings.size()));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    static float[] vector(String text) {
        float[] vector = new float[DIMENSIONS];
        for (String word : text.toLowerCase().split("[^a-z]+")) {
            if (word.length() > 3) {
                // Crude stemming so "cancel", "cancelled" and "cancellation" share a dimension.
                String stem = word.substring(0, Math.min(word.length(), 6));
                vector[Math.floorMod(stem.hashCode(), DIMENSIONS)]++;
            }
        }
        return vector;
    }
}
