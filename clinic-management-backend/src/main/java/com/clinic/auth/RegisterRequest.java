package com.clinic.auth;

import jakarta.validation.constraints.Size;

public record RegisterRequest(@Size(max = 120) String fullName) {}
