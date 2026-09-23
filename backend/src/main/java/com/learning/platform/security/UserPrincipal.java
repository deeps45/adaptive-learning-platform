package com.learning.platform.security;

import java.util.UUID;

/** The authenticated principal extracted from a validated JWT access token. */
public record UserPrincipal(UUID id, String email, String role) {}
