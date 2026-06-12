package org.dreamhorizon.pulseserver.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

public class ValidationModule extends AbstractModule {
  @Override
  protected void configure() {
    // nothing here, or any other bindings you have
  }

  @Provides
  @Singleton
  ValidatorFactory provideValidatorFactory() {
    return Validation.buildDefaultValidatorFactory();
  }

  @Provides
  @Singleton
  Validator provideValidator(ValidatorFactory factory) {
    return factory.getValidator();
  }
}