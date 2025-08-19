package com.streamlyn.api.web.controllers.VIdeo;

import com.streamlyn.api.domain.services.VideoService;
import com.streamlyn.entities.Video;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("videos")
public class VideoController {
    private final VideoService videoService;

    @GetMapping
    public List<Video> findAll() {
        return videoService.findALl();
    }
}
