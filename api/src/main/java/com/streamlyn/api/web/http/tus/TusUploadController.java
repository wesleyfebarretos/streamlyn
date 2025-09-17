package com.streamlyn.api.web.http.tus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("files")
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

    @RequestMapping(method = RequestMethod.PATCH, path = "{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadChunk(HttpServletRequest req, HttpServletResponse res, @PathVariable String fileId) {
        tusUploadService.uploadChunk(req, res, fileId);
    }

    @RequestMapping(method = RequestMethod.HEAD, path = "{fileId}")
    public void getOffset(HttpServletResponse res, @PathVariable String fileId) {
        tusUploadService.getUploadOffset(res, fileId);
    }
}
