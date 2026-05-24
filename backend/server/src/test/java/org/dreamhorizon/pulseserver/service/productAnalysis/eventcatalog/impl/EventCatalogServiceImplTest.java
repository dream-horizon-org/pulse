package org.dreamhorizon.pulseserver.service.productAnalysis.eventcatalog.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog.EventCatalogDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventCatalogServiceImplTest {

  @Mock
  EventCatalogDao eventCatalogDao;

  EventCatalogServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new EventCatalogServiceImpl(eventCatalogDao);
  }

  @Test
  void listEventNames_mapsDaoToResponse() {
    when(eventCatalogDao.listEventNames("p1")).thenReturn(Single.just(List.of("A", "B")));

    var resp = service.listEventNames("p1").blockingGet();
    assertThat(resp.getEvents()).containsExactly("A", "B");
    verify(eventCatalogDao).listEventNames("p1");
  }

  @Test
  void listScreenNames_mapsDaoToResponse() {
    when(eventCatalogDao.listScreenNames("p1")).thenReturn(Single.just(List.of("Home", "Checkout")));

    var resp = service.listScreenNames("p1").blockingGet();
    assertThat(resp.getScreens()).containsExactly("Home", "Checkout");
    verify(eventCatalogDao).listScreenNames("p1");
  }

  @Test
  void listFilterKeys_mapsDaoToResponse() {
    when(eventCatalogDao.listFilterKeys("p1")).thenReturn(Single.just(List.of("OS_NAME")));

    var resp = service.listFilterKeys("p1").blockingGet();
    assertThat(resp.getFilters()).containsExactly("OS_NAME");
  }

  @Test
  void listFilterValues_mapsDaoToResponse() {
    when(eventCatalogDao.listFilterValues("p1", "OS_NAME")).thenReturn(Single.just(List.of("iOS")));

    var resp = service.listFilterValues("p1", "OS_NAME").blockingGet();
    assertThat(resp.getValues()).containsExactly("iOS");
  }
}
