package org.dreamhorizon.pulseserver.error;

public class EventDefinitionNotFoundException extends RuntimeException {

  public EventDefinitionNotFoundException(Long id) {
    super("Event definition not found with id: " + id);
  }

  public EventDefinitionNotFoundException(String eventName) {
    super("Event definition not found with name: " + eventName);
  }
}
