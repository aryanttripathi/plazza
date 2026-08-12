package org.plazza.plazza.user.api.dto;

import org.plazza.plazza.user.UserView;

public record UserResponse(String id, String name, String phone) {

    public static UserResponse from(UserView view) {
        return new UserResponse(view.id(), view.name(), view.phone());
    }
}
