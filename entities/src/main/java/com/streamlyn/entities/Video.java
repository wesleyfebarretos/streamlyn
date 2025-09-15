package com.streamlyn.entities;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.ZonedDateTime;
import java.util.List;

@Document(collection = "videos")
@Getter @Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Video {
    @Id
    private String id;

    private String title;

    private String description;

    private List<String> tags;

    private List<String> resolutions;

    private Long uploadLength;

    private Long offset;

    private String mimeType;

    private String fileName;

    private String fileUrl;

    private String hlsUrl;

    private String thumbnailUrl;

    private Integer duration;

    @Builder.Default
    private Long views = 0L;

    @Builder.Default
    private Long likes = 0L;

    @Builder.Default
    private Long dislikes = 0L;

    private String metadata;

    @Builder.Default
    private Long commentsCount = 0L;

    private ZonedDateTime publishedAt;

    @CreatedDate
    private ZonedDateTime createdAt;

    @LastModifiedDate
    private ZonedDateTime updatedAt;

    @DBRef(lazy = true)
    private User user;
}