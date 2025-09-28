package com.example.codecomplexity.controller;

import com.example.codecomplexity.service.ProjectAnalysisService;
import com.example.codecomplexity.service.ProjectAnalysisService.AnalysisResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class AnalysisController {

    private final ProjectAnalysisService analysisService;

    @Autowired
    public AnalysisController(ProjectAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * Upload a ZIP file containing a Java project (or a single .java file) and receive analysis.
     * Returns a JSON with per-file and aggregated metrics.
     */
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnalysisResult> analyzeProject(@RequestParam("file") MultipartFile file) throws Exception {
        AnalysisResult result = analysisService.analyzeZipOrFile(file);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
