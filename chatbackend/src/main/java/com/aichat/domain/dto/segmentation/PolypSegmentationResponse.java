package com.aichat.domain.dto.segmentation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolypSegmentationResponse {
    private String maskBase64;
    private String overlayBase64;
}