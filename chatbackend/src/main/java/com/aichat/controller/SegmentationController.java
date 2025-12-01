package com.aichat.controller;

import com.aichat.domain.dto.common.ApiResponse;
import com.aichat.domain.dto.segmentation.PolypSegmentationResponse;
import com.aichat.security.UserPrincipal;
import com.aichat.service.segmentation.CaraNetSegmentationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/polyp")
@RequiredArgsConstructor
public class SegmentationController {

    private final CaraNetSegmentationService segmentationService;

    @PostMapping("/segment")
    public ApiResponse<PolypSegmentationResponse> segment(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam("file") MultipartFile file) {
        PolypSegmentationResponse resp = segmentationService.segment(file);
        return ApiResponse.success(resp);
    }
}