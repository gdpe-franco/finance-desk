package io.github.gdpe_franco.internaltransactions.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(@NotBlank @Size(max = 120) String ownerName) {
}
