package com.clinic.auth;

import java.util.List;
import java.util.UUID;

public record UserSession(UUID id, String fullName, String email, List<String> roles, List<MembershipSummary> memberships) {}
