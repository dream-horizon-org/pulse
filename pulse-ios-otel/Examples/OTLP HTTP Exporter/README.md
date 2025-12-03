### OTLP Exporter Example

This example shows how to use OTLP HTTP Exporter to instrument a simple Swift application.

This example will export spans data using OTLP HTTP Exporter. It will use [proto format](https://github.com/open-telemetry/opentelemetry-proto).


## Run the Application

1. Run docker: This will start otel-collector, Zipkin and Prometheus

    ```shell script
    # from this directory
    docker-compose up
    ```

2. Run  app

    ```shell script
    # from this directory
    swift run OTLPHTTPExporter
    ```

3. Teardown the docker images

    ```shell script
    # from this directory
    docker-compose down
    ```

4. Open page at <http://localhost:9411/zipkin/> -  you should be able to see the spans in zipkin
![Screenshot of the running example](images/zipkin-spans.png)

### Prometheus UI

The prometheus client will be available at <http://localhost:9090>.

Note: It may take some time for the application metrics to appear on the Prometheus dashboard.
![Screenshot of the running example](images/prometheus-metrics.png)

5. If you don't set service.name the default name of the service and spans generate by the OTLP Exporter is `unknown_service:otlpexporter` You can either set the service.name by editing the schema in Xcode and the set the environment variable for OTEL_RESOURCE_ATTRIBUTES, or set it via command line:

    ```shell script
    # from this directory
    OTEL_RESOURCE_ATTRIBUTES="service.name=my-swift-app,service.version=v1.2.3" swift run OTLPExporter
    ```
This will create a service and spans with the name `my-swift-app`

## LICENSE

Apache License 2.0
