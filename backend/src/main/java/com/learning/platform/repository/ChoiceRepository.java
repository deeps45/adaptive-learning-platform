package com.learning.platform.repository;

import com.learning.platform.entity.Choice;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChoiceRepository extends JpaRepository<Choice, UUID> {}
