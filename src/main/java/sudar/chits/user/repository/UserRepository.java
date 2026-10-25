package sudar.chits.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sudar.chits.user.entity.User;

public interface UserRepository extends JpaRepository<User, Integer> {
}
