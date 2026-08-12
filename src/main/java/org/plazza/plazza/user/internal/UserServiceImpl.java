package org.plazza.plazza.user.internal;

import org.plazza.plazza.common.error.NotFoundException;
import org.plazza.plazza.common.error.ValidationException;
import org.plazza.plazza.common.text.Texts;
import org.plazza.plazza.user.RegisterUserCommand;
import org.plazza.plazza.user.UserService;
import org.plazza.plazza.user.UserView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserServiceImpl implements UserService {

    private final UserJpaRepository repository;

    UserServiceImpl(UserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public UserView register(RegisterUserCommand command) {
        String name = Texts.requireNonBlank(command.name(), "name");
        String phone = Texts.requireNonBlank(command.phone(), "phone");

        // A friendly 400 for the common case. The uk_users_phone index remains the real guarantee:
        // two simultaneous registrations of the same number cannot both commit, and the loser
        // surfaces as a constraint violation rather than a duplicate rider.
        if (repository.existsByPhone(phone)) {
            throw new ValidationException("phone " + phone + " is already registered");
        }

        return toView(repository.save(new UserEntity(name, phone)));
    }

    @Override
    @Transactional(readOnly = true)
    public UserView requireById(String id) {
        return repository.findById(id)
                .map(UserServiceImpl::toView)
                .orElseThrow(() -> new NotFoundException("user", id));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(String id) {
        return id != null && repository.existsById(id);
    }

    private static UserView toView(UserEntity entity) {
        return new UserView(entity.getId(), entity.getName(), entity.getPhone());
    }
}
