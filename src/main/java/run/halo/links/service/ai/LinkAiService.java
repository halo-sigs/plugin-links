package run.halo.links.service.ai;

import reactor.core.publisher.Mono;
import run.halo.links.dto.LinkCommentExtractionResult;
import run.halo.links.dto.LinkCommentRecognitionRequest;
import run.halo.links.dto.LinkCommentRecognitionResult;

/**
 * Plugin-owned boundary around the optional AI Foundation integration.
 */
public interface LinkAiService {

    Mono<LinkCommentExtractionResult> extract(String content, String modelName);

    Mono<LinkCommentRecognitionResult> recognize(LinkCommentRecognitionRequest request,
        String modelName);

    Mono<Boolean> isOperational(String modelName);
}
