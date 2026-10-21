# Contributing to MotoWear OS 🏍️🤝

Thank you for your interest in contributing to **MotoWear OS**! This project is dedicated to providing an open-source, pixel-accurate replica of the Beeline Moto 2 motorcycle navigation computer on Wear OS smartwatches.

Before contributing, please review our [Architecture Documentation](docs/ARCHITECTURE.md) and [Developer & Contributor Guide](docs/DEVELOPMENT_GUIDE.md).

---

## 🛠️ How to Contribute

### 1. Reporting Bugs
- Search existing issues to confirm the bug has not already been reported.
- If not, open a new issue with a clear description of the problem, steps to reproduce, and device specifications (Wear OS version, watch model, Android version on phone).

### 2. Suggesting Enhancements & New Features
- Open an issue describing the feature proposal and its practical use case for motorcycle riders.
- If proposing UI modifications, ensure they strictly adhere to the [Screen Modes Catalog](docs/SCREEN_MODES.md) and Beeline design guidelines.

### 3. Submitting Pull Requests
1. Fork the repository and create your feature branch:
   ```bash
   git checkout -b feature/my-new-feature
   ```
2. Ensure you have **OpenJDK 17** configured:
   ```bash
   export JAVA_HOME="/path/to/openjdk-17"
   ```
3. Run the complete test suite across all modules before submitting:
   ```bash
   ./gradlew test
   ```
4. Verify code compiles cleanly:
   ```bash
   ./gradlew assembleDebug
   ```
5. Commit your changes using conventional commit messages (e.g. `feat(wear): ...`, `fix(phone): ...`, `docs: ...`).
6. Push to your branch and open a Pull Request against `main`.

---

## 🎨 Code Style Guidelines

- Adhere to official Kotlin and Jetpack Compose style guidelines.
- Jetpack Compose Composables should accept standard parameters (`modifier: Modifier = Modifier`).
- Use explicit types for public API surfaces and domain entities.
- Zero-allocation policy in Canvas draw loops: avoid instantiating objects (e.g. `Paint`, `Path`) inside DrawScope functions unless cached.
- Format code before committing using `./gradlew spotlessApply` or the Android Studio code formatter.

---

## 📜 Code of Conduct

All contributors and maintainers are expected to adhere to our [Code of Conduct](CODE_OF_CONDUCT.md).
