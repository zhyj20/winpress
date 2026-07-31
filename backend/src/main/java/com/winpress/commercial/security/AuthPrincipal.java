package com.winpress.commercial.security;

import java.util.List;

public record AuthPrincipal(Long userId, String userNo, Long organizationId, String organizationName,
                            String username, String displayName, String mobile, String email,
                            String role, List<String> permissions) {}
