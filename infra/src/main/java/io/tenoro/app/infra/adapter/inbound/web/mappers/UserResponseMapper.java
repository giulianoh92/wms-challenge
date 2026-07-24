package io.tenoro.app.infra.adapter.inbound.web.mappers;

import io.tenoro.app.api.dto.UserResponse;
import io.tenoro.app.domain.model.User;

public class UserResponseMapper {

    public static UserResponse fromDomain(User user) {
        return UserResponse.builder()
                .id(user.getId().getValue())
                .name(user.getName())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
