package com.magambell.server.review.adapter.out.persistence;

import com.magambell.server.review.app.port.out.response.ReviewPreSignedUrlImage;
import java.util.List;

public record ReviewRegisterResponse(
        String id,
        List<ReviewPreSignedUrlImage> reviewPreSignedUrlImages
) {
    public ReviewRegisterResponse {
        reviewPreSignedUrlImages = List.copyOf(reviewPreSignedUrlImages);
    }
}
