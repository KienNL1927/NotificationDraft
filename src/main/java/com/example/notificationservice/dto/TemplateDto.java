// TemplateDto.java
package com.example.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateDto {

    private Integer id;

    @NotBlank(message = "Template name is required")
    @Size(max = 100, message = "Template name must be less than 100 characters")
    private String name;

    @NotBlank(message = "Template type is required")
    @Size(max = 20, message = "Template type must be less than 20 characters")
    private String type;

    @Size(max = 255, message = "Subject must be less than 255 characters")
    private String subject;

    @NotBlank(message = "Template body is required")
    private String body;

    private Map<String, Object> variables;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
