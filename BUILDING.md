# Building WorldPainter

This fork builds with nothing but a JDK and Maven: the commercial JIDE docking framework and the Maven toolchain setup
that the original project required are no longer needed.

## Requirements

- **JDK 21 or later** (any distribution: Temurin, Homebrew `openjdk@21`, Oracle, ...). It must be a full JDK, since
  packaging uses `jpackage`.
- **Maven 3.9 or later**.

On macOS with Homebrew:

```bash
brew install openjdk@21 maven
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
```

A few dependencies are not in Maven Central; they are downloaded automatically from the WorldPainter repository
(`https://www.worldpainter.net/maven-repo/`), which is configured in the POM.

## Build

From the `WorldPainter` directory:

```bash
mvn -DskipTests install
```

The tests for the procedural terrain and edge blending features are quick:

```bash
mvn -pl WPCore test -Dtest=ProceduralTerrainTest -Dsurefire.failIfNoSpecifiedTests=false
```

## Run

```bash
mvn -pl WPGUI exec:exec
```

This runs WorldPainter with the JDK that runs Maven, allowing it to use up to 75% of the physical memory. You can also
run the main class, `org.pepsoft.worldpainter.Main`, from your IDE.

## Create a native application

`packaging/package.sh` builds a self-contained application for the current operating system, with a bundled Java
runtime, in the `dist` directory at the root of the repository:

```bash
packaging/package.sh            # macOS: dist/WorldPainter.app; Windows and Linux: dist/WorldPainter folder
packaging/package.sh dmg        # macOS disk image
packaging/package.sh deb        # Linux (also: rpm)
packaging/package.sh msi        # Windows installer (requires the WiX toolset; also: exe)
```

On Windows, run the script from Git Bash. The application is not code signed: on macOS, open it the first time with
right-click > Open, or remove the quarantine flag with `xattr -dr com.apple.quarantine WorldPainter.app`.

The GitHub Actions workflow in `.github/workflows/build.yml` runs the tests and builds packages for macOS (Apple Silicon
and Intel), Windows and Linux on every push.

## Develop WorldPainter

For a few pointers, pitfalls and gotchas about developing WorldPainter, see
[this page](https://www.worldpainter.net/trac/wiki/DevelopingWorldPainter).

- The user interface uses [FlatLaf](https://www.formdev.com/flatlaf/) for the modern themes and a small built-in docking
  framework (`org.pepsoft.worldpainter.docking`) for the panels.
- The procedural terrain and edge blending engine lives in `org.pepsoft.worldpainter.procedural` in WPCore; the dialogs
  are in the same package in WPGUI.
- To merge the latest changes from the original project: `git fetch upstream && git merge upstream/master` (the
  `upstream` remote points to https://github.com/Captain-Chaos/WorldPainter).
