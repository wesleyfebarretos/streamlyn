package com.streamlyn.api.web.tus;

import com.streamlyn.api.domain.exception.ApiException;
import org.apache.logging.log4j.util.Strings;

import java.util.ArrayList;
import java.util.List;

public class TusUploadMetadataValidatorService {
    private final TusUploadMetadataReadService metadataReadService;

    public TusUploadMetadataValidatorService(TusUploadMetadataReadService metadataReadService) {
        this.metadataReadService = metadataReadService;
    }

    public void validate() {
        List<String> requiredFields = new ArrayList<>();

        if(metadataReadService.getTitle().isEmpty()) {
            requiredFields.add("title");
        }

        if(metadataReadService.getFileName().isEmpty()) {
            requiredFields.add("filename");
        }

        if(metadataReadService.getFileType().isEmpty()) {
            requiredFields.add("filetype");
        }

        if(!requiredFields.isEmpty()) {
            throw ApiException.badRequest(String.format("missing %s in Upload-Metadata", Strings.join(requiredFields, ',')));
        }
    }
}
