package org.dreamhorizon.pulsealertscron.module;

import com.google.inject.AbstractModule;
import io.vertx.core.Vertx;

public abstract class VertxAbstractModule extends AbstractModule {

    protected final Vertx vertx;

    public VertxAbstractModule(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    protected void configure() {
        bindConfiguration();
    }

    protected abstract void bindConfiguration();
}

