package io.github.gdpe_franco.internaltransactions.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank @Size(max = 120) String ownerName,
        @NotBlank @Pattern(regexp = "MXN|USD|EUR") String currency) {
}
