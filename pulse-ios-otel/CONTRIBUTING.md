# Contributing to Pulse iOS SDK

We welcome your contributions to Pulse iOS SDK!

This repository is a fork of [OpenTelemetry-Swift](https://github.com/open-telemetry/opentelemetry-swift) with custom features like PulseKit. Contributions should align with our goals of providing a simplified, production-ready SDK for iOS applications.

## Before you begin

### Project Context

Pulse iOS SDK is built on top of OpenTelemetry-Swift and follows the [OpenTelemetry specification][otel-specification]. We maintain this fork to:
- Add custom features like PulseKit
- Provide simplified APIs for iOS developers
- Stay in sync with upstream OpenTelemetry improvements

### Code Style

Contributions should:
- Follow Swift idioms and best practices
- Maintain compatibility with OpenTelemetry specifications
- Focus on simplicity and ease of use for iOS developers
- Include appropriate tests and documentation

## Getting started

Everyone is welcome to contribute code via GitHub Pull Requests (PRs).

### Fork the repo

Fork the project on GitHub by clicking the `Fork` button at the top of the
repository and clone your fork locally:

```sh
git clone git@github.com:YOUR_GITHUB_NAME/pulse-ios-sdk.git
```

or
```sh
git clone https://github.com/YOUR_GITHUB_NAME/pulse-ios-sdk.git
```

### Set up remotes

It's helpful to add both the upstream OpenTelemetry-Swift repo and this repo as remotes:

```sh
# Add the original OpenTelemetry-Swift repo (for syncing upstream changes)
git remote add opentelemetry https://github.com/open-telemetry/opentelemetry-swift.git

# Add this repo as origin (if not already set)
git remote set-url origin https://github.com/dream-horizon-org/pulse-ios-sdk.git
```

This allows you to:
- Sync upstream OpenTelemetry-Swift changes: `git fetch opentelemetry`
- Track your contributions: `git push origin`

### Build

Open `Package.swift` in Xcode and follow normal development process.


```sh
swift build
```

To build specific targets:

```sh
# Build PulseKit only
swift build --target PulseKit

# Build for iOS
make build-for-testing-ios
```

### Test

Open `Package.swift` in Xcode and follow normal testing process.

To test from the command line:

```sh
swift test
```

To test for iOS:

```sh
make test-without-building-ios
```
### Linting
#### SwiftLint
The SwiftLint Xcode plugin can be optionally enabled during development by using an environmental variable when opening the project from the commandline. 
```
OTEL_ENABLE_SWIFTLINT=1 open Package.swift
```
Note: Xcode must be completely closed before running the above command, close Xcode using `⌘Q` or running `killall xcode` in the commandline.  

#### SwiftFormat
SwiftFormat is also used to enforce formatting rules where Swiftlint isn't able.
It will also run in the optionally enabled pre-commit hook if installed via `brew install swiftformat`. 

### Make your modifications

Always work in a branch from your fork:

```sh
git checkout -b my-feature-branch
```

### Create a Pull Request

You'll need to create a Pull Request once you've finished your work.

Open the PR against the `dream-horizon-org/pulse-ios-sdk` repository.

Please put `[WIP]` in the title, or create it as a [`Draft`][github-draft] PR
if the PR is not ready for review.

#### PR Guidelines

- Provide a clear description of changes
- Reference any related issues
- Ensure all tests pass
- Update documentation if needed
- Keep PRs focused and reasonably sized

### Review and feedback

PRs will be reviewed by maintainers. We appreciate your patience and will work with you to ensure your contribution fits well with the project goals. Respond to feedback and work with reviewers to resolve any issues.

## Syncing with Upstream OpenTelemetry-Swift

Since this is a fork, you may want to sync changes from upstream:

```sh
# Fetch upstream changes
git fetch opentelemetry

# Merge upstream main into your branch
git merge opentelemetry/main --allow-unrelated-histories

# Resolve any conflicts and commit
```

See our [issue tracking template](.github/ISSUE_TEMPLATE/) for monitoring upstream Swift 6.x migration progress.

## Generating OTLP Protobuf Files

Occasionally, the OpenTelemetry protocol's protobuf definitions are updated and need to be regenerated for the OTLP exporters. This section documents how to regenerate them for Pulse iOS SDK.

#### Requirements
- [protoc]
- [grpc-swift]
- [opentelemetry-proto]

##### Install protoc
```asciidoc
$ brew install protobuf
$ protoc --version  # Ensure compiler version is 3+
```
##### Installing grpc-swift
```
 brew install swift-protobuf grpc-swift
 ```

##### Generating otlp protobuf files

Clone [opentelemetry-proto]

From within opentelemetry-proto:

```shell
# collect the proto definitions:
PROTO_FILES=($(ls opentelemetry/proto/*/*/*/*.proto opentelemetry/proto/*/*/*.proto))
# generate swift proto files
for file in "${PROTO_FILES[@]}"
do
  protoc --swift_opt=Visibility=Public --swift_out=./out ${file}
done

# genearate GRPC swift proto files
protoc --swift_opt=Visibility=Public --grpc-swift_opt=Visibility=Public  --swift_out=./out --grpc-swift_out=./out opentelemetry/proto/collector/trace/v1/trace_service.proto
protoc --swift_opt=Visibility=Public --grpc-swift_opt=Visibility=Public --swift_out=./out --grpc-swift_out=./out opentelemetry/proto/collector/metrics/v1/metrics_service.proto
protoc --swift_opt=Visibility=Public --grpc-swift_opt=Visibility=Public --swift_out=./out --grpc-swift_out=./out opentelemetry/proto/collector/logs/v1/logs_service.proto
```

Replace the generated files in `Sources/Exporters/OpenTelemetryProtocolCommon/proto` & `Sources/Exporters/OpenTelemetryGrpc/proto`:
###### `OpenTelemetryProtocolGrpc/proto` file list
`logs_service.grpc.swift`
`metrics_serivce.grpc.swift`
`trace_service.grpc.swift`

###### `OpenTelemetryProtocolCommon/proto`
`common.pb.swift`
`logs.pb.swift`
`logs_service.pb.swift`
`metrics.pb.swift`
`metrics_services.pb.swift`
`resource.pb.swift`
`trace.pb.swift`
`trace_service.pb.swift`

## Resources

- [Pulse iOS SDK Repository](https://github.com/dream-horizon-org/pulse-ios-sdk)
- [OpenTelemetry-Swift Repository](https://github.com/open-telemetry/opentelemetry-swift) - Upstream repository
- [OpenTelemetry Specification][otel-specification]
- [OpenTelemetry Community](https://github.com/open-telemetry/community)
- [PulseKit Documentation](Sources/PulseKit/README.md)

## Links

[github-draft]: https://github.blog/2019-02-14-introducing-draft-pull-requests/
[otel-specification]: https://github.com/open-telemetry/opentelemetry-specification
[grpc-swift]: https://github.com/grpc/grpc-swift
[opentelemetry-proto]: https://github.com/open-telemetry/opentelemetry-proto
[protoc]: https://grpc.io/docs/protoc-installation/
[build-tools]: https://github.com/open-telemetry/build-tools
