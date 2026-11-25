package com.clinic.auth;

import com.clinic.clinic.MembershipRole;
import java.util.UUID;

public record MembershipSummary(UUID memberId, UUID clinicId, String clinicName, MembershipRole role) {}
