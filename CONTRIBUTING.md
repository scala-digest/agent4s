# Contributing to agent4s

Clone the repository and run the checked-in Mill launcher. It downloads the pinned Scala compiler and GraalVM. Windows users can run the same tasks with `mill.bat`.

```sh
git clone https://github.com/scala-digest/agent4s.git
cd agent4s
./mill run def WorkspaceIndex --verbose
./mill test + bench.compile
./mill mill.scalalib.scalafmt/checkFormatAll '__.sources'
```

Use `./mill test.testOnly scalex.Agent4sCliSuite` to run the fork's CLI tests. To check a native build, run `./build-native.sh` and then `bash scripts/check-native.sh ./scalex`.

The contributor guide covers the command registry, index snapshots, renderers, plugin manifests and release process. [Development guide](docs/DEVELOPMENT.md)
