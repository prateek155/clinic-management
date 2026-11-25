package com.clinic.security;

public record FirebasePrincipal(String uid, String email, String name, boolean emailVerified) {}
