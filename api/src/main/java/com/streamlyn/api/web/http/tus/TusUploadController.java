package com.streamlyn.api.web.http.tus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tus/videos")
public class TusUploadController {
    private final TusUploadService tusUploadService;

    @RequestMapping(method = RequestMethod.OPTIONS)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void protocolConfiguration(HttpServletResponse res) {
        tusUploadService.protocolConfiguration(res);
    }

    @RequestMapping(method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.CREATED)
    public void create(HttpServletRequest req, HttpServletResponse res) {
        tusUploadService.createUpload(req, res);
    }

    @RequestMapping(
            method = RequestMethod.PATCH,
            path = "{videoId}",
            consumes = "application/offset+octet-stream"
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadChunk(HttpServletRequest req, HttpServletResponse res, @PathVariable String videoId) {
        tusUploadService.uploadChunk(req, res, videoId);
    }

    @RequestMapping(method = RequestMethod.HEAD, path = "{videoId}")
    public void getOffset(HttpServletResponse res, @PathVariable String videoId) {
        tusUploadService.getUploadOffset(res, videoId);
    }
}
