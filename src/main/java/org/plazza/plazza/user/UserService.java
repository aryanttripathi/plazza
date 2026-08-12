package org.plazza.plazza.user;

/**
 * The user module's entire public surface. Other modules depend on this interface and on
 * {@link UserView}; the entity and its repository stay private to {@code user.internal}.
 */
public interface UserService {

    UserView register(RegisterUserCommand command);

    /**
     * @throws org.plazza.plazza.common.error.NotFoundException when no such rider exists — booking
     *         calls this so an unknown rider fails before any driver is reserved
     */
    UserView requireById(String id);

    boolean exists(String id);
}
