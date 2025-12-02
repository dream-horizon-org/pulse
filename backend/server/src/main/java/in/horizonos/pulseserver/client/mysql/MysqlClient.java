package in.horizonos.pulseserver.client.mysql;

import io.reactivex.rxjava3.core.Completable;
import io.vertx.rxjava3.mysqlclient.MySQLPool;

public interface MysqlClient {

  MySQLPool getWriterPool();

  MySQLPool getReaderPool();

  Completable rxConnect();

  Completable rxClose();
}
