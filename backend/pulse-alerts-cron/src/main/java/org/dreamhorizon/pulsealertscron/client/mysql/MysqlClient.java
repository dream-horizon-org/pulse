package org.dreamhorizon.pulsealertscron.client.mysql;

import io.reactivex.rxjava3.core.Completable;
import io.vertx.rxjava3.mysqlclient.MySQLPool;

public interface MysqlClient {
    
    Completable rxConnect();
    
    MySQLPool getMasterMysqlClient();
    
    void close();
}

