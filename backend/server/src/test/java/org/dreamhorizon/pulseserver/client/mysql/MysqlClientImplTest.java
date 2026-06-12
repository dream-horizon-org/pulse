package org.dreamhorizon.pulseserver.client.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MysqlClientImpl}.
 * <p>
 * {@code MySQLPool.pool(...)} is a lazy factory: it constructs a pool wrapper but does not attempt
 * to connect to MySQL until a query is run. That makes it safe to exercise the constructor and
 * pool accessors against a real (local) {@link Vertx} instance without a MySQL server.
 */
class MysqlClientImplTest {

  private Vertx vertx;
  private JsonObject config;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    config = new JsonObject()
        .put(Constants.MYSQL_WRITER_HOST, "writer.local")
        .put(Constants.MYSQL_READER_HOST, "reader.local")
        .put(Constants.MYSQL_PORT, "3306")
        .put(Constants.MYSQL_USER, "user")
        .put(Constants.MYSQL_PASSWORD, "pass")
        .put(Constants.MYSQL_DATABASE, "pulse")
        .put(Constants.MYSQL_WRITER_MAX_POOL_SIZE, "8")
        .put(Constants.MYSQL_READER_MAX_POOL_SIZE, "4");
  }

  @AfterEach
  void tearDown() {
    vertx.close().blockingAwait();
  }

  @Nested
  class Construction {

    @Test
    void shouldCreateWriterAndReaderPools() {
      MysqlClientImpl client = new MysqlClientImpl(vertx, config);

      MySQLPool writer = client.getWriterPool();
      MySQLPool reader = client.getReaderPool();

      assertThat(writer).isNotNull();
      assertThat(reader).isNotNull();
      assertThat(writer).isNotSameAs(reader);
    }

    @Test
    void shouldThrowWhenPortIsNotAnInteger() {
      config.put(Constants.MYSQL_PORT, "not-a-number");

      assertThatThrownBy(() -> new MysqlClientImpl(vertx, config))
          .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void shouldThrowWhenWriterPoolSizeIsNotAnInteger() {
      config.put(Constants.MYSQL_WRITER_MAX_POOL_SIZE, "bogus");

      assertThatThrownBy(() -> new MysqlClientImpl(vertx, config))
          .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void shouldThrowWhenReaderPoolSizeIsNotAnInteger() {
      config.put(Constants.MYSQL_READER_MAX_POOL_SIZE, "bogus");

      assertThatThrownBy(() -> new MysqlClientImpl(vertx, config))
          .isInstanceOf(NumberFormatException.class);
    }
  }

  @Nested
  class LifecycleMethods {

    private MysqlClientImpl client;

    @BeforeEach
    void init() {
      client = new MysqlClientImpl(vertx, config);
    }

    @Test
    void rxConnectShouldCompleteImmediately() {
      Completable connect = client.rxConnect();

      connect.test()
          .assertComplete()
          .assertNoErrors();
    }

    @Test
    void rxCloseShouldCompleteWithoutError() {
      // Pools have never been used, so closing them should succeed quickly.
      client.rxClose()
          .test()
          .awaitDone(5, java.util.concurrent.TimeUnit.SECONDS)
          .assertComplete();
    }

    @Test
    void getWriterPoolShouldReturnSameInstance() {
      assertThat(client.getWriterPool()).isSameAs(client.getWriterPool());
    }

    @Test
    void getReaderPoolShouldReturnSameInstance() {
      assertThat(client.getReaderPool()).isSameAs(client.getReaderPool());
    }
  }
}
