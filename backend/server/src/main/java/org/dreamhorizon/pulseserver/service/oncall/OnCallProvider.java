package org.dreamhorizon.pulseserver.service.oncall;

import io.reactivex.rxjava3.core.Single;
import java.util.List;

public interface OnCallProvider {

  Single<List<OnCallUser>> getOnCallUsers();
}
