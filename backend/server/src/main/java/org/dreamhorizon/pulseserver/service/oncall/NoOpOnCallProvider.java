package org.dreamhorizon.pulseserver.service.oncall;

import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import java.util.List;

@Singleton
public class NoOpOnCallProvider implements OnCallProvider {

  @Override
  public Single<List<OnCallUser>> getOnCallUsers() {
    return Single.just(List.of());
  }
}
